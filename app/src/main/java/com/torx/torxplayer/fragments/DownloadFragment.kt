package com.torx.torxplayer.fragments

import android.graphics.Bitmap
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.navigation.fragment.findNavController
import com.torx.torxplayer.R
import com.torx.torxplayer.databinding.FragmentDownloadBinding

class DownloadFragment : Fragment() {

    private lateinit var binding: FragmentDownloadBinding
    private var isWebView = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentDownloadBinding.inflate(inflater, container, false)

        // Configure WebView settings (optional)
        binding.mediaWV.settings.javaScriptEnabled = true // Enable JavaScript
        binding.mediaWV.settings.domStorageEnabled = true // Enable DOM storage

        // Set a WebViewClient to handle page navigation within the app
        binding.mediaWV.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                // Hide overlay only after new page is fully drawn
                binding.webviewOverlay.visibility = View.GONE
                binding.progressBar.visibility = View.GONE
            }
        }

        // Load a URL

        binding.fbLayout.setOnClickListener {
            loadCleanUrl("https://snapsave.app/")
        }

        binding.ytLayout.setOnClickListener {
            loadCleanUrl(" https://turboscribe.ai/downloader/youtube/video")
        }

        binding.instaLayout.setOnClickListener {
            loadCleanUrl("https://fastdl.app/en2")
        }

        binding.tiktokLayout.setOnClickListener {
            loadCleanUrl("https://ssstik.io/en-1")
        }

        // Create an OnBackPressedCallback
        val callback = object : OnBackPressedCallback(true) { // 'true' means it's initially enabled
            override fun handleOnBackPressed() {
                // Implement your custom back press logic here
                // For example, show a dialog, navigate to a specific screen, etc.
                if (isWebView) {
                    // Perform custom action
                    binding.mediaWV.visibility = View.GONE
                    isWebView = false

                } else {
                    // If you don't handle it, you can disable the callback
                    // to let the dispatcher find the next enabled callback
                    // or fall back to default system behavior.
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed() // Call the dispatcher to continue the back press flow
                }
            }
        }

        // Add the callback to the dispatcher, associating it with the lifecycle owner
        requireActivity().onBackPressedDispatcher.addCallback(this, callback)

        binding.backArrow.setOnClickListener {
            findNavController().navigateUp()
        }

        return binding.root
    }

    private fun loadCleanUrl(url: String) {
        // Show overlay BEFORE loading
        binding.webviewOverlay.visibility = View.VISIBLE

        isWebView = true
        binding.mediaWV.visibility = View.VISIBLE

        // Now load the new link
        binding.mediaWV.loadUrl(url)
    }


}