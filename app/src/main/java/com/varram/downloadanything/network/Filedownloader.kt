package com.varram.downloadanything.network

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.varram.downloadanything.viewmodel.DownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class FileDownloader(private val context: Context) {

    private val client = OkHttpClient.Builder().build()

    fun download(url: String): Flow<DownloadState> = flow {
        val rawFileName = url.substringAfterLast('/').substringBefore('?').ifEmpty { "downloaded_file" }
        val request = Request.Builder().url(url).build()

        var response: Response? = null
        try {
            response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                emit(DownloadState.Failed("Server returned ${response.code}"))
                return@flow
            }

            val body = response.body ?: run {
                emit(DownloadState.Failed("Empty response body"))
                return@flow
            }

            val totalBytes = body.contentLength()
            val mimeType = getMimeType(rawFileName, response)

            val (outputStream, pendingUri) = openOutputStream(rawFileName, mimeType)
                ?: run {
                    emit(DownloadState.Failed("Could not create output stream"))
                    return@flow
                }

            var bytesDownloaded = 0L
            val buffer = ByteArray(8 * 1024)

            // Speed & ETA tracking variables
            var lastSampleTime = System.currentTimeMillis()
            var bytesSinceLastSample = 0L

            body.byteStream().use { input: InputStream ->
                outputStream.use { output: OutputStream ->
                    while (true) {
                        currentCoroutineContext().ensureActive()

                        val read = input.read(buffer)
                        if (read == -1) break

                        output.write(buffer, 0, read)
                        bytesDownloaded += read
                        bytesSinceLastSample += read

                        val currentTime = System.currentTimeMillis()
                        val timeDelta = currentTime - lastSampleTime

                        // Emit update every 500ms to calculate accurate speed & ETA
                        if (timeDelta >= 500 || bytesDownloaded == totalBytes) {
                            val speedBytesPerSec = if (timeDelta > 0) (bytesSinceLastSample * 1000) / timeDelta else 0L
                            val remainingBytes = if (totalBytes > 0) totalBytes - bytesDownloaded else 0L
                            val etaSeconds = if (speedBytesPerSec > 0) remainingBytes / speedBytesPerSec else 0L

                            val progress = if (totalBytes > 0) {
                                ((bytesDownloaded * 100) / totalBytes).toInt()
                            } else 0

                            emit(
                                DownloadState.Downloading(
                                    progress = progress,
                                    bytesDownloaded = bytesDownloaded,
                                    bytesTotal = totalBytes,
                                    speedBytesPerSec = speedBytesPerSec,
                                    etaSeconds = etaSeconds,
                                    indeterminate = totalBytes <= 0
                                )
                            )

                            // Reset sampling window
                            lastSampleTime = currentTime
                            bytesSinceLastSample = 0L
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pendingUri != null) {
                val doneValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                context.contentResolver.update(pendingUri, doneValues, null, null)
            }

            emit(DownloadState.Success(rawFileName))
        } catch (e: IOException) {
            emit(DownloadState.Failed(e.message ?: "Network error"))
        } catch (e: Exception) {
            emit(DownloadState.Failed(e.localizedMessage ?: "Unknown error"))
        } finally {
            response?.close()
        }
    }.flowOn(Dispatchers.IO)

    private fun openOutputStream(fileName: String, mimeType: String): Pair<OutputStream, Uri?>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
            resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, selection, arrayOf(fileName))

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            val stream = resolver.openOutputStream(uri) ?: return null
            stream to uri
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val file = File(downloadsDir, fileName)
            if (file.exists()) file.delete()

            FileOutputStream(file) to null
        }
    }

    private fun getMimeType(fileName: String, response: Response): String {
        val contentType = response.header("Content-Type")
        if (!contentType.isNullOrEmpty() && !contentType.contains("octet-stream")) {
            return contentType.substringBefore(';')
        }
        val extension = fileName.substringAfterLast('.', "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            ?: "application/octet-stream"
    }
}