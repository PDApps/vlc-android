/*
 * ************************************************************************
 *  VideoPlayerOverlayDelegate.kt
 * *************************************************************************
 * Copyright © 2020 VLC authors and VideoLAN
 * Author: Nicolas POMEPUY
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 * **************************************************************************
 *
 *
 */

package org.videolan.vlc.gui.video

import android.animation.Animator
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import androidx.window.layout.FoldingFeature
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.AndroidDevices
import org.videolan.tools.*
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.databinding.PlayerHudBinding
import org.videolan.vlc.databinding.PlayerHudRightBinding
import org.videolan.vlc.gui.dialogs.VideoTracksDialog
import org.videolan.vlc.gui.helpers.BookmarkListDelegateImpl
import org.videolan.vlc.gui.helpers.OnRepeatListenerKey
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.helpers.UiTools.showVideoTrack
import org.videolan.vlc.gui.view.PlayerProgress
import org.videolan.vlc.manageAbRepeatStep
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.*
import java.text.DateFormat
import java.util.*

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
open class VideoPlayerOverlayDelegate (private val player: VideoPlayerActivity) {

    private lateinit var playerOverlayBrightness: ConstraintLayout
    private lateinit var brightnessValueText: TextView
    private lateinit var playerBrightnessProgress: PlayerProgress
    private lateinit var playerOverlayVolume: ConstraintLayout
    private lateinit var volumeValueText: TextView
    private lateinit var playerVolumeProgress: PlayerProgress
    var info: TextView? = null
    var subinfo: TextView? = null
    var overlayInfo: View? = null
    lateinit var playerUiContainer:ViewGroup

    lateinit var hudBinding: PlayerHudBinding

    val isHudBindingIsInitialized: Boolean
        get() = ::hudBinding.isInitialized

    lateinit var hudRightBinding: PlayerHudRightBinding
    private var overlayBackground: View? = null


    private var overlayTimeout = 0
    private var wasPlaying = true

    lateinit var playToPause: AnimatedVectorDrawableCompat
    lateinit var pauseToPlay: AnimatedVectorDrawableCompat

    private val hudBackground: View? by lazy { player.findViewById(R.id.hud_background) }
    private val hudRightBackground: View? by lazy { player.findViewById(R.id.hud_right_background) }

    private lateinit var abRepeatAddMarker: Button

    var seekButtons: Boolean = false
    var hasPlaylist: Boolean = false
    private var hingeSnackShown: Boolean = false

    var enableSubs = true
    private lateinit var bookmarkListDelegate: BookmarkListDelegate

    fun isHudBindingInitialized() = ::hudBinding.isInitialized
    fun isHudRightBindingInitialized() = ::hudRightBinding.isInitialized

    private var orientationLockedBeforeLock: Boolean = false
    lateinit var closeButton: View
    lateinit var hingeArrowRight: ImageView
    lateinit var hingeArrowLeft: ImageView
    lateinit var playlist: RecyclerView
    lateinit var playlistSearchText: TextInputLayout
    var foldingFeature: FoldingFeature? = null
        set(value) {
            field = value
            manageHinge()
        }

    /**
     * Changes the device layout depending on the scree foldable status and features
     */
     fun manageHinge() {
         resetHingeLayout()
         if (foldingFeature == null || !Settings.getInstance(player).getBoolean(ALLOW_FOLD_AUTO_LAYOUT, true))  return
        val foldingFeature = foldingFeature!!

         //device is fully occluded and split vertically. We display the controls on the half left or right side
         if (foldingFeature.occlusionType == FoldingFeature.OcclusionType.FULL && foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL) {
             val onRight = Settings.getInstance(player).getBoolean(HINGE_ON_RIGHT, true)
             hingeArrowLeft.visibility = if (onRight && ::hudBinding.isInitialized) View.VISIBLE else View.GONE
             hingeArrowRight.visibility = if (!onRight && ::hudBinding.isInitialized) View.VISIBLE else View.GONE
             val halfScreenSize = player.getScreenWidth() - foldingFeature.bounds.right
             arrayOf(playerUiContainer, hudBackground, hudRightBackground).forEach {
                 it?.let { view ->
                     val lp = (view.layoutParams as ConstraintLayout.LayoutParams)
                     lp.width = halfScreenSize
                     if (onRight) {
                         lp.endToEnd = 0
                         lp.startToStart = -1
                     } else {
                         lp.startToStart = 0
                         lp.endToEnd = -1
                     }
                     view.layoutParams = lp
                 }
             }
             showHingeSnackIfNeeded()
         } else {
             //device is separated and half opened. We display the controls on the bottom half and the video on the top half
             if (foldingFeature.state == FoldingFeature.State.HALF_OPENED) {
                 val videoLayoutLP = (player.videoLayout!!.layoutParams as ViewGroup.LayoutParams)
                 val halfScreenSize = foldingFeature.bounds.top
                 videoLayoutLP.height = halfScreenSize
                 player.videoLayout!!.layoutParams = videoLayoutLP
                 player.findViewById<FrameLayout>(R.id.player_surface_frame).children.forEach { it.requestLayout() }

                 arrayOf(playerUiContainer).forEach {
                     val lp = (it.layoutParams as ConstraintLayout.LayoutParams)
                     lp.height = halfScreenSize
                     lp.bottomToBottom = 0
                     it.layoutParams = lp
                 }
                 arrayOf(hudBackground, hudRightBackground).forEach {
                     it?.setGone()
                 }
                 showHingeSnackIfNeeded()
             }
         }
     }

    /**
     * Shows the fold layout snackbar if needed
     */
    private fun showHingeSnackIfNeeded() {
        if (!hingeSnackShown) {
            UiTools.snackerConfirm(player, player.getString(R.string.fold_optimized), confirmMessage = R.string.undo) {
                player.resizeDelegate.showResizeOverlay()
            }
            hingeSnackShown = true
        }
    }

    /**
     * Resets the layout to normal after a fold/hinge status change
     */
    private fun resetHingeLayout() {
        arrayOf(playerUiContainer, hudBackground, hudRightBackground).forEach {
            it?.let { view ->
                val lp = (view.layoutParams as ViewGroup.LayoutParams)
                lp.width = RelativeLayout.LayoutParams.MATCH_PARENT
                view.layoutParams = lp
            }
        }
        arrayOf(playerUiContainer).forEach {
            val lp = (it.layoutParams as ConstraintLayout.LayoutParams)
            lp.height = RelativeLayout.LayoutParams.MATCH_PARENT
            lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            it.layoutParams = lp
        }
        if (::hudBinding.isInitialized) arrayOf(hudBackground, hudRightBackground).forEach {
            it?.setVisible()
        }
        hingeArrowLeft.visibility = View.GONE
        hingeArrowRight.visibility = View.GONE
        val lp = (player.videoLayout!!.layoutParams as ViewGroup.LayoutParams)
        lp.height = RelativeLayout.LayoutParams.MATCH_PARENT
        player.videoLayout!!.layoutParams = lp
        player.findViewById<FrameLayout>(R.id.player_surface_frame).children.forEach { it.requestLayout() }
    }

    fun showTracks() {
        player.showVideoTrack(
                {
                    when (it) {
                        VideoTracksDialog.VideoTrackOption.AUDIO_DELAY -> player.delayDelegate.showAudioDelaySetting()
                        VideoTracksDialog.VideoTrackOption.SUB_DELAY -> player.delayDelegate.showSubsDelaySetting()
                        VideoTracksDialog.VideoTrackOption.SUB_DOWNLOAD -> downloadSubtitles()
                        VideoTracksDialog.VideoTrackOption.SUB_PICK -> pickSubtitles()
                    }
                }, { trackID: Int, trackType: VideoTracksDialog.TrackType ->
            when (trackType) {
                VideoTracksDialog.TrackType.AUDIO -> {
                    player.service?.let { service ->
                        service.setAudioTrack(trackID)
                        service.currentMediaWrapper?.audioTrack = trackID
                        runIO {
                            player.service?.playlistManager?.saveMediaMeta()
                        }
                    }
                }
                VideoTracksDialog.TrackType.SPU -> {
                    player.service?.let { service ->
                        service.setSpuTrack(trackID)
                        runIO {
                            val mw = player.medialibrary.findMedia(service.currentMediaWrapper)
                            if (mw != null && mw.id != 0L) mw.setLongMeta(MediaWrapper.META_SUBTITLE_TRACK, trackID.toLong())
                        }
                    }
                }
                VideoTracksDialog.TrackType.VIDEO -> {
                    player.service?.let { service ->
                        player.seek(service.getTime())
                        service.setVideoTrack(trackID)
                        runIO {
                            val mw = player.medialibrary.findMedia(service.currentMediaWrapper)
                            if (mw != null && mw.id != 0L) mw.setLongMeta(MediaWrapper.META_VIDEOTRACK, trackID.toLong())
                        }
                    }
                }
            }
        })
    }

    fun showInfo(@StringRes textId: Int , duration: Int ,@StringRes subtextId: Int = -1) {
        showInfo(player.getString(textId), duration, if (subtextId == -1) "" else player.getString(subtextId))
    }

    /**
     * Show text in the info view for "duration" milliseconds
     * @param text
     * @param duration
     */
    fun showInfo(text: String, duration: Int, subText:String = "") {
        if (player.isInPictureInPictureMode) return
        initInfoOverlay()
        overlayInfo.setVisible()
        info.setVisible()
        info?.text = text
        if (subText.isNotBlank()) {
            subinfo?.text = subText
            subinfo.setVisible()
        } else subinfo.setGone()
        player.handler.removeMessages(VideoPlayerActivity.FADE_OUT_INFO)
        player.handler.sendEmptyMessageDelayed(VideoPlayerActivity.FADE_OUT_INFO, duration.toLong())
    }

     fun fadeOutInfo(view:View?) {
        if (view?.visibility == View.VISIBLE) {
            view.startAnimation(AnimationUtils.loadAnimation(
                    player, android.R.anim.fade_out))
            view.setInvisible()
        }
    }

    fun initInfoOverlay() {
        val vsc = player.findViewById<ViewStub>(R.id.player_info_stub)
        if (vsc != null) {
            vsc.setVisible()
            // the info textView is not on the overlay
            info = player.findViewById(R.id.player_overlay_textinfo)
            subinfo = player.findViewById(R.id.player_overlay_subtextinfo)
            overlayInfo = player.findViewById(R.id.player_overlay_info)
        }
    }

    /**
     * Show the brightness value with  bar
     * @param brightness the brightness value
     */
    fun showBrightnessBar(brightness: Int) {
        player.handler.sendEmptyMessage(VideoPlayerActivity.FADE_OUT_VOLUME_INFO)
        player.findViewById<ViewStub>(R.id.player_brightness_stub)?.setVisible()
        playerOverlayBrightness = player.findViewById(R.id.player_overlay_brightness)
        brightnessValueText = player.findViewById(R.id.brightness_value_text)
        playerBrightnessProgress = player.findViewById(R.id.playerBrightnessProgress)
        playerOverlayBrightness.setVisible()
        brightnessValueText.text = "$brightness%"
        playerBrightnessProgress.setValue(brightness)
        playerOverlayBrightness.setVisible()
        player.handler.removeMessages(VideoPlayerActivity.FADE_OUT_BRIGHTNESS_INFO)
        player.handler.sendEmptyMessageDelayed(VideoPlayerActivity.FADE_OUT_BRIGHTNESS_INFO, 1000L)
        dimStatusBar(true)
    }

    /**
     * Show the volume value with  bar
     * @param volume the volume value
     */
    fun showVolumeBar(volume: Int, fromTouch: Boolean) {
        player.handler.sendEmptyMessage(VideoPlayerActivity.FADE_OUT_BRIGHTNESS_INFO)
        player.findViewById<ViewStub>(R.id.player_volume_stub)?.setVisible()
        playerOverlayVolume = player.findViewById(R.id.player_overlay_volume)
        volumeValueText = player.findViewById(R.id.volume_value_text)
        playerVolumeProgress = player.findViewById(R.id.playerVolumeProgress)
        volumeValueText.text = "$volume%"
        playerVolumeProgress.isDouble = player.isAudioBoostEnabled
        playerVolumeProgress.setValue(volume)
        playerOverlayVolume.setVisible()
        player.handler.removeMessages(VideoPlayerActivity.FADE_OUT_VOLUME_INFO)
        player.handler.sendEmptyMessageDelayed(VideoPlayerActivity.FADE_OUT_VOLUME_INFO, 1000L)
        dimStatusBar(true)
    }

    /**
     * Dim the status bar and/or navigation icons when needed on Android 3.x.
     * Hide it on Android 4.0 and later
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    fun dimStatusBar(dim: Boolean) {
        if (player.isNavMenu) return

        var visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        var navbar = 0
        if (dim || player.isLocked) {
            player.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            navbar = navbar or (View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LOW_PROFILE or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            if (AndroidUtil.isKitKatOrLater) visibility = visibility or View.SYSTEM_UI_FLAG_IMMERSIVE
            visibility = visibility or View.SYSTEM_UI_FLAG_FULLSCREEN
        } else {
            player.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            visibility = visibility or View.SYSTEM_UI_FLAG_VISIBLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }

        playerUiContainer.setPadding(0, 0, 0, 0)
        playerUiContainer.fitsSystemWindows = !player.isLocked

        if (AndroidDevices.hasNavBar)
            visibility = visibility or navbar
        player.window.decorView.systemUiVisibility = visibility
    }

    /**
     * show overlay
     * @param forceCheck: adjust the timeout in function of playing state
     */
    fun showOverlay(forceCheck: Boolean = false) {
        if (forceCheck) overlayTimeout = 0
        showOverlayTimeout(0)
    }

    /**
     * show overlay
     */
    fun showOverlayTimeout(timeout: Int) {
        player.service?.let { service ->
            if (player.isInPictureInPictureMode) return
            initOverlay()
            if (!::hudBinding.isInitialized) return
            overlayTimeout = when {
                Settings.videoHudDelay == 0 -> VideoPlayerActivity.OVERLAY_INFINITE
                isBookmarkShown() -> VideoPlayerActivity.OVERLAY_INFINITE
                timeout != 0 -> timeout
                service.isPlaying -> when (Settings.videoHudDelay) {
                    0 -> VideoPlayerActivity.OVERLAY_INFINITE
                    else -> Settings.videoHudDelay * 1000
                }
                else -> VideoPlayerActivity.OVERLAY_INFINITE
            }
            if (player.isNavMenu) {
                player.isShowing = true
                return
            }
            if (!player.isShowing) {
                player.isShowing = true
                if (!player.isLocked) {
                    showControls(true)
                }
                if (!isBookmarkShown()) dimStatusBar(false)
                enterAnimate(arrayOf(hudBinding.progressOverlay, hudBackground), 100.dp.toFloat()) {
                    if (overlayTimeout != VideoPlayerActivity.OVERLAY_INFINITE)
                        player.handler.sendMessageDelayed(player.handler.obtainMessage(VideoPlayerActivity.FADE_OUT), overlayTimeout.toLong())
                }
                enterAnimate(arrayOf(hudRightBinding.hudRightOverlay, hudRightBackground), -100.dp.toFloat())

                hingeArrowLeft.animate().alpha(1F)
                hingeArrowRight.animate().alpha(1F)

                if (!player.displayManager.isPrimary)
                    overlayBackground.setVisible()
                updateOverlayPausePlay(true)
                player.onOverlayShown()
            } else {
                if (overlayTimeout != VideoPlayerActivity.OVERLAY_INFINITE)
                    player.handler.sendMessageDelayed(player.handler.obtainMessage(VideoPlayerActivity.FADE_OUT), overlayTimeout.toLong())
            }
            player.handler.removeMessages(VideoPlayerActivity.FADE_OUT)
        }
    }

    fun updateOverlayPausePlay(skipAnim: Boolean = false) {
        if (!::hudBinding.isInitialized) return
        player.service?.let { service ->
            if (service.isPausable) {

                if (skipAnim) {
                    hudBinding.playerOverlayPlay.setImageResource(if (service.isPlaying)
                        R.drawable.ic_pause_player
                    else
                        R.drawable.ic_play_player)
                } else {
                    val drawable = if (service.isPlaying) playToPause else pauseToPlay
                    hudBinding.playerOverlayPlay.setImageDrawable(drawable)
                    if (service.isPlaying != wasPlaying) drawable.start()
                }

                wasPlaying = service.isPlaying
            }
            hudBinding.playerOverlayPlay.requestFocus()
        }
    }

    class OnLayoutListener(val endListener: (() -> Unit)? = null) : View.OnLayoutChangeListener {
        override fun onLayoutChange(
            v: View,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            oldLeft: Int,
            oldTop: Int,
            oldRight: Int,
            oldBottom: Int
        ) {
            if (v.translationY == 0f) {
                endListener?.invoke()
                v.removeOnLayoutChangeListener(this)
            }
        }
    }

    private fun enterAnimate(views: Array<View?>, translationStart: Float, endListener:(()->Unit)? = null) = views.forEach { view ->
        view.setVisible()
        view?.alpha = 1f
        view?.translationY = 0F
        view?.addOnLayoutChangeListener(OnLayoutListener(endListener))
    }

    private fun exitAnimate(views: Array<View?>, translationEnd: Float) = views.forEach { view ->
        view?.animate()?.alpha(0F)?.translationY(translationEnd)?.setDuration(150L)?.setListener(object : Animator.AnimatorListener {
            override fun onAnimationEnd(animation: Animator?) {
                view.setInvisible()
            }
            override fun onAnimationCancel(animation: Animator?) {}
            override fun onAnimationRepeat(animation: Animator?) {}
            override fun onAnimationStart(animation: Animator?) {}
        })
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun initOverlay() {
        player.service?.let { service ->
            val vscRight = player.findViewById<ViewStub>(R.id.player_hud_right_stub)
            vscRight?.let {
                it.setVisible()
                if (!::hudRightBinding.isInitialized) {
                    hudRightBinding =
                        DataBindingUtil.bind(player.findViewById(R.id.hud_right_overlay)) ?: return
                }
                if (!player.isBenchmark && player.enableCloneMode && !player.settings.contains("enable_clone_mode")) {
                    UiTools.snackerConfirm(player, player.getString(R.string.video_save_clone_mode)) { player.settings.putSingle("enable_clone_mode", true) }
                }
            }

            val vsc = player.findViewById<ViewStub>(R.id.player_hud_stub)
            if (vsc != null) {
                seekButtons = player.settings.getBoolean(ENABLE_SEEK_BUTTONS, false)
                vsc.setVisible()
                if (!::hudBinding.isInitialized) {
                    hudBinding =
                        DataBindingUtil.bind(player.findViewById(R.id.progress_overlay)) ?: return
                }
                hudBinding.player = player
                hudBinding.progress = service.playlistManager.player.progress
                abRepeatAddMarker = hudBinding.abRepeatContainer.findViewById(R.id.ab_repeat_add_marker)
                service.playlistManager.abRepeat.observe(player) { abvalues ->
                    hudBinding.abRepeatA = if (abvalues.start == -1L) -1F else abvalues.start / service.playlistManager.player.getLength().toFloat()
                    hudBinding.abRepeatB = if (abvalues.stop == -1L) -1F else abvalues.stop / service.playlistManager.player.getLength().toFloat()
                    hudBinding.abRepeatMarkerA.visibility = if (abvalues.start == -1L) View.GONE else View.VISIBLE
                    hudBinding.abRepeatMarkerB.visibility = if (abvalues.stop == -1L) View.GONE else View.VISIBLE
                    service.manageAbRepeatStep(hudBinding.abRepeatReset, hudBinding.abRepeatStop, hudBinding.abRepeatContainer, abRepeatAddMarker)
                    if (player.settings.getBoolean(VIDEO_TRANSITION_SHOW, true)) showOverlayTimeout(if (abvalues.start == -1L || abvalues.stop == -1L) VideoPlayerActivity.OVERLAY_INFINITE else Settings.videoHudDelay * 1000)
                }
                service.playlistManager.abRepeatOn.observe(player) {
                    abRepeatAddMarker.visibility = if (it) View.VISIBLE else View.GONE
                    hudBinding.abRepeatMarkerGuidelineContainer.visibility = if (it) View.VISIBLE else View.GONE
                    if (it) showOverlay(true)
                    if (it) {
                        hudBinding.playerOverlayLength.nextFocusUpId = R.id.ab_repeat_add_marker
                        hudBinding.playerOverlayTime.nextFocusUpId = R.id.ab_repeat_add_marker
                    }
                    if (it) showOverlayTimeout(VideoPlayerActivity.OVERLAY_INFINITE)

                    service.manageAbRepeatStep(hudBinding.abRepeatReset, hudBinding.abRepeatStop, hudBinding.abRepeatContainer, abRepeatAddMarker)
                }
                service.playlistManager.delayValue.observe(player) {
                    player.delayDelegate.delayChanged(it, service)
                }
                service.playlistManager.videoStatsOn.observe(player) {
                    if (it) showOverlay(true)
                    player.statsDelegate.container = hudBinding.statsContainer
                    player.statsDelegate.initPlotView(hudBinding)
                    if (it) player.statsDelegate.start() else player.statsDelegate.stop()
                }
                hudBinding.statsClose.setOnClickListener { service.playlistManager.videoStatsOn.postValue(false) }

                hudBinding.lifecycleOwner = player
                updateOrientationIcon()
                overlayBackground = player.findViewById(R.id.player_overlay_background)
                hudRightBinding.playerOverlayTitle.text = service.currentMediaWrapper?.title
                updateHudMargins()

                initSeekButton()


                resetHudLayout()
                updateOverlayPausePlay(true)
                updateSeekable(service.isSeekable)
                updatePausable(service.isPausable)
                player.updateNavStatus()
                setListeners(true)
                if (foldingFeature != null) manageHinge()
            } else if (::hudBinding.isInitialized) {
                hudBinding.progress = service.playlistManager.player.progress
                hudBinding.lifecycleOwner = player
            }
        }
    }

    fun updateSeekable(seekable: Boolean) {
        if (!::hudBinding.isInitialized) return
        hudBinding.playerOverlayRewind.isEnabled = seekable
        hudBinding.playerOverlayRewind.setImageResource(if (seekable)
            R.drawable.ic_player_rewind_10
        else
            R.drawable.ic_player_rewind_10_disabled)
        hudBinding.playerOverlayForward.isEnabled = seekable
        hudBinding.playerOverlayForward.setImageResource(if (seekable)
            R.drawable.ic_player_forward_10
        else
            R.drawable.ic_player_forward_10_disabled)
        if (!player.isLocked)
            hudBinding.playerOverlaySeekbar.isEnabled = seekable
    }

    fun setListeners(enabled: Boolean) {
        if (::hudBinding.isInitialized) {
            hudBinding.playerOverlaySeekbar.setOnSeekBarChangeListener(if (enabled) player.seekListener else null)
            hudBinding.abRepeatReset.setOnClickListener(player)
            hudBinding.abRepeatStop.setOnClickListener(player)
            abRepeatAddMarker.setOnClickListener(player)
            hudBinding.orientationToggle.setOnClickListener(if (enabled) player else null)
            hudBinding.orientationToggle.setOnLongClickListener(if (enabled) player else null)
            hudBinding.swipeToUnlock.setOnStartTouchingListener { showOverlayTimeout(VideoPlayerActivity.OVERLAY_INFINITE) }
            hudBinding.swipeToUnlock.setOnStopTouchingListener { showOverlayTimeout(Settings.videoHudDelay * 1000) }
            hudBinding.swipeToUnlock.setOnUnlockListener { player.toggleLock() }
        }
        if (::hudRightBinding.isInitialized){
            hudRightBinding.playerOverlayNavmenu.setOnClickListener(if (enabled) player else null)
            hudRightBinding.playbackSpeedQuickAction.setOnLongClickListener {
                player.service?.setRate(1F, true)
                showControls(true)
                true
            }
            hudRightBinding.sleepQuickAction.setOnLongClickListener {
                player.service?.setSleepTimer(null)
                showControls(true)
                true
            }
            hudRightBinding.audioDelayQuickAction.setOnLongClickListener {
                player.service?.setAudioDelay(0L)
                showControls(true)
                true
            }
            hudRightBinding.spuDelayQuickAction.setOnLongClickListener {
                player.service?.setSpuDelay(0L)
                showControls(true)
                true
            }
            hudRightBinding.quickActionsContainer.setOnTouchListener { v, event ->
                showOverlay()
                false
            }
        }
    }

    fun updatePausable(pausable: Boolean) {
        if (!::hudBinding.isInitialized) return
        hudBinding.playerOverlayPlay.isEnabled = pausable
        if (!pausable)
            hudBinding.playerOverlayPlay.setImageResource(R.drawable.ic_play_player_disabled)
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    fun resetHudLayout() {
        if (!::hudBinding.isInitialized) return
        if (!AndroidDevices.isChromeBook && player.isVideo) {
            hudBinding.orientationToggle.setVisible()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSeekButton() {
        if (!::hudBinding.isInitialized) return
        hudBinding.playerOverlayRewind.setOnClickListener(player)
        hudBinding.playerOverlayForward.setOnClickListener(player)
        hudBinding.playerOverlayRewind.setOnLongClickListener(player)
        hudBinding.playerOverlayForward.setOnLongClickListener(player)
        hudBinding.playerOverlayRewind.setOnKeyListener(OnRepeatListenerKey(player))
        hudBinding.playerOverlayForward.setOnKeyListener(OnRepeatListenerKey(player))
    }

    fun updateOrientationIcon() {
        if (::hudBinding.isInitialized) {
            val drawable = if (!player.orientationMode.locked) {
                R.drawable.ic_player_rotate
            } else if (player.orientationMode.orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE || player.orientationMode.orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE || player.orientationMode.orientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                R.drawable.ic_player_lock_landscape
            } else {
                R.drawable.ic_player_lock_portrait
            }
            hudBinding.orientationToggle.setImageDrawable(ContextCompat.getDrawable(player, drawable))
        }
    }

    fun updateHudMargins() {
        //here, we override the default Android overscan
        val overscanHorizontal = 8.dp
        if (::hudBinding.isInitialized) {
            val largeMargin = player.resources.getDimension(R.dimen.large_margins_center)
            val smallMargin = player.resources.getDimension(R.dimen.small_margins_sides)

            applyMargin(hudBinding.playerOverlayTracks, smallMargin.toInt(), false)
            applyMargin(hudBinding.playerOverlayAdvFunction, smallMargin.toInt(), true)

            hudBinding.playerOverlaySeekbar.setPadding(overscanHorizontal, 0, overscanHorizontal, 0)
            hudBinding.bookmarkMarkerContainer.setPadding(overscanHorizontal, 0, overscanHorizontal, 0)

            if (player.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                applyMargin(hudBinding.playerOverlaySeekbar, 0, true)
                applyMargin(hudBinding.playerOverlaySeekbar, 0, false)
                applyMargin(hudBinding.bookmarkMarkerContainer, 0, true)
                applyMargin(hudBinding.bookmarkMarkerContainer, 0, false)

                applyMargin(hudBinding.playlistPrevious, 0, true)
                applyMargin(hudBinding.playerOverlayRewindContainer, 0, true)
                applyMargin(hudBinding.playlistNext, 0, false)
                applyMargin(hudBinding.playerOverlayForwardContainer, 0, false)
                applyMargin(hudBinding.orientationToggle, 0, false)
                applyMargin(hudBinding.playerResize, 0, true)
            } else {
                applyMargin(hudBinding.playerOverlaySeekbar, 20.dp, true)
                applyMargin(hudBinding.playerOverlaySeekbar, 20.dp, false)
                applyMargin(hudBinding.bookmarkMarkerContainer, 20.dp, true)
                applyMargin(hudBinding.bookmarkMarkerContainer, 20.dp, false)

                applyMargin(hudBinding.playlistPrevious, largeMargin.toInt(), true)
                applyMargin(hudBinding.playerOverlayRewindContainer, largeMargin.toInt(), true)
                applyMargin(hudBinding.playlistNext, largeMargin.toInt(), false)
                applyMargin(hudBinding.playerOverlayForwardContainer, largeMargin.toInt(), false)
                applyMargin(hudBinding.orientationToggle, smallMargin.toInt(), false)
                applyMargin(hudBinding.playerResize, smallMargin.toInt(), true)
            }
        }
    }

    private fun applyMargin(view: View, margin: Int, isEnd: Boolean) = (view.layoutParams as ViewGroup.MarginLayoutParams).apply {
        if (isEnd) marginEnd = margin else marginStart = margin
        view.layoutParams = this
    }

    fun showControls(show: Boolean) {
        if (show && player.isInPictureInPictureMode) return
        if (::hudBinding.isInitialized) {
            hudBinding.playerOverlayPlay.visibility = if (show) View.VISIBLE else View.INVISIBLE
            if (seekButtons) {
                hudBinding.playerOverlayRewind.visibility = if (show) View.VISIBLE else View.INVISIBLE
                hudBinding.playerOverlayRewindText.text = "${Settings.videoJumpDelay}"
                hudBinding.playerOverlayRewindText.visibility = if (show) View.VISIBLE else View.INVISIBLE
                hudBinding.playerOverlayForward.visibility = if (show) View.VISIBLE else View.INVISIBLE
                hudBinding.playerOverlayForwardText.text = "${Settings.videoJumpDelay}"
                hudBinding.playerOverlayForwardText.visibility = if (show) View.VISIBLE else View.INVISIBLE
            }
            hudBinding.playerOverlayTracks.visibility = if (show) View.VISIBLE else View.INVISIBLE
            hudBinding.playerOverlayAdvFunction.visibility = if (show) View.VISIBLE else View.INVISIBLE
            hudBinding.playerResize.visibility = if (show && player.isVideo) View.VISIBLE else View.INVISIBLE
            if (hasPlaylist) {
//                hudBinding.playlistPrevious.visibility = if (show) View.VISIBLE else View.INVISIBLE
//                hudBinding.playlistNext.visibility = if (show) View.VISIBLE else View.INVISIBLE
            }
            hudBinding.orientationToggle.visibility = if (AndroidDevices.isChromeBook) View.INVISIBLE else if (show && player.isVideo) View.VISIBLE else View.INVISIBLE
        }
        if (::hudRightBinding.isInitialized) {
            hudRightBinding.playlistToggle.visibility = if (show && player.service?.hasPlaylist() == true) View.VISIBLE else View.GONE
            hudRightBinding.sleepQuickAction.visibility = if (show && PlaybackService.playerSleepTime.value != null) View.VISIBLE else View.GONE
            hudRightBinding.playbackSpeedQuickAction.visibility = if (show && player.service?.rate != 1.0F) View.VISIBLE else View.GONE
            hudRightBinding.spuDelayQuickAction.visibility = if (show && player.service?.spuDelay != 0L) View.VISIBLE else View.GONE
            hudRightBinding.audioDelayQuickAction.visibility = if (show && player.service?.audioDelay != 0L) View.VISIBLE else View.GONE

            hudRightBinding.playbackSpeedQuickAction.text = player.service?.rate?.formatRateString()
            val format =  DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
            PlaybackService.playerSleepTime.value?.let {
                hudRightBinding.sleepQuickAction.text = format.format(it.time)
            }
            hudRightBinding.spuDelayQuickAction.text = "${(player.service?.spuDelay ?: 0L) / 1000L} ms"
            hudRightBinding.audioDelayQuickAction.text = "${(player.service?.audioDelay ?: 0L) / 1000L} ms"

        }
    }

    /**
     * hider overlay
     */
    open fun hideOverlay(fromUser: Boolean) {
        if (player.isShowing) {
            if (isBookmarkShown()) hideBookmarks()
            player.handler.removeMessages(VideoPlayerActivity.FADE_OUT)
            if (!player.displayManager.isPrimary) {
                overlayBackground?.startAnimation(AnimationUtils.loadAnimation(player, android.R.anim.fade_out))
                overlayBackground.setInvisible()
            }

            exitAnimate(arrayOf(hudBinding.progressOverlay, hudBackground),100.dp.toFloat())
            exitAnimate(arrayOf(hudRightBinding.hudRightOverlay, hudRightBackground),-100.dp.toFloat())
            hingeArrowLeft.animate().alpha(0F)
            hingeArrowRight.animate().alpha(0F)

            showControls(false)
            player.isShowing = false
            dimStatusBar(true)
            playlistSearchText.editText?.setText("")
            player.onOverlayHidden()
        } else if (!fromUser) {
            /*
             * Try to hide the Nav Bar again.
             * It seems that you can't hide the Nav Bar if you previously
             * showed it in the last 1-2 seconds.
             */
            dimStatusBar(true)
        }
    }

    fun focusPlayPause() {
        if (::hudBinding.isInitialized) hudBinding.playerOverlayPlay.requestFocus()
    }

    fun toggleOverlay() {
        if (!player.isShowing) showOverlay()
        else hideOverlay(true)
    }

    /**
     * Lock player
     */
    fun lockScreen() {
        orientationLockedBeforeLock = player.orientationMode.locked
        if (!player.orientationMode.locked) player.toggleOrientation()
        if (isHudBindingInitialized()) {
            hudBinding.playerOverlayTime.isEnabled = false
            hudBinding.playerOverlaySeekbar.isEnabled = false
            hudBinding.playerOverlayLength.isEnabled = false
            hudBinding.playlistNext.isEnabled = false
            hudBinding.playlistPrevious.isEnabled = false
            hudBinding.buttonsLayout.isVisible = false
            hudBinding.swipeToUnlock.setVisible()
        }
        player.lockBackButton = true
        player.isLocked = true
    }

    /**
     * Remove player lock
     */
    fun unlockScreen() {
        player.orientationMode.locked = orientationLockedBeforeLock
        player.requestedOrientation = player.getScreenOrientation(player.orientationMode)
        if (isHudBindingInitialized()) {
            hudBinding.playerOverlayTime.isEnabled = true
            hudBinding.playerOverlaySeekbar.isEnabled = player.service?.isSeekable != false
            hudBinding.playerOverlayLength.isEnabled = true
            hudBinding.playlistNext.isEnabled = true
            hudBinding.playlistPrevious.isEnabled = true
            hudBinding.buttonsLayout.isVisible = true
            hudBinding.swipeToUnlock.isVisible = false
        }
        updateOrientationIcon()
        player.isShowing = false
        player.isLocked = false
        showOverlay()
        player.lockBackButton = false
    }

    private fun pickSubtitles() {
        // TODO:
    }

    private fun downloadSubtitles() = player.service?.currentMediaWrapper?.let {
        MediaUtils.getSubs(player, it)
    }

    fun showBookmarks() {
        player.service?.let {
            if (!this::bookmarkListDelegate.isInitialized) {
                bookmarkListDelegate = createBookmarkListDelegate(it)
                bookmarkListDelegate.markerContainer = hudBinding.bookmarkMarkerContainer
                bookmarkListDelegate.visibilityListener = {
                    if (bookmarkListDelegate.visible) showOverlayTimeout(VideoPlayerActivity.OVERLAY_INFINITE)
                    else showOverlayTimeout(Settings.videoHudDelay * 1000)
                }
            }
            bookmarkListDelegate.show()
            bookmarkListDelegate.setProgressHeight((player.getScreenHeight() - hudBinding.constraintLayout2.height + 12.dp).toFloat())
        }
    }

    protected open fun createBookmarkListDelegate(service: PlaybackService): BookmarkListDelegate {
        return BookmarkListDelegateImpl(player, service, player.bookmarkModel)
    }

    fun isBookmarkShown() = ::bookmarkListDelegate.isInitialized && bookmarkListDelegate.visible
    fun hideBookmarks() {
        bookmarkListDelegate.hide()
    }

    fun getOverlayBrightness() = if (::playerOverlayBrightness.isInitialized) playerOverlayBrightness else null

    fun getOverlayVolume() = if (::playerOverlayVolume.isInitialized) playerOverlayVolume else null
}