package com.japanese.ohanashi

import android.app.Activity.BIND_AUTO_CREATE
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.japanese.ohanashi.stories.defaultStories
import com.japanese.ohanashi.ui.theme.OHanashiTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VM_AudioPlayerFactory(val application:Application) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return VM_AudioPlayer(application) as T
    }
}

val LocalAudioPlayer = staticCompositionLocalOf<VM_AudioPlayer> {
    error("No PodcastSearchViewModel provided")
}
@Composable
fun ProvideAudioPlayer(application: Application, content: @Composable () -> Unit) {
    val audioPlayer: VM_AudioPlayer = viewModel(factory = VM_AudioPlayerFactory(application))
    CompositionLocalProvider(
        LocalAudioPlayer provides audioPlayer,
        content = content
    )
}

class VM_AudioPlayer(application: Application) : AndroidViewModel(application) {
    private var progressUpdateJob : Job?   = null

    val showMillis: MutableState<Boolean> = mutableStateOf(false)
    // the time in seconds where playback should start
    private val startTime = mutableFloatStateOf(-1f)
    // the time in seconds when playback should end
    private val endTime   = mutableFloatStateOf(-1f)

    // Set from Service
    // should all of these be LiveData?
    val isPlaying       = mutableStateOf(false)
    val progress        = mutableFloatStateOf(0f)
    val currentPosition = mutableFloatStateOf(0f)
    val duration        = mutableIntStateOf(0)
    private var isServiceRunning = MutableLiveData(false)
    private var mBound           = MutableLiveData(false)
//    val IsServiceRunning : LiveData<Boolean> = isServiceRunning
//    val IsBound: LiveData<Boolean> = mBound

    private var audioServiceBinder : AudioPlayerService.ServiceBinder? = null
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(p0: ComponentName?, binder: IBinder?) {
            audioServiceBinder = (binder as AudioPlayerService.ServiceBinder)
            var audioService = binder.getService()

            binder.setAudioList(defaultStories)

            val context = getApplication<Application>()
            viewModelScope.launch {
                binder.isPlaying().collectLatest {
                    isPlaying.value = it
                }
            }
            viewModelScope.launch {
                binder.currentPosition().collectLatest {
                    currentPosition.floatValue = it
                }
            }
            viewModelScope.launch {
                binder.currentTrackDuration().collectLatest {
                    duration.intValue = it
                }
            }

//            viewModelScope.launch {
//                binder.currentTrack().collectLatest {
//                    currentTrack.value = it
//                }
//            }

            mBound.value = true
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            mBound.value = false
        }
    }

    private fun startService()
    {
        if (mBound.value == false)
        {
            val context = getApplication<Application>()
            val startIntent = Intent(context, AudioPlayerService::class.java)
            context.startService(startIntent)
            context.bindService(startIntent, connection, BIND_AUTO_CREATE)
//            Intent(context, AudioPlayerService::class.java).also {
//                it.action = AudioPlayerService.Actions.Start.toString()
//                context.startService(it)
//                context.bindService(it, connection, BIND_AUTO_CREATE)
            isServiceRunning.postValue(true)
//            }
        }
    }
    private fun stopService() {
        val context = getApplication<Application>()
        if (mBound.value == true)
        {
            context.unbindService(connection)
        }
//        if (isServiceRunning.value == true)
//        {
            val stopIntent  = Intent(context, AudioPlayerService::class.java)
            context.stopService(stopIntent)
//        Intent(context, AudioPlayerService::class.java).also {
//            it.action = AudioPlayerService.Actions.Stop.toString()
//            context.stopService(it)
//        }
            isServiceRunning.postValue(false)
//        }
    }

    private fun setStartTime(startTime: Float) {
        this.startTime.floatValue = startTime
//        if (startTimeMillis > player.currentPosition) {
//            player.seekTo(startTimeMillis)
//        }
    }
    private fun setEndTime(startTime: Float) {
        this.endTime.floatValue = startTime
//        if (endTimeMillis < player.currentPosition) {
//            player.seekTo(endTimeMillis)
//        }
    }

    fun formatCurrentTime(): String {
        return formatNormalizedPositionTime(progress.floatValue)
    }

    fun formatNormalizedPositionTime(position: Float): String {
        val currentTime: Long = (calcDurationMillis() * position).toLong()
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

    private fun calcDurationMillis(): Long {
        return calcEndTimeMillis() - calcStartTimeMillis()
    }

    private fun calcStartTimeMillis(): Long {
        if (startTime.floatValue <= 0) { return 0 }
        val startMillis = (startTime.floatValue * 1000L).toLong()
        return startMillis
    }

    private fun calcEndTimeMillis(): Long {
        if (endTime.floatValue <= 0) { return duration.intValue.toLong() }
        val endMillis = (endTime.floatValue * 1000L).toLong()
        return endMillis
    }

//    val mappedProgress: Float get() = calcMappedProgress()
    private fun calcNormalizedProgress(): Float {
//        if (startIndex.value > endIndex.value) return 0f
        val start  = calcStartTimeMillis()
        val end    = calcEndTimeMillis()
        val result = ((currentPosition.floatValue - start) / (end - start).toFloat())
        return result
    }

    fun togglePlay() {
        if (mBound.value == true) {
            audioServiceBinder?.pauseResume()
            if (isPlaying.value) {
                startProgressUpdate()
            }
            else {
                stopProgressUpdate()
            }
        }
        else {
            startService()
            startProgressUpdate()
        }
    }

//    fun play() {
//
//        isPlaying.value = true
//        if (player.currentPosition >= endTimeMillis) {
//            player.seekTo(startTimeMillis)
//        }
//        else {
//            player.play()
//        }
//        viewModelScope.launch{
//            startProgressUpdate()
//        }
//    }

    fun pause() {
        audioServiceBinder?.pause()
    }

    /**
     * seek to the normalized progress between currently set startTime and endTime
     */
    fun setProgress(newProgress: Float) {

        var seekTo: Long = (newProgress * duration.intValue).toLong()
        if (startTime.floatValue > 0 || endTime.floatValue > 0) {
            val start = if(startTime.floatValue >  0) startTime.floatValue else 0f
            val end =   if(endTime.floatValue   <= 0 || endTime.floatValue > duration.intValue / 1000f) (duration.intValue / 1000f) else endTime.floatValue

            seekTo = ((start + ((end - start) * newProgress)) * 1000f).toLong()
        }
        audioServiceBinder?.seekTo(seekTo.toInt())
    }

    private fun startProgressUpdate() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {

//            while(isPlaying.value) {
            while(true) {
                progress.floatValue = calcNormalizedProgress()
                delay(100L)
    //            // TODO: player.currentPosition is -superHighNumber for some time after first play :(
//                if (currentPosition.floatValue >= calcEndTimeMillis()) {
//                    setProgress(0f)
//                    audioServiceBinder?.pause()
//                }
            }

        }
    }
    private fun stopProgressUpdate() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    fun setStartAndEnd(startTimeSeconds: Float = -1f, endTimeSeconds: Float = -1f, resetProgress: Boolean = false) {
        setStartTime(startTimeSeconds)
        setEndTime(endTimeSeconds)
        if (resetProgress) {
            audioServiceBinder?.seekTo(calcStartTimeMillis().toInt())
//            player.seekTo(startTimeMillis)
//            if (player.currentPosition > 0) {
//                progress.value = 0f
//            }
        }
    }

    fun setAudio(context: Context, resourceId: Int, startTimeSeconds: Float = -1f, endTimeSeconds: Float = -1f) {
        setStartTime(startTimeSeconds)
        setEndTime(endTimeSeconds)
//        val resource = "android.resource://${context.packageName}/${resourceId}"

//        val dataSourceFactory = DefaultDataSourceFactory(context, Util.getUserAgent(context, context.packageName))
//        val mediaItem = MediaItem.fromUri(resource)
//        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
//        player.setMediaSource(mediaSource)
////        player.addMediaSource(mediaSource)
//        player.prepare()
//        player.seekTo(startTimeMillis)
    }

    // called when Activity is destroyed when app shuts down
    override fun onCleared() {
        super.onCleared()
        stopProgressUpdate()
        stopService()
    }

}

@Composable
fun AudioPlayerControls(viewModel: VM_AudioPlayer, resourceId: Int, modifier: Modifier = Modifier, startTime: Float, endTime: Float, showMillis: Boolean = false) {
    val context = LocalContext.current
    viewModel.setAudio(context, resourceId, startTime, endTime)
    Row{
        Text(startTime.toString())
        Text(endTime.toString())
    }
    // seperated out to avoid setAudio call on every recomposition when the slider changes
    AudioPlayerControlsImpl(modifier, viewModel, showMillis)
}

@Composable
fun AudioPlayerControlsImpl(modifier: Modifier = Modifier, viewModel: VM_AudioPlayer, showMillis: Boolean = false) {
    val isPlaying = viewModel.isPlaying.value
//    var sliderPosition by remember { mutableStateOf(viewModel.progress.floatValue) }
    var sliderPosition by remember { viewModel.progress }
    var sliderDragging by remember { mutableStateOf(false) }

//    viewModel.onProgressUpdate = {
//        if (!sliderDragging)
//            sliderPosition = it
//    }

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
            viewModel.togglePlay()
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