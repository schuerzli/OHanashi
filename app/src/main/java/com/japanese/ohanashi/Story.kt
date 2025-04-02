package com.japanese.ohanashi

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.japanese.ohanashi.stories.defaultStories
import com.japanese.ohanashi.stories.shirayukihime
import com.japanese.ohanashi.ui.theme.OHanashiTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

var LocalStory = staticCompositionLocalOf<VM_Story> {
    error("No VM_Story provided")
}


@Serializable
data class StoryList(var stories: List<StoryCard>)
@Serializable
data class Story(var title: String, var cards: List<StoryCard>, var audioId: Int,)
@Serializable
data class StoryCard(
    var text:  String,
    var notes: String = "",
    var audioStartTime: Float = -1f,
    var audioEndTime:   Float = -1f,
    var audioStartTimeString: String = "",
    var audioEndTimeString:   String = "",
)
{
    fun parseTimeWithDefaults(timeString: String): LocalTime {
        var formattedTime = timeString

        // Add default hours ("00") if hours are missing
        val numColon = formattedTime.count { it == ':' }
        if (numColon == 0) {
            formattedTime = "00:00:$formattedTime"
        }
        else if (numColon == 1) {
            formattedTime = "00:$formattedTime"
        }

        // Add default hundredths of seconds ("00") if they are missing
        if (!formattedTime.contains(".")) {
            formattedTime = "$formattedTime.00"
        }

        // Parse with the expected format
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SS")
        return LocalTime.parse(formattedTime, formatter)
    }

    fun parseTimeString(time: String) : Float {
        val date   = parseTimeWithDefaults(time/* e.g. "00:01:08.83" */)
        val result = date.toNanoOfDay().toFloat() / 1_000_000_000f
        return result
    }

    fun getStartTime(): Float {
        if (!audioStartTimeString.isEmpty()) {
            return parseTimeString(audioStartTimeString)
        }
        return audioStartTime;
    }

    fun getEndTime(): Float {
        if (!audioEndTimeString.isEmpty()) {
            return parseTimeString(audioEndTimeString)
        }
        return audioEndTime;
    }
}

@Composable
fun ProvideStoryVM (content: @Composable () -> Unit) {
    val storyVM: VM_Story = viewModel(factory = VM_StoryFacotry(LocalContext.current))
    CompositionLocalProvider(LocalStory provides storyVM, content = content)
}

class VM_StoryFacotry(val context: Context) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return VM_Story(context) as T
    }
}

class VM_Story(context: Context) : ViewModel() {
    var stories:      MutableState<List<Story>> = mutableStateOf(defaultStories)
    var storyIndex:   MutableState<Int> = mutableStateOf(0)
    var cardIndex:    MutableState<Int> = mutableStateOf(0)
    var showFurigana: MutableState<Boolean> = mutableStateOf(false)
    var kanaOnly:     MutableState<Boolean> = mutableStateOf(false)
    var showNotes:    MutableState<Boolean> = mutableStateOf(false)

    init {
        viewModelScope.launch {
            val preferences = context.dataStore.data.first()
            val loadedStoryIndex = preferences[STORY_INDEX]
            if (loadedStoryIndex != null) {
                storyIndex.value = loadedStoryIndex
            }
        }
    }

    fun parseStartTime(card: StoryCard ): Float {
        return card.getStartTime()
    }
    fun parseEndTime(card: StoryCard ): Float {
        return card.getEndTime()
    }
    fun getStartTime(): Float {
        if (cardIndex.value == 0 || defaultStories[storyIndex.value].cards[cardIndex.value].audioStartTime > 0)
            return parseStartTime(defaultStories[storyIndex.value].cards[cardIndex.value]) //.audioStartTime

        return parseEndTime(defaultStories[storyIndex.value].cards[cardIndex.value-1]) //.audioEndTime
    }
    fun nextCard() {
        if (cardIndex.value < defaultStories[storyIndex.value].cards.lastIndex) {
            ++cardIndex.value
        }
    }
    fun prevCard() {
        if (cardIndex.value > 0) {
            --cardIndex.value
        }
    }
}

@ExperimentalMaterialApi
@Composable
fun StoryViewControl(
    navController:  NavController,
    audioPlayer:    VM_AudioPlayer,
    fontSize:       TextUnit = TextUnit.Unspecified,
    showFurigana:   Boolean,
    toggleFurigana: () -> Unit,
    kanaOnly:       Boolean,
    toggleKanaOnly: () -> Unit,
) {
    val cardIndex    = LocalStory.current.cardIndex
    val showNotes    = LocalStory.current.showNotes
    val story: Story = LocalStory.current.stories.value[LocalStory.current.storyIndex.value]
    val storyCard    = story.cards[cardIndex.value]

    if (cardIndex.value < 0)                     error("Story cardIndex was < 0 (${cardIndex.value})!")
    if (cardIndex.value > story.cards.lastIndex) error("Story cardIndex > last entry index (${cardIndex.value} > ${story.cards.lastIndex})!")

    var storyVM = LocalStory.current
    Box(modifier = Modifier.fillMaxSize().padding(10.dp))
    {
        StoryViewStateless(
            audioPlayer    = audioPlayer,
            cardText       = storyCard.text,
            cardNotes      = storyCard.notes,
            fontSize       = fontSize,
            showNotes      = showNotes.value,
            showFurigana   = showFurigana,
            kanaOnly       = kanaOnly,
            cardIndex      = cardIndex.value,
            cardCount      = story.cards.size,
            audioId        = story.audioId,
            audioStartTime = storyVM.getStartTime(),
            audioEndTime   = storyCard.audioEndTime,
            prevCard       = {
                storyVM.prevCard()
                audioPlayer.pause()
                audioPlayer.setStartAndEnd(
                    startTimeSeconds = storyVM.getStartTime(),
                    endTimeSeconds   = story.cards[cardIndex.value].audioEndTime,
                    resetProgress = true,
                )
             },
            nextCard       = {
                storyVM.nextCard()
                audioPlayer.pause()
                audioPlayer.setStartAndEnd(
                    startTimeSeconds = storyVM.getStartTime(),
                    endTimeSeconds   = story.cards[cardIndex.value].audioEndTime,
                    resetProgress = true,
                )
             },
            toggleNotes    = { showNotes.value = !showNotes.value },
            toggleFurigana = toggleFurigana,
            toggleKanaOnly = toggleKanaOnly,
        )
    }
}

@ExperimentalMaterialApi
@Composable
fun StoryViewStateless(
    audioPlayer:  VM_AudioPlayer,
    cardText:     String,
    cardNotes:    String,
    fontSize:     TextUnit = TextUnit.Unspecified,
    showFurigana: Boolean = false,
    kanaOnly:     Boolean    = false,
    showNotes:    Boolean = false,
    cardIndex: Int,
    cardCount: Int,
    audioId:   Int,
    audioStartTime: Float = -1f,
    audioEndTime:   Float = -1f,
    prevCard:       () -> Unit = {},
    nextCard:       () -> Unit = {},
    toggleNotes:    () -> Unit = {},
    toggleFurigana: () -> Unit = {},
    toggleKanaOnly:    () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val scrollState = rememberScrollState()
        Box(modifier = Modifier
            .fillMaxSize()
            .weight(1f)
        ) {
            val swipeableState = rememberSwipeableState(
                initialValue = 0,
                confirmStateChange = {
                    if (it <= -1) nextCard()
                    if (it >=  1) prevCard()
                    // we never want to stay in the target state, it's just a trigger
                    false
                }
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(swipeableState.offset.value.dp * 0.125f)
                    .clip(SemicircleShape(Direction.Right))
                    .background(Color(0.5f, 0.5f, 0.5f, 0.2f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
                    .width(-swipeableState.offset.value.dp * 0.125f)
                    .clip(SemicircleShape(Direction.Left))
                    .background(Color(0.5f, 0.5f, 0.5f, 0.2f))
            )
            SelectionContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .swipeable(
                        state = swipeableState,
                        anchors = mapOf(0f to 0, 200f to 1, -200f to -1),
                        orientation = Orientation.Horizontal,
                    )
            ) {
                Column(modifier = Modifier
                    .verticalScroll(scrollState)
                    .fillMaxWidth()
                    .padding(start = 15.dp, end = 15.dp)
                ) {
//                    FuriganaText(
//                        text = "---== ${story.title} ==---",
//                        fontSize = 25.sp,
//                        modifier = Modifier.align(Alignment.CenterHorizontally),
//                        showFurigana = showFurigana
//                    )
                    FuriganaText(
                        cardText,
                        fontSize = fontSize,
                        showFurigana = showFurigana,
                        hideKanji = kanaOnly,
                    )
                    if (showNotes && cardText.isNotBlank()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp))
                        {
                        Box(modifier = Modifier.height(1.dp).background(getTextColor()).fillMaxWidth())
                        }
                        FuriganaText(
//                            modifier     = Modifier.padding(top = 10.dp),
                            text         = cardNotes,
                            fontSize     = fontSize,
                            showFurigana = showFurigana,
                            hideKanji    = kanaOnly,
                        )
                    }
                }
            }
        }

        AudioPlayerControls(audioPlayer, audioId, modifier = Modifier.height(140.dp), audioStartTime, audioEndTime)

        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
        {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp)
                    .height(15.dp)
//                    .align(Alignment.Center)
            ) {
                Text("${cardIndex+1}/${cardCount}", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                val buttonSize : Dp = 40.dp;

                ControlButton(
                    iconResource = R.drawable.ic_baseline_navigate_before_24,
                    buttonSize   = buttonSize,
                    contentDescription = "PreviousCard",
                ) { prevCard() }
                Row(horizontalArrangement = Arrangement.Center) {
                    ControlButton(
                        iconResource = R.drawable.ic_baseline_notes_24,
                        buttonSize   = buttonSize,
                        iconSize     = 22.dp,
                        invert       = showNotes,
                        onClick      = toggleNotes,
                        contentDescription = "ToggleNotes"
                    )
                    ControlButton(
                        iconResource = R.drawable.ic_baseline_translate_24,
                        buttonSize   = buttonSize,
                        invert       = showFurigana,
                        onClick      = toggleFurigana,
                        contentDescription = "ToggleFurigana"
                    )
                    ControlButton(
//                        iconResource = R.drawable.ic_baseline_translate_24,
                        buttonSize = buttonSize,
                        invert     = kanaOnly,
                        onClick    = toggleKanaOnly,
                        contentDescription = "HideKanji",
                    )
                }
                ControlButton(
                    iconResource = R.drawable.ic_baseline_navigate_next_24,
                    buttonSize = buttonSize,
                    contentDescription = "NextCard",
                ) { nextCard() }
            }
//            Row(
//                horizontalArrangement = Arrangement.SpaceAround,
//                verticalAlignment = Alignment.CenterVertically,
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(8.dp)
//            ) {
//                Text("${cardIndex+1}/${cardCount}", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterVertically))
//                Spacer(Modifier.size(10.dp))
//            }
        }
    }
}

// --------------------------------------------------------------------------------------
@ExperimentalMaterialApi
@Preview(showBackground = true)
@Composable
fun StoryViewPreview() {
    OHanashiTheme {
        StoryViewStateless(
            audioPlayer = viewModel<VM_AudioPlayer>(factory = VM_AudioPlayerFactory(LocalContext.current.applicationContext as Application)),
            cardText  = shirayukihime.cards[1].text,
            cardNotes = shirayukihime.cards[1].notes,
            cardIndex = 2,
            cardCount = shirayukihime.cards.size,
            audioId = -1,
        )
    }
}