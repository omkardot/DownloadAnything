package com.varram.downloadanything.viewmodel

import androidx.lifecycle.ViewModel
import com.varram.downloadanything.data.DownloadRepository
import com.varram.downloadanything.model.DownloadRequest

class DownloadViewModel(
    
    private val repository: DownloadRepository
) : ViewModel() {

    // ✅ Correct
    fun startDownload() {
        .launch {
            downloader.download(request).collect { progress ->
                println(progress)
            }
        }
    }
}