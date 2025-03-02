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
        Start
    }

    inner class ServiceBinder : Binder() {

        fun getService() : AudioPlayerService = this@AudioPlayerService

        fun setAudioList(list:List<Story>) {
            this@AudioPlayerService.trackList = list.toMutableList()
        }

        fun maxDuration()     = this@AudioPlayerService.maxDuration
        fun currentDuration() = this@AudioPlayerService.currentDuration
        fun isPlaying()       = this@AudioPlayerService.isPlaying
        fun currentTrack()    = this@AudioPlayerService.currentTrack
    }

    val binder               = ServiceBinder()
    private var mediaPlayer  = MediaPlayer()
    private val currentTrack = MutableStateFlow<Story>(defaultStories[0])
    private var trackList    = mutableListOf<Story>()
    private val scope        = CoroutineScope(Dispatchers.Main)
    private var job : Job? = null

    val isPlaying       = MutableStateFlow(false)
    val maxDuration     = MutableStateFlow(0f)
    val currentDuration = MutableStateFlow(0f)

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
        val prevIndex = if (index<0) trackList.size - 1 else index - 1
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

            maxDuration.update { mediaPlayer.duration.toFloat() }

            while(true) {
                currentDuration.update { mediaPlayer.currentPosition.toFloat() }
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
            .setStyle(style)
            .setContentTitle(story.title)
            .addAction(R.drawable.ic_baseline_navigate_before_24, "prev", createPrevPendingIntent())
            .addAction(
                if (mediaPlayer.isPlaying)
                    R.drawable.ic_baseline_pause_24
                else
                    R.drawable.ic_baseline_play_arrow_24,
                "play_pause", createPlayPausePendingIntent())
            .addAction(R.drawable.ic_baseline_navigate_next_24, "next", createNextPendingIntent())
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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
            action = PREV
        }
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun createNextPendingIntent() : PendingIntent {
        val intent = Intent(this, AudioPlayerService::class.java).apply {
            action = NEXT
        }
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun createPlayPausePendingIntent() : PendingIntent {
        val intent = Intent(this, AudioPlayerService::class.java).apply {
            action = PLAY_PAUSE
        }
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

}