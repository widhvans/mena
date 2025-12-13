package com.provideoplayer

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.provideoplayer.databinding.ActivityPlayerBinding
import java.io.File
import kotlin.math.abs

/**
 * Video Player Activity with ExoPlayer
 * Features: Gestures, Subtitles, Audio Tracks, Brightness/Volume control
 */
@SuppressLint("UnsafeOptInUsageError")
class PlayerActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_VIDEO_TITLE = "video_title"
        const val EXTRA_VIDEO_PATH = "video_path"
        
        private const val SEEK_INCREMENT = 10000L // 10 seconds
        private const val GESTURE_THRESHOLD = 50
        private const val HIDE_CONTROLS_DELAY = 3000L
    }
    
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var playerView: PlayerView
    private lateinit var player: ExoPlayer
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var audioManager: AudioManager
    
    private var videoUri: String = ""
    private var videoTitle: String = ""
    private var videoPath: String = ""
    
    // Gesture handling
    private lateinit var gestureDetector: GestureDetector
    private var screenBrightness = 0.5f
    private var currentVolume = 0
    private var maxVolume = 0
    private var isControlsLocked = false
    private var currentAspectRatioMode = 0 // 0=Fit, 1=Fill, 2=Stretch
    
    // Playback speed options
    private val speedOptions = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    private val speedLabels = arrayOf("0.25x", "0.5x", "0.75x", "1x", "1.25x", "1.5x", "1.75x", "2x")
    private var currentSpeedIndex = 3 // Default 1x
    
    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideSystemUI() }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get intent extras
        videoUri = intent.getStringExtra(EXTRA_VIDEO_URI) ?: ""
        videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "Video"
        videoPath = intent.getStringExtra(EXTRA_VIDEO_PATH) ?: ""
        
        if (videoUri.isEmpty()) {
            Toast.makeText(this, R.string.error_playing_video, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        setupWindow()
        setupAudioManager()
        setupPlayer()
        setupGestures()
        setupControls()
        
        playVideo(Uri.parse(videoUri))
    }
    
    private fun setupWindow() {
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Fullscreen immersive
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemUI()
        
        // Set landscape orientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        
        // Get current brightness
        try {
            screenBrightness = Settings.System.getInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            ) / 255f
        } catch (e: Exception) {
            screenBrightness = 0.5f
        }
    }
    
    private fun hideSystemUI() {
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    
    private fun setupAudioManager() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }
    
    private fun setupPlayer() {
        playerView = binding.playerView
        
        // Create track selector for audio/subtitle selection
        trackSelector = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters().setPreferredAudioLanguage("en"))
        }
        
        // Create ExoPlayer
        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setSeekBackIncrementMs(SEEK_INCREMENT)
            .setSeekForwardIncrementMs(SEEK_INCREMENT)
            .build()
        
        playerView.player = player
        playerView.keepScreenOn = true
        
        // Set video title
        playerView.findViewById<TextView>(R.id.exo_title)?.text = videoTitle
        
        // Player listener
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> showLoading(true)
                    Player.STATE_READY -> showLoading(false)
                    Player.STATE_ENDED -> {
                        // Video ended - could auto-play next
                    }
                    Player.STATE_IDLE -> {}
                }
            }
            
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(
                    this@PlayerActivity,
                    "Error: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayPauseButton(isPlaying)
                if (isPlaying) {
                    scheduleHideControls()
                }
            }
        })
    }
    
    private fun playVideo(uri: Uri) {
        // Create media item
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(uri)
        
        // Check for external subtitle files
        findSubtitleFile(videoPath)?.let { subtitleFile ->
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subtitleFile))
                .setMimeType(getSubtitleMimeType(subtitleFile.extension))
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
        }
        
        player.setMediaItem(mediaItemBuilder.build())
        player.prepare()
        player.playWhenReady = true
    }
    
    private fun findSubtitleFile(videoPath: String): File? {
        if (videoPath.isEmpty()) return null
        
        val videoFile = File(videoPath)
        val baseName = videoFile.nameWithoutExtension
        val parentDir = videoFile.parentFile ?: return null
        
        // Look for common subtitle formats
        val subtitleExtensions = listOf("srt", "ass", "ssa", "vtt", "ttml")
        
        for (ext in subtitleExtensions) {
            val subtitleFile = File(parentDir, "$baseName.$ext")
            if (subtitleFile.exists()) {
                return subtitleFile
            }
        }
        
        return null
    }
    
    private fun getSubtitleMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "srt" -> MimeTypes.APPLICATION_SUBRIP
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "vtt" -> MimeTypes.TEXT_VTT
            "ttml" -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private var isScrolling = false
            private var scrollStartX = 0f
            private var scrollStartY = 0f
            private var scrollType = 0 // 0=none, 1=brightness, 2=volume
            
            override fun onDown(e: MotionEvent): Boolean {
                scrollStartX = e.x
                scrollStartY = e.y
                isScrolling = false
                scrollType = 0
                return true
            }
            
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!isControlsLocked) {
                    toggleControls()
                }
                return true
            }
            
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isControlsLocked) return true
                
                val screenWidth = binding.gestureOverlay.width
                val x = e.x
                
                if (x < screenWidth / 2) {
                    // Double tap left - rewind
                    seekBackward()
                    showSeekIndicator(false)
                } else {
                    // Double tap right - forward
                    seekForward()
                    showSeekIndicator(true)
                }
                return true
            }
            
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (isControlsLocked || e1 == null) return false
                
                val deltaY = e1.y - e2.y
                val deltaX = e2.x - e1.x
                
                // Determine scroll type on first significant movement
                if (!isScrolling && (abs(deltaY) > GESTURE_THRESHOLD || abs(deltaX) > GESTURE_THRESHOLD)) {
                    isScrolling = true
                    
                    // Vertical scroll - brightness or volume
                    if (abs(deltaY) > abs(deltaX)) {
                        val screenWidth = binding.gestureOverlay.width
                        scrollType = if (e1.x < screenWidth / 2) 1 else 2 // 1=brightness, 2=volume
                    }
                }
                
                if (isScrolling) {
                    when (scrollType) {
                        1 -> adjustBrightness(deltaY)
                        2 -> adjustVolume(deltaY)
                    }
                }
                
                return true
            }
        })
        
        binding.gestureOverlay.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                hideGestureIndicator()
            }
            true
        }
    }
    
    private fun adjustBrightness(deltaY: Float) {
        val change = deltaY / binding.gestureOverlay.height
        screenBrightness = (screenBrightness + change).coerceIn(0.01f, 1f)
        
        // Apply brightness to window
        val layoutParams = window.attributes
        layoutParams.screenBrightness = screenBrightness
        window.attributes = layoutParams
        
        showGestureIndicator(
            R.drawable.ic_brightness,
            "${(screenBrightness * 100).toInt()}%",
            (screenBrightness * 100).toInt()
        )
    }
    
    private fun adjustVolume(deltaY: Float) {
        val change = (deltaY / binding.gestureOverlay.height) * maxVolume
        currentVolume = (currentVolume + change.toInt()).coerceIn(0, maxVolume)
        
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            currentVolume,
            0
        )
        
        val volumePercent = (currentVolume * 100) / maxVolume
        showGestureIndicator(R.drawable.ic_volume, "$volumePercent%", volumePercent)
    }
    
    private fun showGestureIndicator(iconRes: Int, text: String, progress: Int) {
        binding.gestureIndicator.visibility = View.VISIBLE
        binding.ivGestureIcon.setImageResource(iconRes)
        binding.tvGestureValue.text = text
        binding.gestureProgress.progress = progress
    }
    
    private fun hideGestureIndicator() {
        binding.gestureIndicator.visibility = View.GONE
    }
    
    private fun showSeekIndicator(isForward: Boolean) {
        binding.seekIndicator.visibility = View.VISIBLE
        binding.ivSeekIcon.setImageResource(
            if (isForward) R.drawable.ic_forward_10 else R.drawable.ic_replay_10
        )
        binding.tvSeekValue.text = "10s"
        
        binding.seekIndicator.postDelayed({
            binding.seekIndicator.visibility = View.GONE
        }, 500)
    }
    
    private fun seekForward() {
        player.seekTo(player.currentPosition + SEEK_INCREMENT)
    }
    
    private fun seekBackward() {
        player.seekTo((player.currentPosition - SEEK_INCREMENT).coerceAtLeast(0))
    }
    
    private fun toggleControls() {
        if (playerView.isControllerFullyVisible) {
            playerView.hideController()
            hideSystemUI()
        } else {
            playerView.showController()
            scheduleHideControls()
        }
    }
    
    private fun scheduleHideControls() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, HIDE_CONTROLS_DELAY)
    }
    
    private fun setupControls() {
        // Back button
        playerView.findViewById<ImageButton>(R.id.exo_back)?.setOnClickListener {
            finish()
        }
        
        // Play/Pause button
        playerView.findViewById<ImageButton>(R.id.exo_play_pause)?.setOnClickListener {
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
        
        // Rewind button
        playerView.findViewById<ImageButton>(R.id.exo_rew)?.setOnClickListener {
            seekBackward()
        }
        
        // Forward button
        playerView.findViewById<ImageButton>(R.id.exo_ffwd)?.setOnClickListener {
            seekForward()
        }
        
        // Speed button
        playerView.findViewById<MaterialButton>(R.id.exo_speed)?.setOnClickListener {
            showSpeedDialog()
        }
        
        // Audio track button
        playerView.findViewById<ImageButton>(R.id.exo_audio_track)?.setOnClickListener {
            showAudioTrackDialog()
        }
        
        // Subtitle button
        playerView.findViewById<ImageButton>(R.id.exo_subtitle)?.setOnClickListener {
            showSubtitleDialog()
        }
        
        // Aspect ratio button
        playerView.findViewById<ImageButton>(R.id.exo_aspect_ratio)?.setOnClickListener {
            cycleAspectRatio()
        }
        
        // Rotate button
        playerView.findViewById<ImageButton>(R.id.exo_rotate)?.setOnClickListener {
            toggleOrientation()
        }
        
        // Lock button
        binding.btnLock.setOnClickListener {
            toggleControlsLock()
        }
        
        // Settings button
        playerView.findViewById<ImageButton>(R.id.exo_settings)?.setOnClickListener {
            showSettingsDialog()
        }
    }
    
    private fun updatePlayPauseButton(isPlaying: Boolean) {
        playerView.findViewById<ImageButton>(R.id.exo_play_pause)?.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }
    
    private fun showLoading(show: Boolean) {
        binding.loadingSpinner.visibility = if (show) View.VISIBLE else View.GONE
    }
    
    private fun showSpeedDialog() {
        MaterialAlertDialogBuilder(this, R.style.TrackSelectionDialog)
            .setTitle(R.string.playback_speed)
            .setSingleChoiceItems(speedLabels, currentSpeedIndex) { dialog, which ->
                currentSpeedIndex = which
                player.setPlaybackSpeed(speedOptions[which])
                playerView.findViewById<MaterialButton>(R.id.exo_speed)?.text = speedLabels[which]
                dialog.dismiss()
            }
            .show()
    }
    
    private fun showAudioTrackDialog() {
        val tracks = player.currentTracks
        val audioTracks = mutableListOf<String>()
        val audioIndices = mutableListOf<Int>()
        var selectedIndex = 0
        
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val language = format.language ?: "Unknown"
                    val label = format.label ?: language
                    audioTracks.add("$label (${format.sampleRate}Hz)")
                    audioIndices.add(groupIndex)
                    
                    if (group.isTrackSelected(i)) {
                        selectedIndex = audioTracks.size - 1
                    }
                }
            }
        }
        
        if (audioTracks.isEmpty()) {
            Toast.makeText(this, "No additional audio tracks available", Toast.LENGTH_SHORT).show()
            return
        }
        
        MaterialAlertDialogBuilder(this, R.style.TrackSelectionDialog)
            .setTitle(R.string.select_audio_track)
            .setSingleChoiceItems(audioTracks.toTypedArray(), selectedIndex) { dialog, which ->
                selectAudioTrack(audioIndices[which])
                dialog.dismiss()
            }
            .show()
    }
    
    private fun selectAudioTrack(groupIndex: Int) {
        val tracks = player.currentTracks
        val group = tracks.groups[groupIndex]
        
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setOverrideForType(
                    TrackSelectionOverride(group.mediaTrackGroup, 0)
                )
        )
    }
    
    private fun showSubtitleDialog() {
        val tracks = player.currentTracks
        val subtitleTracks = mutableListOf<String>()
        val subtitleGroups = mutableListOf<Int>()
        var selectedIndex = 0
        
        // Add "Off" option
        subtitleTracks.add(getString(R.string.no_subtitle))
        subtitleGroups.add(-1)
        
        tracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val language = format.language ?: "Unknown"
                    val label = format.label ?: language
                    subtitleTracks.add(label)
                    subtitleGroups.add(groupIndex)
                    
                    if (group.isTrackSelected(i)) {
                        selectedIndex = subtitleTracks.size - 1
                    }
                }
            }
        }
        
        if (subtitleTracks.size <= 1) {
            Toast.makeText(this, "No subtitles available", Toast.LENGTH_SHORT).show()
            return
        }
        
        MaterialAlertDialogBuilder(this, R.style.TrackSelectionDialog)
            .setTitle(R.string.select_subtitle)
            .setSingleChoiceItems(subtitleTracks.toTypedArray(), selectedIndex) { dialog, which ->
                if (subtitleGroups[which] == -1) {
                    disableSubtitles()
                } else {
                    selectSubtitleTrack(subtitleGroups[which])
                }
                dialog.dismiss()
            }
            .show()
    }
    
    private fun selectSubtitleTrack(groupIndex: Int) {
        val tracks = player.currentTracks
        val group = tracks.groups[groupIndex]
        
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setOverrideForType(
                    TrackSelectionOverride(group.mediaTrackGroup, 0)
                )
        )
    }
    
    private fun disableSubtitles() {
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        )
    }
    
    private fun cycleAspectRatio() {
        currentAspectRatioMode = (currentAspectRatioMode + 1) % 3
        
        val mode = when (currentAspectRatioMode) {
            0 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        
        playerView.resizeMode = mode
        
        val modeName = when (currentAspectRatioMode) {
            0 -> "Fit"
            1 -> "Zoom"
            2 -> "Fill"
            else -> "Fit"
        }
        
        Toast.makeText(this, modeName, Toast.LENGTH_SHORT).show()
    }
    
    private fun toggleOrientation() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }
    
    private fun toggleControlsLock() {
        isControlsLocked = !isControlsLocked
        
        if (isControlsLocked) {
            playerView.hideController()
            playerView.useController = false
            binding.btnLock.setIconResource(R.drawable.ic_lock)
            binding.btnLock.visibility = View.VISIBLE
        } else {
            playerView.useController = true
            playerView.showController()
            binding.btnLock.setIconResource(R.drawable.ic_lock_open)
            binding.btnLock.visibility = View.GONE
        }
    }
    
    private fun showSettingsDialog() {
        val options = arrayOf(
            getString(R.string.playback_speed),
            getString(R.string.audio_tracks),
            getString(R.string.subtitles),
            getString(R.string.aspect_ratio)
        )
        
        MaterialAlertDialogBuilder(this, R.style.TrackSelectionDialog)
            .setTitle(R.string.settings)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSpeedDialog()
                    1 -> showAudioTrackDialog()
                    2 -> showSubtitleDialog()
                    3 -> cycleAspectRatio()
                }
            }
            .show()
    }
    
    // Picture-in-Picture support
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player.isPlaying) {
            enterPictureInPicture()
        }
    }
    
    private fun enterPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(16, 9)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            enterPictureInPictureMode(params)
        }
    }
    
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            playerView.useController = false
        } else {
            playerView.useController = !isControlsLocked
        }
    }
    
    override fun onStart() {
        super.onStart()
        if (::player.isInitialized) {
            player.playWhenReady = true
        }
    }
    
    override fun onStop() {
        super.onStop()
        if (::player.isInitialized && !isInPictureInPictureMode) {
            player.playWhenReady = false
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        hideHandler.removeCallbacks(hideRunnable)
        if (::player.isInitialized) {
            player.release()
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isControlsLocked) {
            toggleControlsLock()
        } else {
            super.onBackPressed()
        }
    }
}
