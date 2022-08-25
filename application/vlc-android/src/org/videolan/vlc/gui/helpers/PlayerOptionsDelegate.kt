package org.videolan.vlc.gui.helpers

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.leanback.widget.BrowseFrameLayout
import androidx.leanback.widget.BrowseFrameLayout.OnFocusSearchListener
import androidx.lifecycle.LifecycleObserver
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import org.videolan.resources.AndroidDevices
import org.videolan.resources.VLCOptions
import org.videolan.tools.AppScope
import org.videolan.tools.Settings
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.databinding.PlayerOptionItemBinding
import org.videolan.vlc.gui.DiffUtilAdapter
import org.videolan.vlc.gui.audio.EqualizerFragment
import org.videolan.vlc.gui.dialogs.*
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.media.PlayerController

private const val ACTION_AUDIO_DELAY = 2
private const val ACTION_SPU_DELAY = 3

private const val ID_PLAY_AS_AUDIO = 0L
private const val ID_SLEEP = 1L
private const val ID_JUMP_TO = 2L
private const val ID_PLAY_AS_VIDEO = 3L
private const val ID_BOOKMARK = 4L
private const val ID_CHAPTER_TITLE = 5L
private const val ID_PLAYBACK_SPEED = 6L
private const val ID_EQUALIZER = 7L
private const val ID_POPUP_VIDEO = 9L
private const val ID_REPEAT = 10L
private const val ID_SHUFFLE = 11L
private const val ID_PASSTHROUGH = 12L
private const val ID_ABREPEAT = 13L
private const val ID_LOCK_PLAYER = 14L
private const val ID_VIDEO_STATS = 15L
private const val ID_VIDEO_CONTROLS_SETTING = 19L
private const val ID_AUDIO_CONTROLS_SETTING = 20L
@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
@SuppressLint("ShowToast")
open class PlayerOptionsDelegate(val activity: FragmentActivity, val service: PlaybackService, private val showABReapeat:Boolean = true) : LifecycleObserver {

    private lateinit var bookmarkClickedListener: () -> Unit
    private lateinit var recyclerview: RecyclerView
    private lateinit var rootView: FrameLayout
    var flags: Long = 0L
    private val toast by lazy(LazyThreadSafetyMode.NONE) { Toast.makeText(activity, "", Toast.LENGTH_SHORT) }

    private val primary = activity is VideoPlayerActivity && activity.displayManager.isPrimary
    private val isChromecast = activity is VideoPlayerActivity && activity.displayManager.isOnRenderer
    open val video = activity is VideoPlayerActivity
    protected val res = activity.resources
    private val settings = Settings.getInstance(activity)
    private lateinit var abrBinding: PlayerOptionItemBinding
    private lateinit var ptBinding: PlayerOptionItemBinding
    private lateinit var repeatBinding: PlayerOptionItemBinding
    private lateinit var shuffleBinding: PlayerOptionItemBinding
    private lateinit var sleepBinding: PlayerOptionItemBinding

    open fun onCreateOptions(options: MutableList<PlayerOption>) {

    }

    fun setup() {
        if (!this::recyclerview.isInitialized || PlayerController.playbackState == PlaybackStateCompat.STATE_STOPPED) return
        val options = mutableListOf<PlayerOption>()
        if (video) options.add(PlayerOption(ID_LOCK_PLAYER, R.drawable.ic_lock_player, res.getString(R.string.lock)))
        options.add(PlayerOption(ID_SLEEP, R.drawable.ic_sleep, res.getString(R.string.sleep_title)))
        if (!isChromecast) options.add(PlayerOption(ID_PLAYBACK_SPEED, R.drawable.ic_speed, res.getString(R.string.playback_speed)))
        options.add(PlayerOption(ID_JUMP_TO, R.drawable.ic_jumpto, res.getString(R.string.jump_to_time)))
        options.add(PlayerOption(ID_EQUALIZER, R.drawable.ic_equalizer, res.getString(R.string.equalizer)))
        if (video) {
            if (primary && AndroidDevices.pipAllowed && !AndroidDevices.isDex(activity))
                options.add(PlayerOption(ID_POPUP_VIDEO, R.drawable.ic_popup_dim, res.getString(R.string.ctx_pip_title)))
            if (primary)
                options.add(PlayerOption(ID_REPEAT, R.drawable.ic_repeat, res.getString(R.string.repeat_title)))
            if (service.canShuffle()) options.add(PlayerOption(ID_SHUFFLE, R.drawable.ic_shuffle, res.getString(R.string.shuffle_title)))
            options.add(PlayerOption(ID_VIDEO_STATS, R.drawable.ic_video_stats, res.getString(R.string.video_information)))
        }
        val chaptersCount = service.getChapters(-1)?.size ?: 0
        if (chaptersCount > 1) options.add(PlayerOption(ID_CHAPTER_TITLE, R.drawable.ic_chapter, res.getString(R.string.go_to_chapter)))
        if (::bookmarkClickedListener.isInitialized) options.add(PlayerOption(ID_BOOKMARK, R.drawable.ic_bookmark, res.getString(R.string.bookmarks)))
        if (showABReapeat) options.add(PlayerOption(ID_ABREPEAT, R.drawable.ic_abrepeat, res.getString(R.string.ab_repeat)))
        if (service.playlistManager.player.canDoPassthrough() && settings.getString("aout", "0") == "0")
            options.add(PlayerOption(ID_PASSTHROUGH, R.drawable.ic_passthrough, res.getString(R.string.audio_digital_title)))
        if (!Settings.showTvUi) {
            if (video) {
            options.add(PlayerOption(ID_VIDEO_CONTROLS_SETTING, R.drawable.ic_video_controls, res.getString(R.string.controls_setting)))
            } else {
            options.add(PlayerOption(ID_AUDIO_CONTROLS_SETTING, R.drawable.ic_audio_controls, res.getString(R.string.controls_setting)))
            }
        }
        onCreateOptions(options)
        (recyclerview.adapter as OptionsAdapter).update(options)
    }

    fun show() {
        activity.findViewById<ViewStub>(R.id.player_options_stub)?.let {
            rootView = it.inflate() as FrameLayout
            recyclerview = rootView.findViewById(R.id.options_list)
            val browseFrameLayout =  rootView.findViewById<BrowseFrameLayout>(R.id.options_background)
            browseFrameLayout.onFocusSearchListener = OnFocusSearchListener { focused, direction ->
                if (recyclerview.hasFocus()) focused // keep focus on recyclerview! DO NOT return recyclerview, but focused, which is a child of the recyclerview
                else null // someone else will find the next focus
            }
            service.lifecycle.addObserver(this)
            activity.lifecycle.addObserver(this)
            if (recyclerview.layoutManager == null) recyclerview.layoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, false)
            recyclerview.adapter = OptionsAdapter()
            recyclerview.itemAnimator = null

            rootView.setOnClickListener { hide() }
        }
        setup()
        rootView.visibility = View.VISIBLE
        if (Settings.showTvUi) AppScope.launch {
            delay(100L)
            val position = (recyclerview.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            (recyclerview.layoutManager as LinearLayoutManager).findViewByPosition(position)?.requestFocus()
        }
    }

    fun hide() {
        rootView.visibility = View.GONE
    }

    fun setBookmarkClickedListener(listener:()->Unit) {
        this.bookmarkClickedListener = listener
    }

    open fun onClick(option: PlayerOption) {
        when (option.id) {
            ID_SLEEP -> {
                showFragment(ID_SLEEP)
            }
            ID_PLAY_AS_AUDIO -> (activity as VideoPlayerActivity).switchToAudioMode(true)
            ID_POPUP_VIDEO -> {
                (activity as VideoPlayerActivity).switchToPopup()
                hide()
            }
            ID_REPEAT -> setRepeatMode()
            ID_SHUFFLE -> {
                service.shuffle()
                setShuffle()
            }
            ID_PASSTHROUGH -> togglePassthrough()
            ID_ABREPEAT -> {
                hide()
                service.playlistManager.toggleABRepeat()
            }
            ID_LOCK_PLAYER -> {
                hide()
                (activity as VideoPlayerActivity).toggleLock()
            }
            ID_VIDEO_STATS -> {
                hide()
                service.playlistManager.toggleStats()
            }
            ID_BOOKMARK -> {
                hide()
                bookmarkClickedListener.invoke()
            }
            ID_VIDEO_CONTROLS_SETTING -> {
                hide()
                val videoControlsSettingsDialog = VideoControlsSettingsDialog()
                videoControlsSettingsDialog.show(activity.supportFragmentManager, "fragment_video_controls_settings")
            }
            ID_AUDIO_CONTROLS_SETTING -> {
                hide()
                val audioControlsSettingsDialog = AudioControlsSettingsDialog()
                audioControlsSettingsDialog.show(activity.supportFragmentManager, "fragment_audio_controls_settings")
            }
            else -> showFragment(option.id)
        }
    }

    private fun showFragment(id: Long) {
        val newFragment: DialogFragment
        val tag: String
        when (id) {
            ID_PLAYBACK_SPEED -> {
                newFragment = PlaybackSpeedDialog.newInstance()
                tag = "playback_speed"
            }
            ID_JUMP_TO -> {
                newFragment = JumpToTimeDialog.newInstance()
                tag = "time"
            }
            ID_SLEEP -> {
                newFragment = SleepTimerDialog.newInstance()
                tag = "time"
            }
            ID_CHAPTER_TITLE -> {
                newFragment = SelectChapterDialog.newInstance()
                tag = "select_chapter"
            }
            ID_EQUALIZER -> {
                newFragment = EqualizerFragment.newInstance()
                tag = "equalizer"
            }
            else -> return
        }
        if (newFragment is VLCBottomSheetDialogFragment && activity is VideoPlayerActivity)
            newFragment.onDismissListener = DialogInterface.OnDismissListener { activity.overlayDelegate.dimStatusBar(true) }
        newFragment.show(activity.supportFragmentManager, tag)
        hide()
    }

    private fun showValueControls(action: Int) {
        val controller = (activity as? VideoPlayerActivity)?.delayDelegate ?: return
        when (action) {
            ACTION_AUDIO_DELAY -> controller.showAudioDelaySetting()
            ACTION_SPU_DELAY -> controller.showSubsDelaySetting()
            else -> return
        }
        hide()
    }

    private fun setRepeatMode() {
        when (service.repeatType) {
            PlaybackStateCompat.REPEAT_MODE_NONE -> {
                repeatBinding.optionIcon.setImageResource(R.drawable.ic_repeat_one)
                service.repeatType = PlaybackStateCompat.REPEAT_MODE_ONE
            }
            PlaybackStateCompat.REPEAT_MODE_ONE -> if (service.hasPlaylist()) {
                repeatBinding.optionIcon.setImageResource(R.drawable.ic_repeat_all)
                service.repeatType = PlaybackStateCompat.REPEAT_MODE_ALL
            } else {
                repeatBinding.optionIcon.setImageResource(R.drawable.ic_repeat)
                service.repeatType = PlaybackStateCompat.REPEAT_MODE_NONE
            }
            PlaybackStateCompat.REPEAT_MODE_ALL -> {
                repeatBinding.optionIcon.setImageResource(R.drawable.ic_repeat)
                service.repeatType = PlaybackStateCompat.REPEAT_MODE_NONE
            }
        }
    }

    private fun setShuffle() {
        shuffleBinding.optionIcon.setImageResource(if (service.isShuffling) R.drawable.ic_shuffle_on_48dp else R.drawable.ic_shuffle)
    }

    private fun initShuffle(binding: PlayerOptionItemBinding) {
        shuffleBinding = binding
        AppScope.launch(Dispatchers.Main) {
            shuffleBinding.optionIcon.setImageResource(if (service.isShuffling) R.drawable.ic_shuffle_on_48dp else R.drawable.ic_shuffle)
        }
    }

    private fun initRepeat(binding: PlayerOptionItemBinding) {
        repeatBinding = binding
        AppScope.launch(Dispatchers.Main) {
            repeatBinding.optionIcon.setImageResource(when (service.repeatType) {
                PlaybackStateCompat.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                PlaybackStateCompat.REPEAT_MODE_ALL -> R.drawable.ic_repeat_all
                else -> R.drawable.ic_repeat
            })
        }
    }

    private fun togglePassthrough() {
        val enabled = !VLCOptions.isAudioDigitalOutputEnabled(settings)
        if (service.setAudioDigitalOutputEnabled(enabled)) {
            ptBinding.optionIcon.setImageResource(if (enabled) R.drawable.ic_passthrough_on
            else UiTools.getResourceFromAttribute(activity, R.attr.ic_passthrough))
            VLCOptions.setAudioDigitalOutputEnabled(settings, enabled)
            toast.setText(res.getString(if (enabled) R.string.audio_digital_output_enabled else R.string.audio_digital_output_disabled))
        } else
            toast.setText(R.string.audio_digital_failed)
        toast.show()
    }

    fun isShowing() = rootView.visibility == View.VISIBLE

    private inner class OptionsAdapter : DiffUtilAdapter<PlayerOption, OptionsAdapter.ViewHolder>() {

        private lateinit var layountInflater: LayoutInflater

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            if (!this::layountInflater.isInitialized) layountInflater = LayoutInflater.from(parent.context)
            return ViewHolder(PlayerOptionItemBinding.inflate(layountInflater, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val option = dataset[position]
            holder.binding.option = option
            when (option.id) {
                ID_ABREPEAT -> abrBinding = holder.binding
                ID_PASSTHROUGH -> ptBinding = holder.binding
                ID_REPEAT -> initRepeat(holder.binding)
                ID_SHUFFLE -> initShuffle(holder.binding)
                ID_SLEEP -> sleepBinding = holder.binding
            }
            holder.binding.optionIcon.setImageResource(option.icon)
        }

        inner class ViewHolder(val binding: PlayerOptionItemBinding) : RecyclerView.ViewHolder(binding.root) {

            init {
                itemView.setOnClickListener { onClick(dataset[layoutPosition]) }
            }
        }
    }
}

data class PlayerOption(val id: Long, val icon: Int, val title: String)
