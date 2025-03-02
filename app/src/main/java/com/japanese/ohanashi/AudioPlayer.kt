package com.japanese.ohanashi

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player.Listener
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.japanese.ohanashi.ui.theme.OHanashiTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VM_AudioPlayerFactory(val context: Context) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return VM_AudioPlayer(context) as T
    }
}

//val LocalAudioPlayer = staticCompositionLocalOf<VM_AudioPlayer> {
//    error("No PodcastSearchViewModel provided")
//}
//@Composable
//fun ProvideAudioPlayer(content: @Composable () -> Unit) {
//    val audioPlayer: VM_AudioPlayer = viewModel(factory = VM_AudioPlayerFactory(LocalContext.current))
//    CompositionLocalProvider(
//        LocalAudioPlayer provides audioPlayer,
//        content = content
//    )
//}

class VM_AudioPlayer(context: Context) : ViewModel() {
    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply{
        addListener(object: Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
//                super.onPlaybackStateChanged(playbackState)
                if (playbackState == ExoPlayer.STATE_READY) {
//                    seekTo(startTime)
                    onProgressUpdate(progress.value)
                }
            }
        })
    }
    var onProgressUpdate: (newProgress: Float) -> Unit = {}

    val isPlaying:  MutableState<Boolean> = mutableStateOf(false)
    val showMillis: MutableState<Boolean> = mutableStateOf(false)
    val progress:   MutableState<Float>   = mutableStateOf(0f)
    // the time in seconds where playback should start
    private val startTime: MutableState<Float> = mutableStateOf(-1f)
    // the time in seconds when playback should end
    private val endTime: MutableState<Float> = mutableStateOf(-1f)

    fun setStartTime(startTime: Float) {
        this.startTime.value = startTime
        if (startTimeMillis > player.currentPosition) {
            player.seekTo(startTimeMillis)
        }
    }
    fun setEndTime(startTime: Float) {
        this.endTime.value = startTime
        if (endTimeMillis < player.currentPosition) {
            player.seekTo(endTimeMillis)
        }
    }

    override fun onCleared() {
        super.onCleared()
        player.stop()
        player.release()
    }

    val startTimeMillis: Long get() = calcStartTime()
    val endTimeMillis:   Long get() = calcEndTime()
    val durationMillis:  Long get() = calcDuration()

    fun formatCurrentTime(): String {
        return formatNormalizedPositionTime(progress.value)
    }

    fun formatNormalizedPositionTime(position: Float): String {
        val currentTime: Long = (durationMillis * position).toLong()
        val secondsTotal: Float = currentTime / 1000f
        val seconds: Int =  (secondsTotal % 60).toInt()
        val minutes: Int = ((secondsTotal / 60f) % 60).toInt()
        val hours:   Int = ((secondsTotal / 3600f) % 60).toInt()

        if (showMillis.value) {
            val millis: Int  = ((secondsTotal - secondsTotal.toInt()) * 1000f).toInt()
            return if (hours > 0) "%02d:%02d:%02d.%03d".format(hours,minutes,seconds,millis)
                   else                "%02d:%02d.%03d".format(minutes,seconds,millis)
        }

        return if (hours > 0) "%02d:%02d:%02d".format(hours,minutes,seconds) else "%02d:%02d".format(minutes,seconds)
    }

    fun calcDuration(): Long {
        return endTimeMillis - startTimeMillis
    }

    fun calcStartTime(): Long {
        if (startTime.value <= 0) return 0

        val startMillis = (startTime.value * 1000L).toLong()
//        if (startMillis > player.duration) return player.duration

        return startMillis
    }

    fun calcEndTime(): Long {
        if (endTime.value <= 0) return player.duration
//        if (endIndex.value < startIndex.value) return startTime

        val endMillis = (endTime.value * 1000L).toLong()
//        if (endMillis > player.duration) return player.duration

        return endMillis
    }

//    val mappedProgress: Float get() = calcMappedProgress()
    fun calcMappedProgress(): Float {
//        if (startIndex.value > endIndex.value) return 0f
        val start  = startTimeMillis
        val end    = endTimeMillis
        val result = ((player.currentPosition - start) / (end - start).toFloat())
        return result
    }

    fun play() {

        isPlaying.value = true
        if (player.currentPosition >= endTimeMillis) {
            player.seekTo(startTimeMillis)
        }
        else {
            player.play()
        }
        viewModelScope.launch{
            startProgressUpdate()
        }
    }

    fun pause() {
        player.pause()
        isPlaying.value = false
    }

    fun setProgress(newProgress: Float) {

        var seekTo: Long = (newProgress * player.duration).toLong()
        if (startTime.value > 0 || endTime.value > 0) {
            val start = if(startTime.value > 0) startTime.value else 0f
            val end =   if(endTime.value <= 0 || endTime.value > player.duration / 1000f) (player.duration / 1000f) else endTime.value

            seekTo = ((start + ((end - start) * newProgress)) * 1000f).toLong()
        }

        player.playWhenReady = isPlaying.value
        progress.value = newProgress
        player.seekTo(seekTo)
    }

    private suspend fun startProgressUpdate()
    {
        while(isPlaying.value) {
            progress.value = calcMappedProgress()
            onProgressUpdate(progress.value)
            delay(200L)
            // TODO: player.currentPosition is -superHighNumber for some time after first play :(
            if (player.currentPosition >= endTimeMillis) stop()
        }
    }

    private fun stop() {
        isPlaying.value = false
        player.playWhenReady = false
        player.pause()
        player.seekTo(startTimeMillis)
        progress.value = 0f
        onProgressUpdate(progress.value)
    }

    fun setStartAndEnd(startTimeSeconds: Float = -1f, endTimeSeconds: Float = -1f, resetProgress: Boolean = false) {
        setStartTime(startTimeSeconds)
        setEndTime(endTimeSeconds)
        if (resetProgress) {
            player.seekTo(startTimeMillis)
            if (player.currentPosition > 0) {
                progress.value = 0f
            }
        }
    }

    fun setAudio(context: Context, resourceId: Int, startTimeSeconds: Float = -1f, endTimeSeconds: Float = -1f) {
        setStartTime(startTimeSeconds)
        setEndTime(endTimeSeconds)
        val resource = "android.resource://${context.packageName}/${resourceId}"

        val dataSourceFactory = DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))
        val mediaItem = MediaItem.fromUri(resource)
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        player.setMediaSource(mediaSource)
//        player.addMediaSource(mediaSource)
        player.prepare()
        player.seekTo(startTimeMillis)
    }
}

@Composable
fun AudioPlayerControls(viewModel: VM_AudioPlayer, resourceId: Int, modifier: Modifier = Modifier, startTime: Float, endTime: Float, showMillis: Boolean = false) {
    val context = LocalContext.current
    viewModel.setAudio(context, resourceId, startTime, endTime)
    // seperated out to avoid setAudio call on every recomposition when the slider changes
    AudioPlayerControlsImpl(modifier, viewModel, showMillis)
}

@Composable
fun AudioPlayerControlsImpl(modifier: Modifier = Modifier, viewModel: VM_AudioPlayer, showMillis: Boolean = false) {
    val isPlaying = viewModel.isPlaying.value
    var sliderPosition by remember { mutableStateOf(viewModel.progress.value) }
    var sliderDragging by remember { mutableStateOf(false) }

    viewModel.onProgressUpdate = {
        if (!sliderDragging)
            sliderPosition = it
    }

    AudioPlayerStateless(
        modifier,
        sliderPosition,
        onSliderValueChange = {
            sliderDragging = true
            sliderPosition = it
        },
        onSliderValueChangeFinished = {
            sliderDragging = false
            viewModel.setProgress(sliderPosition)
        },
        onToggleMillisClicked = { viewModel.showMillis.value = !viewModel.showMillis.value },
        onTogglePlayButtonClick = {
//            viewModel.setResource(context, resourceId)
            if (isPlaying) viewModel.pause()
            else viewModel.play()
        },
        isPlaying = isPlaying,
        currentTimeText = viewModel.formatNormalizedPositionTime(sliderPosition)
    )
}

@Composable
fun AudioPlayerStateless(
    modifier: Modifier = Modifier,
    sliderValue: Float,
    onSliderValueChange: (Float) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    onToggleMillisClicked: () -> Unit,
    onTogglePlayButtonClick: () -> Unit,
    isPlaying: Boolean,
    currentTimeText: String = "00:00",
    timeTextColor: Color = Color.Unspecified
) {
    // https://github.com/google/ExoPlayer/issues/5416
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 15.dp, end = 15.dp)
            .background(Color.Transparent),
        verticalArrangement = Arrangement.Center
    ) {
        // https://piotrprus.medium.com/custom-slider-in-jetpack-compose-43ed08e2c338
        Column(modifier = Modifier.fillMaxWidth()) {
            val sliderPadding: Float = with(LocalDensity.current) { 10.dp.toPx() }
            val textSize: Float = with(LocalDensity.current) { 15.sp.toPx() }
            val textColor = (timeTextColor.takeOrElse {
                getTextColor()
            }).toArgb()
            Canvas(modifier = Modifier
                .fillMaxWidth()
                .height(20.dp))
            {
                val textPaint = android.graphics.Paint().apply {
                    color = textColor
                    textAlign = android.graphics.Paint.Align.CENTER
                    this.textSize = textSize
                }
                this.drawContext.canvas.nativeCanvas.drawText(
                    currentTimeText,
                    sliderPadding + (size.width-sliderPadding*2f) * sliderValue ,
                    size.height,
                    textPaint
                )
            }
            Slider(
                value = sliderValue,
                onValueChange = onSliderValueChange,
                onValueChangeFinished = onSliderValueChangeFinished,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(
                buttonSize = 35.dp,
                iconSize = 25.dp,
                iconResource = R.drawable.ic_baseline_more_horiz_24,
                onClick = onToggleMillisClicked,
                contentDescription = "ShowMillis"
            )
            ControlButton(
                buttonSize = 45.dp,
                iconSize = 40.dp,
                iconResource =
                    if (isPlaying) R.drawable.ic_baseline_pause_24
                    else           R.drawable.ic_baseline_play_arrow_24,
                onClick = onTogglePlayButtonClick,
                contentDescription = "Play/Pause"
            )
            Spacer(Modifier.size(35.dp))
        }
    }
}

// --------------------------------------------------------------------------------------
@Preview(showBackground = true)
@Composable
fun AudioPlayerPreview() {
    OHanashiTheme {
        AudioPlayerStateless(
            modifier = Modifier.padding(0.dp),
            sliderValue = 0.5f,
            onSliderValueChange = { },
            onSliderValueChangeFinished = { },
            onToggleMillisClicked = { },
            onTogglePlayButtonClick = { },
            isPlaying = false
        )
    }
}