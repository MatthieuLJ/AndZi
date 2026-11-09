package com.matthieu.zi_app.ui.podcast.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class AudioPlayerState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
    val speed: Float = 1.0f,
    val isBuffering: Boolean = false,
    val error: String? = null,
    val episodeId: Int? = null,
    val title: String? = null,
    val channelTitle: String? = null,
    val isMiniPlayerVisible: Boolean = false
)

/**
 * Modern audio player backed by AndroidX Media3 (ExoPlayer + MediaSession).
 * Supports background playback, lock-screen notifications, audio focus,
 * headphone disconnect (becoming noisy) pauses, and variable playback speed.
 *
 * Thread-safe: Ensures all ExoPlayer and MediaSession calls execute on the Main Looper.
 */
class PodcastAudioPlayer(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var currentSource: String? = null
    private var progressJob: Job? = null

    private val _playerState = MutableStateFlow(AudioPlayerState())
    val playerState: StateFlow<AudioPlayerState> = _playerState.asStateFlow()

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            scope.launch(Dispatchers.Main) {
                block()
            }
        }
    }

    private fun ensurePlayerInitialized(): ExoPlayer {
        val existing = exoPlayer
        if (existing != null) return existing

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = try {
            ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
                .setHandleAudioBecomingNoisy(true)
                .build()
        } catch (_: Throwable) {
            ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
                .setHandleAudioBecomingNoisy(false)
                .build()
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.value = _playerState.value.copy(
                    isPlaying = isPlaying,
                    isMiniPlayerVisible = _playerState.value.episodeId != null
                )
                if (isPlaying) {
                    startProgressTracking()
                } else {
                    stopProgressTracking()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _playerState.value = _playerState.value.copy(isBuffering = true)
                    }
                    Player.STATE_READY -> {
                        val dur = player.duration
                        val validDur = if (dur > 0 && dur != C.TIME_UNSET) dur.toInt() else _playerState.value.durationMs
                        _playerState.value = _playerState.value.copy(
                            isBuffering = false,
                            durationMs = validDur
                        )
                    }
                    Player.STATE_ENDED -> {
                        _playerState.value = _playerState.value.copy(
                            isPlaying = false,
                            isBuffering = false,
                            currentPositionMs = _playerState.value.durationMs
                        )
                        stopProgressTracking()
                    }
                    Player.STATE_IDLE -> {
                        _playerState.value = _playerState.value.copy(isBuffering = false)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _playerState.value = _playerState.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    error = error.localizedMessage ?: "Playback error (${error.errorCode})"
                )
                stopProgressTracking()
            }
        })

        try {
            mediaSession = MediaSession.Builder(context, player)
                .setId("PodcastMediaSession_${System.currentTimeMillis()}")
                .build()
            activeMediaSession = mediaSession
            activePlayer = player
        } catch (_: Exception) {
            // In unit test / mock environments, MediaSession may not bind to native service
        }

        exoPlayer = player
        return player
    }

    fun loadAudio(
        source: String,
        initialPositionMs: Int = 0,
        autoPlay: Boolean = false,
        episodeId: Int? = null,
        title: String? = null,
        channelTitle: String? = null
    ) {
        runOnMain {
            loadAudioInternal(source, initialPositionMs, autoPlay, episodeId, title, channelTitle)
        }
    }

    private fun loadAudioInternal(
        source: String,
        initialPositionMs: Int,
        autoPlay: Boolean,
        episodeId: Int?,
        title: String?,
        channelTitle: String?
    ) {
        val player = ensurePlayerInitialized()

        if (currentSource == source) {
            _playerState.value = _playerState.value.copy(
                episodeId = episodeId ?: _playerState.value.episodeId,
                title = title ?: _playerState.value.title,
                channelTitle = channelTitle ?: _playerState.value.channelTitle,
                isMiniPlayerVisible = true
            )
            if (autoPlay && !_playerState.value.isPlaying) {
                playInternal()
            }
            return
        }

        currentSource = source
        _playerState.value = _playerState.value.copy(
            isBuffering = true,
            error = null,
            episodeId = episodeId,
            title = title,
            channelTitle = channelTitle,
            isMiniPlayerVisible = true
        )

        try {
            val uri = if (source.startsWith("http://") || source.startsWith("https://") ||
                source.startsWith("content://") || source.startsWith("file://")
            ) {
                Uri.parse(source)
            } else {
                Uri.fromFile(File(source))
            }

            val metadata = MediaMetadata.Builder()
                .setTitle(title ?: "Story")
                .setArtist(channelTitle ?: "")
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(metadata)
                .build()

            player.setMediaItem(mediaItem)
            player.prepare()

            if (initialPositionMs > 0) {
                player.seekTo(initialPositionMs.toLong())
                _playerState.value = _playerState.value.copy(currentPositionMs = initialPositionMs)
            }

            if (_playerState.value.speed != 1.0f) {
                player.playbackParameters = PlaybackParameters(_playerState.value.speed)
            }

            if (autoPlay) {
                player.play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _playerState.value = _playerState.value.copy(
                isBuffering = false,
                error = e.localizedMessage ?: "Failed to load audio"
            )
        }
    }

    fun play() {
        runOnMain { playInternal() }
    }

    private fun playInternal() {
        val player = exoPlayer ?: ensurePlayerInitialized()
        try {
            if (_playerState.value.speed != 1.0f) {
                player.playbackParameters = PlaybackParameters(_playerState.value.speed)
            }
            player.play()
            _playerState.value = _playerState.value.copy(isPlaying = true, isMiniPlayerVisible = true)
            startProgressTracking()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pause() {
        runOnMain { pauseInternal() }
    }

    private fun pauseInternal() {
        val player = exoPlayer ?: return
        try {
            player.pause()
            _playerState.value = _playerState.value.copy(isPlaying = false)
            stopProgressTracking()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun togglePlayPause() {
        if (_playerState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Int) {
        runOnMain { seekToInternal(positionMs) }
    }

    private fun seekToInternal(positionMs: Int) {
        val player = exoPlayer ?: return
        try {
            val duration = if (player.duration > 0 && player.duration != C.TIME_UNSET) player.duration else Long.MAX_VALUE
            val clamped = positionMs.toLong().coerceIn(0L, duration)
            player.seekTo(clamped)
            _playerState.value = _playerState.value.copy(currentPositionMs = clamped.toInt())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun seekBy(offsetMs: Int) {
        runOnMain {
            val current = _playerState.value.currentPositionMs
            seekToInternal(current + offsetMs)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        runOnMain { setPlaybackSpeedInternal(speed) }
    }

    private fun setPlaybackSpeedInternal(speed: Float) {
        _playerState.value = _playerState.value.copy(speed = speed)
        val player = exoPlayer ?: return
        try {
            player.playbackParameters = PlaybackParameters(speed)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dismissMiniPlayer() {
        runOnMain { dismissMiniPlayerInternal() }
    }

    private fun dismissMiniPlayerInternal() {
        pauseInternal()
        _playerState.value = _playerState.value.copy(isMiniPlayerVisible = false)
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        _playerState.value = _playerState.value.copy(
                            currentPositionMs = player.currentPosition.toInt()
                        )
                    }
                }
                delay(200)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        runOnMain { releaseInternal() }
    }

    private fun releaseInternal() {
        stopProgressTracking()
        try {
            mediaSession?.run {
                if (activeMediaSession == this) activeMediaSession = null
                release()
            }
            mediaSession = null

            exoPlayer?.run {
                if (activePlayer == this) activePlayer = null
                stop()
                release()
            }
            exoPlayer = null
        } catch (_: Exception) {
            // ignore
        }
        currentSource = null
        _playerState.value = AudioPlayerState()
    }

    companion object {
        var activeMediaSession: MediaSession? = null
        var activePlayer: Player? = null
    }
}
