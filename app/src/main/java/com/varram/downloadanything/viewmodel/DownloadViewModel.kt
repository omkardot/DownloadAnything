package com.varram.downloadanything.viewmodel

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val progress: Int,
        val bytesDownloaded: Long,
        val bytesTotal: Long,
        val indeterminate: Boolean
    ) : DownloadState()
    data class Success(val fileName: String) : DownloadState()
    data class Failed(val reason: String) : DownloadState()
}

class DownloaderViewModel : ViewModel() {

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    private var pollingJob: Job? = null

    fun startDownload(context: Context, url: String) {
        try {
            val fileName = url.substringAfterLast('/').substringBefore('?').ifEmpty { "downloaded_file" }

            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setDescription("Downloading file")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            _downloadState.value = DownloadState.Downloading(0, 0, 0, indeterminate = true)
            observeProgress(downloadManager, downloadId, fileName)
        } catch (e: Exception) {
            _downloadState.value = DownloadState.Failed(e.message ?: "Unknown error")
        }
    }

    private fun observeProgress(downloadManager: DownloadManager, downloadId: Long, fileName: String) {
        pollingJob?.cancel()

        pollingJob = viewModelScope.launch {
            progressFlow(downloadManager, downloadId, fileName).collect { state ->
                _downloadState.value = state
            }
        }
    }

    private fun progressFlow(downloadManager: DownloadManager, downloadId: Long, fallbackFileName: String) = flow {
        var isDownloading = true

        while (isDownloading) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor: Cursor? = downloadManager.query(query)

            if (cursor != null && cursor.moveToFirst()) {
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val bytesDownloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val bytesTotalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val titleIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)

                val status = if (statusIdx != -1) cursor.getInt(statusIdx) else -1
                val bytesDownloaded = if (bytesDownloadedIdx != -1) cursor.getLong(bytesDownloadedIdx) else 0L
                val bytesTotal = if (bytesTotalIdx != -1) cursor.getLong(bytesTotalIdx) else -1L
                val title = if (titleIdx != -1) cursor.getString(titleIdx) ?: fallbackFileName else fallbackFileName

                Log.d("DownloadProgress", "Status: $status | Downloaded: $bytesDownloaded | Total: $bytesTotal")

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        emit(DownloadState.Success(title))
                        isDownloading = false
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val reasonCode = if (reasonIdx != -1) cursor.getInt(reasonIdx) else -1
                        emit(DownloadState.Failed("Download failed with reason code: $reasonCode"))
                        isDownloading = false
                    }
                    DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED -> {
                        if (bytesTotal > 0) {
                            val progress = ((bytesDownloaded * 100) / bytesTotal).toInt()
                            emit(DownloadState.Downloading(progress, bytesDownloaded, bytesTotal, indeterminate = false))
                        } else {
                            emit(DownloadState.Downloading(0, bytesDownloaded, bytesTotal, indeterminate = true))
                        }
                    }
                }
                cursor.close()
            } else {
                cursor?.close()
                emit(DownloadState.Failed("Download process lost or removed"))
                isDownloading = false
            }

            if (isDownloading) {
                delay(500) // Poll every 500ms (150ms can overhead the cursor queries)
            }
        }
    }.flowOn(Dispatchers.IO) // Execute Cursor DB queries on IO thread

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}