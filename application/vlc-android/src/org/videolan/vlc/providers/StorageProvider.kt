/*****************************************************************************
 * StorageProvider.kt
 *****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
 *
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
 *****************************************************************************/

package org.videolan.vlc.providers

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.medialibrary.media.Storage
import org.videolan.resources.AndroidDevices
import org.videolan.tools.livedata.LiveDataset
import org.videolan.vlc.R
import java.io.File
import java.util.*

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class StorageProvider(context: Context, dataset: LiveDataset<MediaLibraryItem>, url: String?, showHiddenFiles: Boolean): FileBrowserProvider(context, dataset, url, false, showHiddenFiles) {

    override suspend fun browseRootImpl() {
        var storage: Storage
        val storagesList = ArrayList<MediaLibraryItem>()
        dataset.value = storagesList
    }

    private val sb = StringBuilder()
    override fun getDescription(folderCount: Int, mediaFileCount: Int): String {
        val res = context.resources
        sb.clear()
        if (folderCount > 0) {
            sb.append(res.getQuantityString(R.plurals.subfolders_quantity, folderCount, folderCount))
        } else sb.append(res.getString(R.string.nosubfolder))
        return sb.toString()
    }

    override suspend fun findMedia(media: IMedia) = media.takeIf { it.isStorage() }?.let { Storage(it.uri) }

    override fun computeHeaders(value: List<MediaLibraryItem>) {}
}

private fun IMedia.isStorage() = type == IMedia.Type.Directory
