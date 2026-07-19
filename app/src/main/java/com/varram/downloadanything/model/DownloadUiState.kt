package com.varram.downloadanything.model

data class DownloadUiState(
    val progress: DownloadProgress? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED
)