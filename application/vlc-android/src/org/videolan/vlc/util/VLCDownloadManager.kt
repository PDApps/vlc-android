package org.videolan.vlc.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.Extensions
import org.videolan.resources.AppContextProvider
import org.videolan.tools.isStarted
import org.videolan.vlc.R
import org.videolan.vlc.gui.dialogs.SubtitleItem
import org.videolan.vlc.gui.helpers.hf.getExtWritePermission


object VLCDownloadManager: BroadcastReceiver(), LifecycleObserver {
    private val downloadManager = AppContextProvider.appContext.getSystemService<DownloadManager>()!!
    private var dlDeferred : CompletableDeferred<SubDlResult>? = null
    private lateinit var defaultSubsDirectory : String

    override fun onReceive(context: Context, intent: Intent?) {
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun register() {
        AppContextProvider.appContext.applicationContext.registerReceiver(this, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun unRegister() {
        AppContextProvider.appContext.applicationContext.unregisterReceiver(this)
    }

    suspend fun download(context: FragmentActivity, subtitleItem: SubtitleItem) {
        val request = DownloadManager.Request(subtitleItem.zipDownloadLink.toUri())
        request.setDescription(subtitleItem.movieReleaseName)
        request.setTitle(context.resources.getString(R.string.download_subtitle_title))
        request.setDestinationInExternalFilesDir(context, getDownloadPath(subtitleItem), "")
        val id = downloadManager.enqueue(request)
        val deferred = CompletableDeferred<SubDlResult>().also { dlDeferred = it }
        when (val result = deferred.await()) {
            is SubDlFailure -> downloadFailed(result.id, context)
            is SubDlSuccess -> downloadSuccessful(result.id, result.subtitleItem, result.localUri, context)
        }
    }

    private suspend fun downloadSuccessful(id:Long, subtitleItem: SubtitleItem, localUri: String, context: FragmentActivity) {
        val extractDirectory = getFinalDirectory(context, subtitleItem) ?: return
        val downloadedPaths = FileUtils.unpackZip(localUri, extractDirectory)
        subtitleItem.run {
            downloadedPaths.forEach {
                Toast.makeText(context, R.string.subtitles_download_failed, Toast.LENGTH_SHORT)
                    .show()
            }
            withContext(Dispatchers.IO) { FileUtils.deleteFile(localUri) }
        }
    }

    private suspend fun getFinalDirectory(context: FragmentActivity, subtitleItem: SubtitleItem) : String? {
        if (!this::defaultSubsDirectory.isInitialized) defaultSubsDirectory = "${context.applicationContext.getExternalFilesDir(null)!!.absolutePath}/subtitles"
        if (subtitleItem.mediaUri.scheme != "file") return defaultSubsDirectory
        val folder = subtitleItem.mediaUri.path.getParentFolder() ?: return context.getExternalFilesDir("subs")?.absolutePath
        val canWrite = context.isStarted() && context.getExtWritePermission(folder.toUri())
        return if (canWrite) folder
        else (context.applicationContext.getExternalFilesDir(null))?.absolutePath ?: defaultSubsDirectory
    }

    private fun downloadFailed(id: Long, context: Context) {
        Toast.makeText(context, R.string.subtitles_download_failed, Toast.LENGTH_SHORT).show()
    }

    private fun getDownloadPath(subtitleItem: SubtitleItem) = "VLC/${subtitleItem.movieReleaseName}_${subtitleItem.idSubtitle}.zip"

    private fun getDownloadState(downloadId: Long): Pair<Int, String> {
        val query = DownloadManager.Query()
        query.setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        cursor.moveToFirst()
        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)

        val status = if (statusIndex != -1)
            cursor.getInt(statusIndex)
        else DownloadManager.STATUS_FAILED

        val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
        val localUri = if (localUriIndex != -1)
            cursor.getString(localUriIndex)
        else ""

        return Pair(status, if (localUri != null) localUri.toUri().path!! else "")
    }
}

private sealed class SubDlResult
private class SubDlSuccess(val id:Long, val subtitleItem: SubtitleItem, val localUri: String) : SubDlResult()
private class SubDlFailure(val id:Long) : SubDlResult()
