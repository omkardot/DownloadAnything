package com.varram.downloadanything.ui

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.varram.downloadanything.R
import com.varram.downloadanything.databinding.FragmentHomeBinding
import com.varram.downloadanything.viewmodel.DownloadState
import com.varram.downloadanything.viewmodel.DownloaderViewModel
import kotlinx.coroutines.launch

class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DownloaderViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        setupListeners()
        observeDownloadState()
    }

    private fun setupListeners() {
        // Paste icon click listener
        binding.urlInputLayout.setEndIconOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                binding.urlEditText.setText(clipData.getItemAt(0).text)
            }
        }

        // Start download button
        binding.btnStartDownload.setOnClickListener {
            val url = binding.urlEditText.text.toString().trim()
            if (url.isNotEmpty()) {
                viewModel.startDownloadFile(requireContext().applicationContext, url)
            } else {
                Toast.makeText(requireContext(), "Please enter or paste a URL", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeDownloadState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.downloadState.collect { state ->
                    when (state) {
                        is DownloadState.Idle -> {
                            binding.downloadProgressCard.visibility = View.GONE
                        }

                        is DownloadState.Downloading -> {
                            binding.downloadProgressCard.visibility = View.VISIBLE

                            if (state.indeterminate) {
                                binding.progressBar.isIndeterminate = true
                                binding.tvProgressRatio.text = "Size Unknown • ${formatBytes(state.bytesDownloaded)} downloaded"
                                binding.tvEta.text = "⏳ --"
                            } else {
                                binding.progressBar.isIndeterminate = false
                                binding.progressBar.progress = state.progress
                                binding.tvProgressRatio.text = "${state.progress}% • ${formatBytes(state.bytesDownloaded)} / ${formatBytes(state.bytesTotal)}"
                                binding.tvEta.text = "⏳ " + formatEta(state.etaSeconds)
                            }

                            binding.tvFileName.text = "Downloading File..."
                            binding.tvSpeed.text = "⚡ " + formatSpeed(state.speedBytesPerSec)
                        }

                        is DownloadState.Success -> {
                            binding.downloadProgressCard.visibility = View.VISIBLE
                            binding.progressBar.isIndeterminate = false
                            binding.progressBar.progress = 100
                            binding.tvFileName.text = "✅ Download Complete!"
                            binding.tvProgressRatio.text = "Saved: ${state.fileName}"
                            binding.tvSpeed.text = ""
                            binding.tvEta.text = ""
                        }

                        is DownloadState.Failed -> {
                            binding.downloadProgressCard.visibility = View.VISIBLE
                            binding.tvFileName.text = "❌ Download Failed"
                            binding.tvProgressRatio.text = state.reason
                            binding.tvSpeed.text = ""
                            binding.tvEta.text = ""
                        }
                    }
                }
            }
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.2f MB/s", bytesPerSec / (1024f * 1024f))
            bytesPerSec >= 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024f)
            else -> "$bytesPerSec B/s"
        }
    }

    private fun formatEta(seconds: Long): String {
        if (seconds <= 0) return "Calculating..."
        val mins = seconds / 60
        val secs = seconds % 60
        return if (mins > 0) String.format("%02dm %02ds left", mins, secs) else "${secs}s left"
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024f * 1024f * 1024f))
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024f * 1024f))
            bytes >= 1024 -> String.format("%.0f KB", bytes / 1024f)
            else -> "$bytes B"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}