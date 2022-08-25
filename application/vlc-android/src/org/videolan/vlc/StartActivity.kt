/*
 * *************************************************************************
 *  StartActivity.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.resources.*
import org.videolan.resources.util.launchForeground
import org.videolan.tools.*
import org.videolan.vlc.gui.helpers.hf.StoragePermissionsDelegate.Companion.getStoragePermission
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.Permissions

private const val SEND_CRASH_RESULT = 0
private const val TAG = "VLC/StartActivity"
@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class StartActivity : FragmentActivity() {

    private val idFromShortcut: Int
        get() {
            if (!AndroidUtil.isNougatMR1OrLater) return 0
            val intent = intent
            val action = intent?.action
            if (!action.isNullOrEmpty()) {
                return when (action) {
                    "vlc.shortcut.video" -> R.id.nav_video
                    "vlc.shortcut.audio" -> R.id.nav_audio
                    "vlc.shortcut.browser" -> R.id.nav_directories
                    "vlc.shortcut.network" -> R.id.nav_network
                    "vlc.shortcut.playlists" -> R.id.nav_playlists
                    "vlc.shortcut.resume" -> R.id.ml_menu_last_playlist
                    else -> 0
                }
            }
            return 0
        }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.getContextWithLocale(AppContextProvider.locale))
    }

    override fun getApplicationContext(): Context {
        return super.getApplicationContext().getContextWithLocale(AppContextProvider.locale)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resume()
    }

    private fun resume() {
        val intent = intent
        val action = intent?.action

        if (Intent.ACTION_VIEW == action || ACTION_VIEW_ARC == action) {
            startPlaybackFromApp(intent)
            return
        } else if (Intent.ACTION_SEND == action) {
            val cd = intent.clipData
            val item = if (cd != null && cd.itemCount > 0) cd.getItemAt(0) else null
            if (item != null) {
                var uri: Uri? = FileUtils.getUri(item.uri)
                if (uri == null && item.text != null) uri = item.text.toString().toUri()
                if (uri != null) {
                    MediaUtils.openMediaNoUi(uri)
                    finish()
                    return
                }
            }
        }

        // Start application
        /* Get the current version from package */
        val settings = Settings.getInstance(this)
        val currentVersionNumber = BuildConfig.VLC_VERSION_CODE
        val savedVersionNumber = settings.getInt(PREF_FIRST_RUN, -1)
        /* Check if it's the first run */
        val firstRun = savedVersionNumber == -1
        val upgrade = firstRun || savedVersionNumber != currentVersionNumber
        if (upgrade && !firstRun) settings.putSingle(PREF_FIRST_RUN, currentVersionNumber)
        val removeOldDevices = savedVersionNumber in 3028201..3028399
        // Route search query
        if (Intent.ACTION_SEARCH == action || ACTION_SEARCH_GMS == action) {
            intent.setClassName(applicationContext, MOBILE_SEARCH_ACTIVITY)
            startActivity(intent)
            finish()
            return
        } else if (MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH == action) {
            val serviceInent = Intent(ACTION_PLAY_FROM_SEARCH, null, this, PlaybackService::class.java)
                    .putExtra(EXTRA_SEARCH_BUNDLE, intent.extras)
            launchForeground(serviceInent)
        } else {
            if (action != null && action.startsWith("vlc.mediashortcut:")) {
            } else {
                val target = idFromShortcut
                if (target == R.id.ml_menu_last_playlist)
                    PlaybackService.loadLastAudio(this)
                else
                    startApplication(firstRun, upgrade, target, removeOldDevices)
            }
        }
        FileUtils.copyLua(applicationContext, upgrade)
        FileUtils.copyHrtfs(applicationContext, upgrade)
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SEND_CRASH_RESULT) {
            resume()
        }
    }

    private fun startApplication(firstRun: Boolean, upgrade: Boolean, target: Int, removeDevices:Boolean = false) {
        val settings = Settings.getInstance(this@StartActivity)
        // Start Medialibrary from background to workaround Dispatchers.Main causing ANR
        // cf https://github.com/Kotlin/kotlinx.coroutines/issues/878
        if (!firstRun) {
            Thread {
                AppScope.launch {
                    // workaround for a Android 9 bug
                    // https://issuetracker.google.com/issues/113122354
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P && !awaitAppIsForegroung()) {
                        return@launch
                    }
                }
            }.start()
            val mainIntent = Intent(Intent.ACTION_VIEW)
                    .setClassName(applicationContext, MOBILE_MAIN_ACTIVITY)
                    .putExtra(EXTRA_FIRST_RUN, firstRun)
                    .putExtra(EXTRA_UPGRADE, upgrade)
            if (target != 0) mainIntent.putExtra(EXTRA_TARGET, target)
            startActivity(mainIntent)
        }
    }

    private fun startPlaybackFromApp(intent: Intent) = lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
        // workaround for a Android 9 bug
        // https://issuetracker.google.com/issues/113122354
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P && !awaitAppIsForegroung()) {
            finish()
            return@launch
        }
        if (Permissions.canReadStorage(applicationContext) || getStoragePermission()) when {
            intent.type?.startsWith("video") == true -> try {
                val app = AppContextProvider.appContext
                val playerClass = if (app is ClassProvider) {
                    app.playerClass
                } else {
                    VideoPlayerActivity::class.java
                }
                startActivity(intent.setClass(this@StartActivity, playerClass).apply { putExtra(VideoPlayerActivity.FROM_EXTERNAL, true) })
            } catch (ex: SecurityException) {
                intent.data?.let { MediaUtils.openMediaNoUi(it) }
            }
            else -> withContext(Dispatchers.IO) { FileUtils.getUri(intent.data)}?.let { MediaUtils.openMediaNoUi(it) }
        }
        finish()
    }

    interface ClassProvider {
        val playerClass: Class<*>
    }
}
