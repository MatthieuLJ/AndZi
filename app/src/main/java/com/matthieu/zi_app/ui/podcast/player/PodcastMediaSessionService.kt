package com.matthieu.zi_app.ui.podcast.player

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * AndroidX Media3 MediaSessionService managing background playback,
 * system media notifications, lock-screen controls, and headphone events.
 */
class PodcastMediaSessionService : MediaSessionService() {

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val session = PodcastAudioPlayer.activeMediaSession
        if (session != null && !isSessionAdded(session)) {
            addSession(session)
        }
        return session
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = PodcastAudioPlayer.activePlayer
        if (player != null && !player.isPlaying) {
            pauseAllPlayersAndStopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }
}
