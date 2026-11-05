package com.matthieu.zi_app.ui.podcast.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AudioPlayerState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
    val speed: Float = 1.0f,
    val isBuffering: Boolean = false,
    val error: String? = null
)

class PodcastAudioPlayer(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentSource: String? = null
    private var progressJob: Job? = null

    private val _playerState = MutableStateFlow(AudioPlayerState())
    val playerState: StateFlow<AudioPlayerState> = _playerState.asStateFlow()

    fun loadAudio(source: String, initialPositionMs: Int = 0, autoPlay: Boolean = false) {
        if (currentSource == source && mediaPlayer != null) {
            if (autoPlay && !(_playerState.value.isPlaying)) {
                play()
            }
            return
        }

        release()
        currentSource = source
        _playerState.value = _playerState.value.copy(isBuffering = true, error = null)

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(source)
                setOnPreparedListener { mp ->
                    val duration = mp.duration
                    _playerState.value = _playerState.value.copy(
                        durationMs = if (duration > 0) duration else 0,
                        isBuffering = false
                    )
                    if (initialPositionMs > 0 && initialPositionMs < duration) {
                        mp.seekTo(initialPositionMs)
                        _playerState.value = _playerState.value.copy(currentPositionMs = initialPositionMs)
                    }
                    if (autoPlay) {
                        play()
                    }
                }
                setOnCompletionListener {
                    _playerState.value = _playerState.value.copy(
                        isPlaying = false,
                        currentPositionMs = it.duration
                    )
                    stopProgressTracking()
                }
                setOnErrorListener { _, what, extra ->
                    _playerState.value = _playerState.value.copy(
                        isPlaying = false,
                        isBuffering = false,
                        error = "Playback error ($what, $extra)"
                    )
                    stopProgressTracking()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            e.printStackTrace()
            _playerState.value = _playerState.value.copy(
                isBuffering = false,
                error = e.localizedMessage ?: "Failed to load audio"
            )
        }
    }

    fun play() {
        val player = mediaPlayer ?: return
        try {
            player.start()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && _playerState.value.speed != 1.0f) {
                try {
                    player.playbackParams = player.playbackParams.setSpeed(_playerState.value.speed)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _playerState.value = _playerState.value.copy(isPlaying = true)
            startProgressTracking()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pause() {
        val player = mediaPlayer ?: return
        try {
            if (player.isPlaying) {
                player.pause()
            }
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
        val player = mediaPlayer ?: return
        try {
            val clamped = positionMs.coerceIn(0, player.duration)
            player.seekTo(clamped)
            _playerState.value = _playerState.value.copy(currentPositionMs = clamped)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun seekBy(offsetMs: Int) {
        val current = _playerState.value.currentPositionMs
        seekTo(current + offsetMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        _playerState.value = _playerState.value.copy(speed = speed)
        val player = mediaPlayer ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (player.isPlaying) {
                    player.playbackParams = player.playbackParams.setSpeed(speed)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        _playerState.value = _playerState.value.copy(
                            currentPositionMs = player.currentPosition
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
        stopProgressTracking()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // ignore
        }
        mediaPlayer = null
        currentSource = null
        _playerState.value = AudioPlayerState()
    }
}

