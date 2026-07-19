package com.varram.downloadanything.model

data class DownloadRequest(
    val url: String,
    val fileName: String,
    val destination: String
)