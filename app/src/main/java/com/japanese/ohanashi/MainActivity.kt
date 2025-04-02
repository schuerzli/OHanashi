package com.japanese.ohanashi

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.google.gson.Gson
import com.japanese.ohanashi.stories.defaultStories
import com.japanese.ohanashi.ui.theme.OHanashiTheme
import com.japanese.ohanashi.ui.theme.SoftWhite
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.util.Log
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
TODO:
 - Use a custom notification layout with RemoteViews
 - Page slider for quickly scrolling through pages
 - Add scrollbar to Cards
 - Playback options
   - Randomize cards
   - Loop Story
   - Continue with next story
 - 0.8x speed
 - Listening comprehension mode: play audio first, reveal transcript only after pressing a button

 - (Load Story from json?)
 - Spaced repetition for cards function
 - Settings drop down with:
   - MainText Size
   - Furigana Size
   - Show notes
   - Show translation

 - Edit Cards
   - Edit Card Text
   - Edit Card audio time stamps
   - Save/Load Cards
   - Access audio files from disk
*/

// https://medium.com/mindorks/implementing-exoplayer-for-beginners-in-kotlin-c534706bce4b
// http://kakasi.namazu.org/
// https://pykakasi.readthedocs.io/en/latest/api.html
// https://github.com/hexenq/kuroshiro
// https://github.com/google/ringdroid
// https://stackoverflow.com/questions/38744579/show-waveform-of-audio
// https://developer.android.com/reference/android/media/audiofx/Visualizer

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
suspend fun <T> DataStore<Preferences>.save(key: Preferences.Key<T>, value: T) {
    this.edit { settings -> settings[key] = value }
}
suspend fun <T> DataStore<Preferences>.read(key: Preferences.Key<T>): T {
    val preferences = this.data.first()
    val result = preferences[key]
    return result!!
}

@Composable
fun getTextColor(): Color {
    return LocalTextStyle.current.color.takeOrElse {
        LocalContentColor.current.copy(alpha = LocalContentAlpha.current)
    }
}

val STORY_INDEX: Preferences.Key<Int> = intPreferencesKey("CURRENT_STORY_INDEX")
var actionBarHeightPx: Int = 0
var actionBarHeightDp: Dp  = 35.dp

class MainActivity : ComponentActivity() {

    suspend fun <T> save(key: Preferences.Key<T>, value: T) {
        dataStore.edit {
                settings ->
            settings[key] = value
        }
    }

    suspend fun <T> read(key: Preferences.Key<T>): T {
        val preferences = dataStore.data.first()
        val result = preferences[key]
        return result!!
    }

    @ExperimentalMaterialApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        dataStore = createDataStore(name = "settings")

//        WindowCompat.setDecorFitsSystemWindows(window, false)
        Log.e("JSON_TEST", Gson().toJson(defaultStories[0]))
//        val tv = TypedValue()
//        if (this.theme.resolveAttribute(R.attr.actionBarSize, tv, true)) {
//            actionBarHeightDp = TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_DIP, tv.data.toFloat(), resources.displayMetrics).dp
////            actionBarHeightPx = TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
////            actionBarHeightDp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, actionBarHeightPx.toFloat(), resources.displayMetrics).dp
//        }
        setContent {
            ProvideStoryVM {
                val storyVM = LocalStory.current
                OHanashi(
                    saveStoryIndex = { lifecycleScope.launch { save(STORY_INDEX, storyVM.storyIndex.value) } },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Check if the activity is finishing (i.e., app is closing)
        if (isFinishing) {
            // Stop the service here
            val intent = Intent(this, AudioPlayerService::class.java)
            stopService(intent)
        }
    }
}

@ExperimentalMaterialApi
@Composable
fun OHanashi(saveStoryIndex: () -> Unit) {
    var darkTheme by remember { mutableStateOf(true) }

    OHanashiTheme(darkTheme = darkTheme) {
            ProvideNavController {
                val navController = LocalNavHostController.current
                val audioPlayer : VM_AudioPlayer = viewModel(factory = VM_AudioPlayerFactory(LocalContext.current.applicationContext as Application))

                Menus(navController, { darkTheme = !darkTheme}) {
                    NavHost(navController = navController, startDestination = Screen.StoryScreen.route) {
                        composable(route = Screen.StoryScreen.route) {
                            StoryScreen( navController = navController, audioPlayer = audioPlayer)
                            saveStoryIndex()
                        }
                        composable(route = Screen.EditStoryScreen.route
                            /**, arguments = listOf( navArgument("argumentName") { type = NavType.StringType, defaultValue = "Default Value", nullable = true } ) */
                        ) {
                            DetailsScreen(navController = navController)
                        }
                    }
                }
            }
//        }
    }
}

@Composable
fun Menus(navController: NavController, toggleDarkTheme: () -> Unit = {}, content: @Composable () -> Unit) {
    var showStorySelection by remember { mutableStateOf(false) }
    val storySelectionListWidth = 200.dp

    Column(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colors.background)) {
        // top menu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(actionBarHeightDp + 50.dp)
                .background(MaterialTheme.colors.primary)
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                icon = R.drawable.ic_baseline_menu_24,
                buttonSize = 40.dp,
                iconSize = 28.dp,
                contentDescription = "StorySelection",
//                modifier = Modifier.align(Alignment.CenterVertically)// .padding(start = if (showStorySelection) storySelectionListWidth - 40.dp else 0.dp)
            ) {
                showStorySelection = !showStorySelection
            }
            IconButton(
                icon = R.drawable.ic_baseline_brightness_medium_24,
                buttonSize = 40.dp,
                iconSize = 28.dp,
                contentDescription = "ToggleDarkMode",
//                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                toggleDarkTheme()
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            // CONTENT #########
            content()
            // #################

            Box(
                modifier =
                if (showStorySelection) Modifier.fillMaxSize()
                else Modifier.size(0.dp)
            ) {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .clickable { showStorySelection = false })
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(if (showStorySelection) storySelectionListWidth else 0.dp)
                        .clickable(enabled = false) {}
                        .background(MaterialTheme.colors.primary)
                        .padding(10.dp)
                ) {
                    val scrollState = rememberLazyListState()
                    val storyList = LocalStory.current.stories.value
                    val storyIndex = LocalStory.current.storyIndex
                    val cardIndex = LocalStory.current.cardIndex
                    LazyColumn(state = scrollState) {
                        items(storyList.size) {
                            val story = storyList[it]
                            Row(modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    storyIndex.value = it
                                    cardIndex.value = 0
                                    // refresh story UI
                                    navController.navigate(Screen.StoryScreen.route)
                                }
                            ) {
                                FuriganaText(
                                    story.title,
                                    showFurigana = LocalStory.current.showFurigana.value,
                                    hideKanji    = LocalStory.current.kanaOnly.value,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontColor = SoftWhite
                                )
                            }
                        }
                    }
                }
                Row(modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)) {
                    IconButton(
                        icon = R.drawable.ic_baseline_add_24,
                        contentDescription = "AddStory",
                        buttonSize = 50.dp,
                        iconSize = 40.dp,
                        background = MaterialTheme.colors.secondary,
                        iconTint = MaterialTheme.colors.primary,
                    ) {
                        // TODO: add story
                    }
                }
            }
        }
    }
}

@Composable
fun IconButton(
    buttonSize: Dp = 24.dp,
    iconSize: Dp = 24.dp,
    icon: Int,
    background: Color = Color(1f, 1f, 1f, 0f),
    iconTint: Color = MaterialTheme.colors.secondary,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(modifier = modifier
        .clip(RoundedCornerShape(buttonSize))
        .background(background)
        .clickable { onClick() }) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier
                .size(iconSize)
                .align(Alignment.Center)
        )
    }
}

@ExperimentalMaterialApi
@Composable
fun StoryScreen(
    navController: NavController,
    audioPlayer: VM_AudioPlayer,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.background),
        verticalArrangement = Arrangement.Center,
    ) {
        val showFurigana = LocalStory.current.showFurigana
        val kanaOnly     = LocalStory.current.kanaOnly
        val isPlaying    = audioPlayer.isPlaying

        if (LocalStory.current.stories.value.size > 0) {
            if (LocalStory.current.storyIndex.value > LocalStory.current.stories.value.lastIndex) {
                LocalStory.current.storyIndex.value = LocalStory.current.stories.value.lastIndex
            }

            StoryViewControl(
                navController  = navController,
                audioPlayer    = audioPlayer,
                fontSize       = 20.sp,
                showFurigana   = showFurigana.value,
                kanaOnly       = kanaOnly.value,
                toggleFurigana = { showFurigana.value = !showFurigana.value },
                toggleKanaOnly = { kanaOnly.value = !kanaOnly.value }
            )
        }
    }
}

@Composable
fun DetailsScreen(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("---== Details Screen ==---", modifier = Modifier.align(Alignment.CenterHorizontally))
        ControlButton(
            iconResource = R.drawable.ic_baseline_play_arrow_24,
            contentDescription = "GotoStoryScreen",
            onClick = { navController.navigate(Screen.StoryScreen.route) },
        )
    }
}

@Composable
fun ControlButton(
    buttonSize: Dp = 60.dp,
    iconSize: Dp = 30.dp,
    iconResource: Int = -1,
    invert: Boolean = false,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val background = if(invert) MaterialTheme.colors.secondary else MaterialTheme.colors.primary
    val iconTint   = if(invert) MaterialTheme.colors.primary    else MaterialTheme.colors.secondary
    val padding = 5.dp
    Box(
        modifier = modifier
            .padding(padding)
            .size(buttonSize)
            .clip(RoundedCornerShape(buttonSize * 0.5f))
            .clickable { onClick() }
            .background(background)
            .padding(),
        contentAlignment = Alignment.Center,
    ) {
        if (iconResource != -1)
        {
            Icon(
                painter  = painterResource(iconResource),
                contentDescription = contentDescription,
                tint     = iconTint,
                modifier = Modifier
                    .size(iconSize)
                    .align(Alignment.Center)
            )
        }
    }
}

// --------------------------------------------------------------------------------------
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    OHanashiTheme {
        Column(modifier = Modifier.fillMaxSize())
        {
            Text("話[はなし] 話[ばなし]")
//        FuriganaText("話[はなし] 話[ばなし]")
//        val text = " 心[こころ]むかしむかし、とっても 美[うつく]しいけれど、 心[こころ]のみにくいおきさきがいました。 心[こころ]"
            val text = " 心[こころ]むかしむかし、とっても 美[うつく]しいけれど、 心[こころ]のみにくいおきさきがいました。 心[こころ]"
            FuriganaText(text, fontSize = 30.sp, modifier = Modifier.padding(20.dp))
            Text("------------------------------------------------------")
            Text(
                text,
                modifier = Modifier.verticalScroll(rememberScrollState()),
                fontSize = 30.sp
            )
        }
    }
}