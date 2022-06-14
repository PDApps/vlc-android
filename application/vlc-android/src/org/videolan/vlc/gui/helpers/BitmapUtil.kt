/*****************************************************************************
 * BitmapUtil.java
 *
 * Copyright © 2011-2014 VLC authors and VideoLAN
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
 */

package org.videolan.vlc.gui.helpers

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.net.Uri
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.AppContextProvider
import org.videolan.tools.BitmapCache
import org.videolan.vlc.R

object BitmapUtil {
    const val TAG = "VLC/UiTools/BitmapUtil"


    fun getPictureFromCache(media: MediaWrapper): Bitmap? {
        // mPicture is not null only if passed through
        // the ctor which is deprecated by now.
        val b = media.picture
        return b ?: BitmapCache.getBitmapFromMemCache(media.location)
    }

    fun getPicture(media: MediaWrapper): Bitmap? {
        return getPictureFromCache(media)
    }

    fun centerCrop(srcBmp: Bitmap, width: Int, height: Int): Bitmap {
        val widthDiff = srcBmp.width - width
        val heightDiff = srcBmp.height - height
        if (widthDiff <= 0 && heightDiff <= 0) return srcBmp
        return try {
            Bitmap.createBitmap(
                    srcBmp,
                    widthDiff / 2,
                    heightDiff / 2,
                    width,
                    height
            )
        } catch (ignored: Exception) {
            srcBmp
        }

    }

    fun getBitmapFromVectorDrawable(context: Context, @DrawableRes drawableId: Int, width: Int = -1, height: Int = -1): Bitmap? {
        var drawable: Drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            drawable = DrawableCompat.wrap(drawable).mutate()
        }
        return when (drawable) {
            is BitmapDrawable -> drawable.bitmap
            is VectorDrawableCompat, is VectorDrawable -> {
                val bitmap = if (width > 0 && height > 0)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                else
                    Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
            else -> BitmapFactory.decodeResource(context.resources, drawableId)
        }
    }

    fun vectorToBitmap(context: Context, @DrawableRes resVector: Int, width:Int? = null, height:Int? = null): Bitmap? {
        val drawable = AppCompatResources.getDrawable(context, resVector)
        val b = Bitmap.createBitmap(width ?: drawable!!.intrinsicWidth, height ?: drawable!!.intrinsicHeight,
                Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        drawable!!.setBounds(0, 0, c.width, c.height)
        drawable!!.draw(c)
        return b
    }
}

fun Bitmap?.centerCrop(dstWidth: Int, dstHeight: Int):Bitmap? {
    if (this == null) return null
    return BitmapUtil.centerCrop(this, dstWidth, dstHeight)
}


fun Context.getBitmapFromDrawable(@DrawableRes drawableId: Int, width: Int = -1, height: Int = -1): Bitmap? {
    var drawable: Drawable = try {
        ContextCompat.getDrawable(this, drawableId) ?: return null
    } catch (e: Resources.NotFoundException) {
        VectorDrawableCompat.create(this.resources, drawableId, this.theme)!!
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
        drawable = DrawableCompat.wrap(drawable).mutate()
    }
    return when {
        drawable is BitmapDrawable -> drawable.bitmap
        drawable is VectorDrawableCompat || (AndroidUtil.isLolliPopOrLater && drawable is VectorDrawable) -> {
            val bitmap = if (width > 0 && height > 0)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            else
                Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
        else -> BitmapFactory.decodeResource(this.resources, drawableId)
    }
}