package com.varram.downloadanything.network

import com.varram.downloadanything.model.DownloadProgress
import com.varram.downloadanything.model.DownloadRequest
import kotlinx.coroutines.flow.Flow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile



class DownloadImpl(
    private val client: OkHttpClient = OkHttpClient()
) : Downloader {

    override fun download(request: DownloadRequest): Flow<DownloadProgress> = flow {
        val _request = request

        if (_request.url.isEmpty()) {
            throw IllegalArgumentException("Download URL cannot be empty")
        }

        val destinationDir = File(_request.destination)
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }
        val outputFile = File(destinationDir, _request.fileName)

        val httpRequest = Request.Builder()
            .url(_request.url)
            .build()

        client.newCall(httpRequest).execute().use { responce ->
            if (!responce.isSuccessful) {
                throw IOException("Unexpected HTTP code: ${responce.code}")
            }

            val body = responce.body ?: throw IOException("Empty response body")
            val totalBytes = body.contentLength().let { if (it > 0)it else -1L }

            var downloadedBytes = 0L
            val startTime = System.currentTimeMillis()
            val buffer = ByteArray(8 * 1024)

            body.byteStream().use { input ->
                RandomAccessFile(outputFile, "rw").use { output ->
                    var bytesRead: Int
                    var lastEmitTime = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        // Throttle emissions to ~every 200ms to avoid flooding the Flow
                        if (now - lastEmitTime >= 200 || downloadedBytes == totalBytes) {
                            lastEmitTime = now
                            val elapsedSeconds = (now - startTime) / 1000.0
                            val speedBytesPerSecond = if (elapsedSeconds > 0) {
                                (downloadedBytes / elapsedSeconds).toLong()
                            } else 0L

                            val progress = if (totalBytes > 0) {
                                ((downloadedBytes * 100) / totalBytes).toInt()
                            } else -1 // unknown total size

                            val remainingBytes = totalBytes - downloadedBytes
                            val etaSeconds = if (speedBytesPerSecond > 0 && totalBytes > 0) {
                                remainingBytes / speedBytesPerSecond
                            } else -1L

                            emit(
                                DownloadProgress(
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    progress = progress,
                                    speedBytesPerSecond = speedBytesPerSecond,
                                    etaSeconds = etaSeconds
                                )
                            )
                        }
                    }

                    // Ensure a final 100% emission
                    val finalElapsed = (System.currentTimeMillis() - startTime) / 1000.0
                    val finalSpeed = if (finalElapsed > 0) (downloadedBytes / finalElapsed).toLong() else 0L
                    emit(
                        DownloadProgress(
                            downloadedBytes = downloadedBytes,
                            totalBytes = if (totalBytes > 0) totalBytes else downloadedBytes,
                            progress = 100,
                            speedBytesPerSecond = finalSpeed,
                            etaSeconds = 0L
                        )
                    )
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}