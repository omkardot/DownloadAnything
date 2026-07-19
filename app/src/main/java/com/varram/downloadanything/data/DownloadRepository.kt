package com.varram.downloadanything.data

import com.varram.downloadanything.model.DownloadRequest
import com.varram.downloadanything.network.Downloader

class DownloadRepository(private val downloader: Downloader) {

    fun downloadthis(downloadReq: DownloadRequest) =
        downloader.download(downloadReq)

}