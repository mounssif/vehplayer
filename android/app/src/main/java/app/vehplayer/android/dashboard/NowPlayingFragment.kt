package app.vehplayer.android.dashboard

import android.content.Intent
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.palette.graphics.Palette
import app.vehplayer.android.R
import app.vehplayer.android.media.NotificationAccess
import app.vehplayer.android.media.VehplayerNotificationListenerService

/**
 * Page 1 of heroPager. Real media info (Phase 2): MediaSessionManager, via
 * VehplayerNotificationListenerService as the required listener component
 * (see that class's doc comment - media state has nothing to do with
 * notification content, the API still requires an enabled listener).
 * Falls back to the static idle state both when nothing's playing and when
 * the permission isn't granted yet - the two idle sub-states differ only in
 * whether tapping opens the settings screen.
 */
class NowPlayingFragment : Fragment(R.layout.fragment_now_playing) {

    private lateinit var idleLayout: LinearLayout
    private lateinit var idleText: TextView
    private lateinit var contentLayout: LinearLayout
    private lateinit var albumArt: ImageView
    private lateinit var trackTitle: TextView
    private lateinit var trackArtist: TextView
    private lateinit var btnPrevious: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton

    private var controller: MediaController? = null
    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = refresh()
        override fun onSessionDestroyed() = reloadController()
    }

    private val onNotificationChange = { reloadController() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        idleLayout = view.findViewById(R.id.nowPlayingIdle)
        idleText = view.findViewById(R.id.nowPlayingIdleText)
        contentLayout = view.findViewById(R.id.nowPlayingContent)
        albumArt = view.findViewById(R.id.albumArt)
        trackTitle = view.findViewById(R.id.trackTitle)
        trackArtist = view.findViewById(R.id.trackArtist)
        btnPrevious = view.findViewById(R.id.btnPrevious)
        btnPlayPause = view.findViewById(R.id.btnPlayPause)
        btnNext = view.findViewById(R.id.btnNext)

        btnPrevious.setOnClickListener { controller?.transportControls?.skipToPrevious() }
        btnNext.setOnClickListener { controller?.transportControls?.skipToNext() }
        btnPlayPause.setOnClickListener {
            val playing = controller?.playbackState?.state == PlaybackState.STATE_PLAYING
            if (playing) controller?.transportControls?.pause() else controller?.transportControls?.play()
        }

        VehplayerNotificationListenerService.addChangeListener(onNotificationChange)
        reloadController()
    }

    override fun onDestroyView() {
        controller?.unregisterCallback(controllerCallback)
        VehplayerNotificationListenerService.removeChangeListener(onNotificationChange)
        super.onDestroyView()
    }

    private fun reloadController() {
        controller?.unregisterCallback(controllerCallback)
        controller = null

        if (!NotificationAccess.isEnabled(requireContext())) {
            showIdle("Tap to enable Now Playing", enablePrompt = true)
            return
        }
        val active = VehplayerNotificationListenerService.activeMediaController(requireContext())
        if (active == null) {
            showIdle("Nothing playing", enablePrompt = false)
            return
        }
        controller = active
        active.registerCallback(controllerCallback)
        refresh()
    }

    private fun refresh() {
        val active = controller
        val metadata = active?.metadata
        if (active == null || metadata == null) {
            showIdle("Nothing playing", enablePrompt = false)
            return
        }
        idleLayout.visibility = View.GONE
        contentLayout.visibility = View.VISIBLE

        trackTitle.text = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown title"
        trackArtist.text = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown artist"

        val art = metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
        if (art != null) {
            albumArt.setImageBitmap(art)
            // Tint the play button badge from the actual album art's
            // dominant color instead of the fixed accent amber - a small
            // per-track touch, falls back silently if Palette finds nothing
            // usable (e.g. flat/blank art).
            Palette.from(art).generate { palette ->
                val color = palette?.getVibrantColor(resources.getColor(R.color.dash_accent_amber, null))
                if (color != null && isAdded) {
                    btnPlayPause.background.setTint(color)
                }
            }
        } else {
            albumArt.setImageDrawable(null)
        }

        val playing = active.playbackState?.state == PlaybackState.STATE_PLAYING
        btnPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun showIdle(message: String, enablePrompt: Boolean) {
        contentLayout.visibility = View.GONE
        idleLayout.visibility = View.VISIBLE
        idleText.text = message
        idleLayout.setOnClickListener {
            if (enablePrompt) startActivity(NotificationAccess.settingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
