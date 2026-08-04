package com.varram.downloadanything.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.varram.downloadanything.R

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.varram.downloadanything.network.DownloadNotificationHelper
import com.varram.downloadanything.viewmodel.DownloadState
import com.varram.downloadanything.viewmodel.DownloaderViewModel
import kotlinx.coroutines.launch

class DownloaderActivity : AppCompatActivity()  {

    private lateinit var urlInput: EditText
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var notifificationHelper: DownloadNotificationHelper

    // by viewModels() gives us a ViewModel that survives configuration
    // changes (e.g. screen rotation) instead of being recreated with the Activity.
    private val viewModel: DownloaderViewModel by viewModels()

    private val storagePermissionCode = 100
    private val notificationPermissionCode = 101

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_downloader)

        urlInput = findViewById(R.id.urlInput)
        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.downloadProgressBar)
        progressText = findViewById(R.id.progressText)
        notifificationHelper = DownloadNotificationHelper(applicationContext)

        val downloadButton: Button = findViewById(R.id.downloadButton)

        downloadButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isEmpty()) {
                statusText.text = "Enter a link first."
                return@setOnClickListener
            }
            if (!hasNotificationPermission()) {
                requestNotificationPermission()
            } else {
                notifificationHelper.updateNotification(DownloadState.Idle)
            }
            if (hasStoragePermission()) {
                viewModel.startDownload(applicationContext, url)
            } else {
                requestStoragePermission()
            }
        }

        observeDownloadState()
    }

    // This is the core piece: collect the ViewModel's StateFlow and update
    // views whenever a new state is emitted. repeatOnLifecycle(STARTED)
    // automatically pauses collection when the Activity goes to the
    // background and resumes it when it comes back - this avoids wasted
    // work and crashes from updating views that aren't visible.
    private fun observeDownloadState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.downloadState.collect { state ->
                    when (state) {
                        is DownloadState.Idle -> {
                            progressBar.isIndeterminate = false
                            progressBar.progress = 0
                            progressText.text = "0%"
                            statusText.text = "Ready"
                        }
                        is DownloadState.Downloading -> {
                            // Only change indeterminate mode if it actually toggled
                            if (progressBar.isIndeterminate != state.indeterminate) {
                                progressBar.isIndeterminate = state.indeterminate
                            }

                            if (!state.indeterminate) {
                                progressBar.progress = state.progress
                                progressText.text = "${state.progress}%"
                            } else {
                                val kbDownloaded = state.bytesDownloaded / 1024
                                progressText.text = if (kbDownloaded > 0) "$kbDownloaded KB" else "Starting..."
                            }
                            statusText.text = "Downloading…"
                        }
                        is DownloadState.Success -> {
                            progressBar.isIndeterminate = false
                            progressBar.progress = 100
                            progressText.text = "100%"
                            statusText.text = "Downloaded \"${state.fileName}\" to Downloads folder."
                        }
                        is DownloadState.Failed -> {
                            progressBar.isIndeterminate = false
                            progressBar.progress = 0
                            progressText.text = ""
                            statusText.text = "Failed: ${state.reason}"
                            Toast.makeText(this@DownloaderActivity, state.reason, Toast.LENGTH_SHORT).show()
                        }
                    }
                    notifificationHelper.updateNotification(state)
                }
            }
        }
    }
    private fun hasStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            storagePermissionCode
        )
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotificationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            notificationPermissionCode
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED

        when (requestCode) {
            storagePermissionCode -> {
                if (granted) {
                    viewModel.startDownload(applicationContext, urlInput.text.toString().trim())
                } else {
                    statusText.text = "Storage permission is needed to save the file."
                }
            }
            notificationPermissionCode -> {
//                if (granted) {
//                    notifificationClass.sendNotification(applicationContext)
//                }
                // If denied, we just skip the notification - download still proceeds.
            }
        }
    }
}