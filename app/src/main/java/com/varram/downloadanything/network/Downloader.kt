package com.varram.downloadanything.network

import com.varram.downloadanything.model.DownloadProgress
import com.varram.downloadanything.model.DownloadRequest
import com.varram.downloadanything.model.DownloadResult
import com.varram.downloadanything.model.DownloadUiState
import kotlinx.coroutines.flow.Flow

interface Downloader {

    fun download(
        request: DownloadRequest
    ): Flow<DownloadProgress>
}