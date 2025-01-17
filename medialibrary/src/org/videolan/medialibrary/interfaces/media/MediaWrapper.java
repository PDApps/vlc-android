/*
 *****************************************************************************
 * MediaWrapper.java
 *****************************************************************************
 * Copyright © 2019 VLC authors and VideoLAN
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

package org.videolan.medialibrary.interfaces.media;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.libvlc.util.Extensions;
import org.videolan.medialibrary.MLServiceLocator;
import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.media.MediaLibraryItem;

import java.util.Locale;

public abstract class MediaWrapper extends MediaLibraryItem implements Parcelable {
    public final static int TYPE_ALL = -1;
    public final static int TYPE_VIDEO = 0;
    public final static int TYPE_AUDIO = 1;
    public final static int TYPE_GROUP = 2;
    public final static int TYPE_DIR = 3;
    public final static int TYPE_SUBTITLE = 4;
    public final static int TYPE_PLAYLIST = 5;
    public final static int TYPE_STREAM = 6;

    public final static int MEDIA_VIDEO = 0x01;
    public final static int MEDIA_NO_HWACCEL = 0x02;
    public final static int MEDIA_PAUSED = 0x4;
    public final static int MEDIA_FORCE_AUDIO = 0x8;
    public final static int MEDIA_BENCHMARK = 0x10;
    public final static int MEDIA_FROM_START = 0x20;

    //MetaData flags
    public final static int META_RATING = 1;
    //Playback
    public final static int META_SPEED = 51;
    public final static int META_TITLE = 52;
    public final static int META_CHAPTER = 53;
    public final static int META_PROGRAM = 54;
    //video
    public final static int META_VIDEOTRACK = 100;
    public final static int META_ASPECT_RATIO = 101;
    public final static int META_ZOOM = 102;
    public final static int META_CROP = 103;
    public final static int META_DEINTERLACE = 104;
    public final static int META_VIDEOFILTER = 105;
    //Audio
    public final static int META_GAIN = 151;
    public final static int META_AUDIODELAY = 152;
    //Spu
    public final static int META_SUBTITLE_TRACK = 200;
    public final static int META_SUBTITLE_DELAY = 201;
    //Various
    public final static int META_APPLICATION_SPECIFIC = 250;
    public final static int META_METADATA_RETRIEVED = 251;

    // threshold lentgh between song and podcast ep, set to 15 minutes
    protected static final long PODCAST_THRESHOLD = 900000L;
    protected static final long PODCAST_ABSOLUTE = 3600000L;

    protected String mDisplayTitle;
    protected String mSettings;
    protected String mNowPlaying;
    protected boolean mThumbnailGenerated;
    private boolean mIsPresent = true;

    public byte[] fileHash = new byte[0];
    protected final Uri mUri;
    protected String mFilename;
    protected long mTime = -1;
    protected float mPosition = -1;
    protected long mDisplayTime = 0;
    /* -1 is a valid track (Disabled) */
    protected int mAudioTrack = -2;
    protected int mSpuTrack = -2;
    protected long mLength = 0;
    protected int mType;
    protected int mWidth = 0;
    protected int mHeight = 0;
    protected Bitmap mPicture;
    protected boolean mIsPictureParsed;
    protected int mFlags = 0;
    protected long mLastModified = 0L;
    protected IMedia.Slave[] mSlaves = null;
    public Float speed = -1f;

    protected long mSeen = 0L;

    public abstract void rename(String name);
    public abstract long getMetaLong(int metaDataType);
    public abstract String getMetaString(int metaDataType);
    public abstract boolean setLongMeta(int metaDataType, long metaDataValue);
    public abstract boolean setStringMeta(int metaDataType, String metaDataValue);
    public abstract boolean setPlayCount(long playCount);
    public abstract BookmarkBase[] getBookmarks();
    public abstract BookmarkBase addBookmark(long time);
    public abstract boolean removeBookmark(long time);
    public abstract boolean removeAllBookmarks();

    private String manageVLCMrl(String mrl) {
        if (!mrl.isEmpty() && mrl.charAt(0) == '/') {
            mrl = "file://" + mrl;
        } else if (mrl.toLowerCase().startsWith("vlc://")) {
            mrl = mrl.substring(6);
            if (Uri.parse(mrl).getScheme() == null) {
                mrl = "http://" + mrl;
            }
        }
        return mrl;
    }

    /**
     * Create a new MediaWrapper
     *
     * @param uri Should not be null.
     */
    public MediaWrapper(Uri uri) {
        super();
        if (uri == null) throw new NullPointerException("uri was null");

        uri = Uri.parse(manageVLCMrl(uri.toString()));
        mUri = uri;
        init(null);
    }

    /**
     * Create a new MediaWrapper
     *
     * @param media should be parsed and not NULL
     */
    public MediaWrapper(IMedia media) {
        super();
        if (media == null)
            throw new NullPointerException("media was null");

        mUri = media.getUri();
        init(media);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MediaLibraryItem) || ((MediaLibraryItem) obj).getItemType() != TYPE_MEDIA)
            return false;
        return equals((MediaWrapper) obj);
    }

    public boolean equals(MediaWrapper obj) {
        long otherId = obj.getId();
        if (otherId != 0L && getId() != 0L && otherId == getId()) return true;
        final Uri otherUri = obj.getUri();
        return !(mUri == null || otherUri == null) && (mUri == otherUri || mUri.equals(otherUri));
    }

    private void init(IMedia media) {
        mType = TYPE_ALL;

        if (media != null) {
            if (media.isParsed()) {
                mLength = media.getDuration();

                for (int i = 0; i < media.getTrackCount(); ++i) {
                    final IMedia.Track track = media.getTrack(i);
                    if (track == null)
                        continue;
                    if (track.type == Media.Track.Type.Video) {
                        final IMedia.VideoTrack videoTrack = (IMedia.VideoTrack) track;
                        mType = TYPE_VIDEO;
                        mWidth = videoTrack.width;
                        mHeight = videoTrack.height;
                    } else if (mType == TYPE_ALL && track.type == Media.Track.Type.Audio) {
                        mType = TYPE_AUDIO;
                    }
                }
            }
            updateMeta(media);
            if (mType == TYPE_ALL)
                switch (media.getType()) {
                    case Media.Type.Directory:
                        mType = TYPE_DIR;
                        break;
                    case Media.Type.Playlist:
                        mType = TYPE_PLAYLIST;
                        break;
                }
            mSlaves = media.getSlaves();
        }
        defineType();
    }

    private void defineType() {
        if (mType != TYPE_ALL)
            return;

        String fileExt = null, filename = mUri.getLastPathSegment();
        if (TextUtils.isEmpty(filename))
            filename = mTitle;
        if (TextUtils.isEmpty(filename))
            return;
        int index = filename.indexOf('?');
        if (index != -1)
            filename = filename.substring(0, index);

        index = filename.lastIndexOf(".");

        if (index != -1)
            fileExt = filename.substring(index).toLowerCase(Locale.ENGLISH);

        if (!TextUtils.isEmpty(fileExt)) {
            if (Extensions.VIDEO.contains(fileExt)) {
                mType = TYPE_VIDEO;
            } else if (Extensions.AUDIO.contains(fileExt)) {
                mType = TYPE_AUDIO;
            } else if (Extensions.SUBTITLES.contains(fileExt)) {
                mType = TYPE_SUBTITLE;
            } else if (Extensions.PLAYLIST.contains(fileExt)) {
                mType = TYPE_PLAYLIST;
            }
        }
    }

    private void init(long time, float position, long length, int type,
                      Bitmap picture, String title,
                      int width, int height, int audio, int spu, long lastModified,
                      long seen, boolean isPresent, IMedia.Slave[] slaves) {
        mFilename = null;
        mTime = time;
        mPosition = position;
        mDisplayTime = time;
        mAudioTrack = audio;
        mSpuTrack = spu;
        mLength = length;
        mType = type;
        mPicture = picture;
        mWidth = width;
        mHeight = height;

        mTitle = title != null ? title.trim() : null;
        mLastModified = lastModified;
        mSeen = seen;
        mSlaves = slaves;
        mIsPresent = isPresent;
    }

    public MediaWrapper(Uri uri, long time, float position, long length, int type,
                        Bitmap picture, String title,
                        int width, int height, int audio, int spu, long lastModified, long seen) {
        mUri = uri;
        init(time, position, length, type, picture, title,
                width, height, audio, spu, lastModified, seen, true, null);
    }

    @Override
    public MediaWrapper[] getTracks() {
        return new MediaWrapper[]{this};
    }

    @Override
    public int getTracksCount() {
        return 1;
    }

    @Override
    public int getItemType() {
        return TYPE_MEDIA;
    }

    @Override
    public long getId() {
        return mId;
    }

    public String getLocation() {
        return mUri.toString();
    }

    public Uri getUri() {
        return mUri;
    }

    private static String getMetaId(IMedia media, String defaultMeta, int id, boolean trim) {
        String meta = media.getMeta(id, true);
        return meta != null ? trim ? meta.trim() : meta : defaultMeta;
    }

    private void updateMeta(IMedia media) {
        mTitle = getMetaId(media, mTitle, Media.Meta.Title, true);
        mNowPlaying = getMetaId(media, mNowPlaying, Media.Meta.NowPlaying, false);
    }

    public void updateMeta(MediaPlayer mediaPlayer) {
        if ((!TextUtils.isEmpty(mTitle) && TextUtils.isEmpty(mDisplayTitle)) || (mDisplayTitle != null && !mDisplayTitle.equals(mTitle)))
            mDisplayTitle = mTitle;
        final IMedia media = mediaPlayer.getMedia();
        if (media == null)
            return;
        updateMeta(media);
        media.release();
    }

    public String getFileName() {
        if (mFilename == null) {
            if (mUri == null)
                mFilename = "";
            else
                mFilename = mUri.getLastPathSegment();
        }
        return mFilename;
    }

    public long getTime() {
        return mTime;
    }

    public void setTime(long time) {
        mTime = time;
    }

    public float getPosition() {
        return mPosition;
    }

    public void setPosition(float mPosition) {
        this.mPosition = mPosition;
    }

    public long getDisplayTime() {
        return mDisplayTime;
    }

    public void setDisplayTime(long time) {
        mDisplayTime = time;
    }

    public int getAudioTrack() {
        return mAudioTrack;
    }

    public void setAudioTrack(int track) {
        mAudioTrack = track;
    }

    public int getSpuTrack() {
        return mSpuTrack;
    }

    public void setSpuTrack(int track) {
        mSpuTrack = track;
    }

    public long getLength() {
        return mLength;
    }

    public void setLength(long length) {
        mLength = length;
    }

    public int getType() {
        return mType;
    }

    public boolean isPodcast() {
        return mType == TYPE_AUDIO && mLength > PODCAST_ABSOLUTE;
    }

    public void setType(int type) {
        mType = type;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    /**
     * Returns the raw picture object. Likely to be NULL in VLC for Android
     * due to lazy-loading.
     *
     * @return The raw picture or NULL
     */
    public Bitmap getPicture() {
        return mPicture;
    }

    /**
     * Sets the raw picture object.
     *
     * @param p Bitmap picture
     */
    public void setPicture(Bitmap p) {
        mPicture = p;
    }

    public boolean isPictureParsed() {
        return mIsPictureParsed;
    }

    public void setPictureParsed(boolean isParsed) {
        mIsPictureParsed = isParsed;
    }

    public void setDisplayTitle(String title) {
        mDisplayTitle = title;
    }

    @Override
    public void setTitle(String title) {
        mTitle = title;
    }

    @Override
    public String getTitle() {
        String displayTitle = mDisplayTitle;
        if (!TextUtils.isEmpty(displayTitle))
            return displayTitle;
        String title = mTitle;
        if (!TextUtils.isEmpty(title))
            return title;
        String fileName = getFileName();
        if (fileName == null)
            return "";
        int end = fileName.lastIndexOf(".");
        if (end <= 0)
            return fileName;
        return fileName.substring(0, end);
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

    public boolean isPresent() {
        return mIsPresent;
    }

    @Nullable
    public IMedia.Slave[] getSlaves() {
        return mSlaves;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public MediaWrapper(Parcel in) {
        super(in);
        mUri = in.readParcelable(Uri.class.getClassLoader());
        init(in.readLong(),
                in.readFloat(),
                in.readLong(),
                in.readInt(),
                (Bitmap) in.readParcelable(Bitmap.class.getClassLoader()),
                in.readString(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readInt(),
                in.readLong(),
                in.readLong(),
                in.readInt() == 1,
                in.createTypedArray(PSlave.CREATOR));
        int arraySize = in.readInt();
        fileHash = new byte[arraySize];
        in.readByteArray(fileHash);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(mUri, flags);
        dest.writeLong(getTime());
        dest.writeFloat(getPosition());
        dest.writeLong(getLength());
        dest.writeInt(getType());
        dest.writeParcelable(getPicture(), flags);
        dest.writeString(getTitle());
        dest.writeInt(getWidth());
        dest.writeInt(getHeight());
        dest.writeInt(getAudioTrack());
        dest.writeInt(getSpuTrack());
        dest.writeLong(getLastModified());
        dest.writeLong(getSeen());
        dest.writeInt(isPresent() ? 1 : 0);

        if (mSlaves != null) {
            PSlave[] pslaves = new PSlave[mSlaves.length];
            for (int i = 0; i < mSlaves.length; ++i) {
                pslaves[i] = new PSlave(mSlaves[i]);
            }
            dest.writeTypedArray(pslaves, flags);
        } else
            dest.writeTypedArray(null, flags);

        dest.writeInt(fileHash.length);
        dest.writeByteArray(fileHash);
    }

    public static final Parcelable.Creator<MediaWrapper> CREATOR = new Parcelable.Creator<MediaWrapper>() {
        @Override
        public MediaWrapper createFromParcel(Parcel in) {
            return MLServiceLocator.getAbstractMediaWrapper(in);
        }

        @Override
        public MediaWrapper[] newArray(int size) {
            return new MediaWrapper[size];
        }
    };

    protected static class PSlave extends IMedia.Slave implements Parcelable {

        PSlave(IMedia.Slave slave) {
            super(slave.type, slave.priority, slave.uri);
        }

        PSlave(Parcel in) {
            super(in.readInt(), in.readInt(), in.readString());
        }

        public static final Creator<PSlave> CREATOR = new Creator<PSlave>() {
            @Override
            public PSlave createFromParcel(Parcel in) {
                return new PSlave(in);
            }

            @Override
            public PSlave[] newArray(int size) {
                return new PSlave[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int flags) {
            parcel.writeInt(type);
            parcel.writeInt(priority);
            parcel.writeString(uri);
        }
    }
}
