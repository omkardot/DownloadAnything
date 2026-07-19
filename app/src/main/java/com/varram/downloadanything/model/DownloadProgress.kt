package com.varram.downloadanything.model

data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val progress: Int,
    val speedBytesPerSecond: Long,
    val etaSeconds: Long
)