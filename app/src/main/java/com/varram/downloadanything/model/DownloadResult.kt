package com.varram.downloadanything.model

sealed interface DownloadResult {

    data class Progress(val persentage :String ,val kbps:Int) : DownloadResult

    data class Error(
        val message: String
    ) : DownloadResult

    object Completed : DownloadResult
}