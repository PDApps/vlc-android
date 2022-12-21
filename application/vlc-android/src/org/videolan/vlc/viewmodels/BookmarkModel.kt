/*
 * ************************************************************************
 *  BookmarkModel.kt
 * *************************************************************************
 * Copyright © 2021 VLC authors and VideoLAN
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

package org.videolan.vlc.viewmodels

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.medialibrary.interfaces.media.Bookmark
import org.videolan.tools.livedata.LiveDataset
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.gui.video.VideoPlayerActivity

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
open class BookmarkModel : ViewModel(), PlaybackService.Callback {

    val dataset = LiveDataset<Bookmark>()

    open fun refresh(fileHash: ByteArray) {
    }

    companion object {
        fun get(activity: FragmentActivity) =
            ViewModelProvider(activity).get(BookmarkModel::class.java)
    }

    override fun update() {
    }

    override fun onMediaEvent(event: IMedia.Event) {
    }

    override fun onMediaPlayerEvent(event: MediaPlayer.Event) {
        if (event.type == MediaPlayer.Event.Opening) refresh(byteArrayOf())
    }

    open fun delete(bookmark: Bookmark) {
    }

    open fun addBookmark(context: VideoPlayerActivity) {

    }
}
