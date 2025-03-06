package com.japanese.ohanashi

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.japanese.ohanashi.stories.defaultStories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val PREV = "prev"
const val NEXT = "next"
const val PLAY_PAUSE = "play_pause"

class AudioPlayerService : Service() {
    enum class Actions {
        Previous,
        Next,
        PauseResume,
        Start,
        Stop
    }

    inner class ServiceBinder : Binder() {

        fun getService() : AudioPlayerService = this@AudioPlayerService

        fun setAudioList(list:List<Story>) {
            this@AudioPlayerService.trackList = list.toMutableList()
        }

        fun pauseResume() {
            this@AudioPlayerService.pauseResume()
        }

        fun pause() {
            this@AudioPlayerService.pause()
        }

        fun currentTrackDuration() = this@AudioPlayerService.currentTrackDuration
        fun currentPosition()      = this@AudioPlayerService.currentPosition
        fun isPlaying()            = this@AudioPlayerService.isPlaying
        fun currentTrack()         = this@AudioPlayerService.currentTrack
    }

    val binder               = ServiceBinder()
    private var mediaPlayer  = MediaPlayer()
    private val currentTrack = MutableStateFlow<Story>(defaultStories[0])
    private var trackList    = defaultStories.toMutableList()
    private val scope        = CoroutineScope(Dispatchers.Main)
    private var job : Job?   = null

    val isPlaying            = MutableStateFlow(false)
    val currentTrackDuration = MutableStateFlow(0)
    val currentPosition      = MutableStateFlow(0f)

    override fun onBind(p0: Intent?): IBinder? {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        intent?.let {
            when(intent.action) {

                Actions.Previous.toString()    -> { prev() }
                Actions.Next.toString()        -> { next() }
                Actions.PauseResume.toString() -> { pauseResume() }
                else -> {
                    if (trackList.size > 0) {
                        currentTrack.update { trackList[0] }
                    }
                    play(currentTrack.value)
                }

            }
        }

        return START_STICKY
    }

    fun pause() {
        mediaPlayer.pause()
        sendNotification(currentTrack.value)
    }
    fun pauseResume() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        }
        else {
            mediaPlayer.start()
        }
        sendNotification(currentTrack.value)
    }

    fun prev() {
        job?.cancel()

        mediaPlayer.reset()
        mediaPlayer = MediaPlayer()

        val index = trackList.indexOf(currentTrack.value)
        val prevIndex = if (index>0) index - 1 else trackList.size - 1
        if (prevIndex >= 0)
        {
            val prevItem = trackList[prevIndex]

            currentTrack.update { prevItem }

            mediaPlayer.setDataSource(this, getRawUri(currentTrack.value.audioId))
            mediaPlayer.prepareAsync()
            mediaPlayer.setOnPreparedListener {
                mediaPlayer.start()
                sendNotification(currentTrack.value)
                updateDuration()
            }
        }
    }

    fun next() {
        job?.cancel()

        mediaPlayer.reset()
        mediaPlayer = MediaPlayer()

        val index = trackList.indexOf(currentTrack.value)
        val nextIndex = (index + 1).mod(trackList.size)
        val nextItem = trackList.get(nextIndex);

        currentTrack.update { nextItem }

        mediaPlayer.setDataSource(this, getRawUri(nextItem.audioId))
        mediaPlayer.prepareAsync()
        mediaPlayer.setOnPreparedListener {
            mediaPlayer.start()
            sendNotification(currentTrack.value)
            updateDuration()
        }
    }

    fun updateDuration() {

        job = scope.launch {

            if (!mediaPlayer.isPlaying) return@launch

            currentTrackDuration.update { mediaPlayer.duration }

            while(true) {
                currentPosition.update { mediaPlayer.currentPosition.toFloat() }
                delay(100)
            }

        }
    }

    private fun play(story: Story) {
        mediaPlayer.reset()
        mediaPlayer = MediaPlayer()
        mediaPlayer.setDataSource(this, getRawUri(story.audioId))
        mediaPlayer.prepareAsync()
        mediaPlayer.setOnPreparedListener {
            mediaPlayer.start()
            sendNotification(story)
            updateDuration()
        }
    }

    private fun getRawUri(id: Int) = Uri.parse("android.resource://${packageName}/${id}")

    private fun sendNotification(story: Story) {

        val session = MediaSessionCompat(this, "audio")

        isPlaying.update { mediaPlayer.isPlaying }

        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
            .setMediaSession(session.sessionToken)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSilent(true)
            .setStyle(style)
            .setContentTitle(story.title)
//            .setContentText("Current Sentence")
            .addAction(R.drawable.ic_baseline_navigate_before_24, "prev", createPrevPendingIntent())
            .addAction(
                if (mediaPlayer.isPlaying)
                    R.drawable.ic_baseline_pause_24
                else
                    R.drawable.ic_baseline_play_arrow_24,
                "play_pause", createPlayPausePendingIntent())
            .addAction(R.drawable.ic_baseline_navigate_next_24, "next", createNextPendingIntent())
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(100, 25, false)
            .setOngoing(true) // set to show up on lock screen
//            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.big_image))
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                startForeground(1, notification)
            }
        }
        else {
            startForeground(1, notification)
        }

    }

    fun createPrevPendingIntent() : PendingIntent {
        val intent = Intent(this, AudioPlayerService::class.java).apply {
            action = Actions.Previous.toString()
        }
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun createNextPendingIntent() : PendingIntent {
        val intent = Intent(this, AudioPlayerService::class.java).apply {
            action = Actions.Next.toString()
        }
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun createPlayPausePendingIntent() : PendingIntent {
        val intent = Intent(this, AudioPlayerService::class.java).apply {
            action = Actions.PauseResume.toString()
        }
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup when the service is destroyed
//        mediaPlayer.release()
//        stopForeground(true)
//        stopForeground(1)
        job?.cancel()
        job = null
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop()
        }
        mediaPlayer.release()
        stopSelf()
    }

}