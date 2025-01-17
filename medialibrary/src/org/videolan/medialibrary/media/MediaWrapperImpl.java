/*
 *****************************************************************************
 * MediaWrapperImpl.java
 *****************************************************************************
 * Copyright © 2011-2019 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.medialibrary.media;


import android.net.Uri;
import android.os.Parcel;

import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.medialibrary.interfaces.Medialibrary;
import org.videolan.medialibrary.interfaces.media.BookmarkBase;
import org.videolan.medialibrary.interfaces.media.MediaWrapper;

@SuppressWarnings("JniMissingFunction")
public class MediaWrapperImpl extends MediaWrapper {
    public final static String TAG = "VLC/MediaWrapperImpl";

    public MediaWrapperImpl(Uri uri) { super(uri); }
    public MediaWrapperImpl(IMedia media) { super(media); }
    public MediaWrapperImpl(Parcel in) { super(in); }

    public void rename(String name) {
        final Medialibrary ml = Medialibrary.getInstance();
        if (mId != 0 && ml.isInitiated()) nativeSetMediaTitle(ml, mId, name);
    }

    public String getSettings() {
        return mSettings;
    }

    public String getNowPlaying() {
        return mNowPlaying;
    }

    public long getLastModified() {
        return mLastModified;
    }

    public void setLastModified(long mLastModified) {
        this.mLastModified = mLastModified;
    }

    public long getSeen() {
        return mSeen;
    }

    public void setSeen(long seen) {
        mSeen = seen;
    }

    public void addFlags(int flags) {
        mFlags |= flags;
    }

    public void setFlags(int flags) {
        mFlags = flags;
    }

    public int getFlags() {
        return mFlags;
    }

    public boolean hasFlag(int flag) {
        return (mFlags & flag) != 0;
    }

    public void removeFlags(int flags) {
        mFlags &= ~flags;
    }

    public long getMetaLong(int metaDataType) {
        Medialibrary ml = Medialibrary.getInstance();
        return mId == 0 || !ml.isInitiated() ? 0L : nativeGetMediaLongMetadata(ml, mId, metaDataType);
    }

    public String getMetaString(int metaDataType) {
        Medialibrary ml = Medialibrary.getInstance();
        return mId == 0 || !ml.isInitiated() ? null : nativeGetMediaStringMetadata(ml, mId, metaDataType);
    }

    public BookmarkBase[] getBookmarks() {
        Medialibrary ml = Medialibrary.getInstance();
        return mId == 0 || !ml.isInitiated() ? null : nativeGetBookmarks(ml, mId);
    }

    public BookmarkBase addBookmark(long time) {
        Medialibrary ml = Medialibrary.getInstance();
        return mId == 0 || !ml.isInitiated() ? null : nativeAddBookmark(ml, mId, time);
    }

    public boolean removeBookmark(long time) {
        Medialibrary ml = Medialibrary.getInstance();
        return mId == 0 || !ml.isInitiated() ? null : nativeRemoveBookmark(ml, mId, time);
    }

    public boolean removeAllBookmarks() {
        Medialibrary ml = Medialibrary.getInstance();
        return mId == 0 || !ml.isInitiated() ? null : nativeRemoveAllBookmarks(ml, mId);
    }

    public boolean setLongMeta(int metaDataType, long metadataValue) {
        Medialibrary ml = Medialibrary.getInstance();
        if (mId != 0 && ml.isInitiated())
            nativeSetMediaLongMetadata(ml, mId, metaDataType, metadataValue);
        return mId != 0;
    }

    public boolean setStringMeta(int metaDataType, String metadataValue) {
        if (mId == 0L) return false;
        Medialibrary ml = Medialibrary.getInstance();
        if (mId != 0 && ml.isInitiated())
            nativeSetMediaStringMetadata(ml, mId, metaDataType, metadataValue);
        return true;
    }

    @Override
    public boolean setPlayCount(long playCount) {
        if (mId == 0L) return false;
        final Medialibrary ml = Medialibrary.getInstance();
        return nativeSetMediaPlayCount(ml, mId, playCount);
    }

    private native long nativeGetMediaLongMetadata(Medialibrary ml, long id, int metaDataType);
    private native String nativeGetMediaStringMetadata(Medialibrary ml, long id, int metaDataType);
    private native void nativeSetMediaStringMetadata(Medialibrary ml, long id, int metaDataType, String metadataValue);
    private native void nativeSetMediaLongMetadata(Medialibrary ml, long id, int metaDataType, long metadataValue);
    private native void nativeSetMediaTitle(Medialibrary ml, long id, String name);
    private native boolean nativeSetMediaPlayCount(Medialibrary ml, long id, long playCount);
    private native BookmarkBase[] nativeGetBookmarks(Medialibrary ml, long id);
    private native BookmarkBase nativeAddBookmark(Medialibrary ml, long id, long time);
    private native boolean nativeRemoveBookmark(Medialibrary ml, long id, long time);
    private native boolean nativeRemoveAllBookmarks(Medialibrary ml, long id);
}
