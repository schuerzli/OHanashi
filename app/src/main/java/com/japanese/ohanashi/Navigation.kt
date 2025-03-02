package com.japanese.ohanashi

import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

var LocalNavHostController = staticCompositionLocalOf<NavHostController> {
    error ("No NavController provided!")
}

@Composable
fun ProvideNavController(content: @Composable () -> Unit) {
    val navController = rememberNavController()
    CompositionLocalProvider(LocalNavHostController provides navController, content = content)
}

sealed class Screen(val route: String) {
    object StoryScreen     : Screen("screen_story")
    object EditStoryScreen : Screen("screen_details")
}