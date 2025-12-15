package com.torx.torxplayer.fragments

import android.graphics.Bitmap
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.navigation.fragment.findNavController
import com.torx.torxplayer.R
import com.torx.torxplayer.databinding.FragmentDownloadBinding
import org.json.JSONArray
import java.net.URL
import java.net.URLEncoder

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

        binding.searchTIET.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                enterSearchMode()
            } else {
                exitSearchMode()
            }
        }

        binding.searchTIET.setOnItemClickListener { _, _, position, _ ->
            val query = binding.searchTIET.adapter.getItem(position) as String
            openSearch(query)
        }

        binding.searchTIET.setOnEditorActionListener { _, _, _ ->
            openSearch(binding.searchTIET.text.toString())
            true
        }

        setupSearchSuggestions()

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

    private fun setupSearchSuggestions() {
        val suggestions = mutableListOf<String>()
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            suggestions
        )
        binding.searchTIET.setAdapter(adapter)

        binding.searchTIET.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                if (query.length < 2) return

                fetchGoogleSuggestions(query) { result ->
                    suggestions.clear()
                    suggestions.addAll(result)
                    adapter.notifyDataSetChanged()
                    binding.searchTIET.showDropDown()
                }
            }
        })

    }

    private fun fetchGoogleSuggestions(query: String, callback: (List<String>) -> Unit) {
        Thread {
            try {
                val url =
                    "https://suggestqueries.google.com/complete/search?client=firefox&q=$query"
                val json = URL(url).readText()
                val array = JSONArray(json).getJSONArray(1)

                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }

                requireActivity().runOnUiThread {
                    callback(list)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun enterSearchMode() {
        binding.downloadLinksLayout.visibility = View.GONE
//        binding.ytLayout.visibility = View.GONE
//        binding.instaLayout.visibility = View.GONE
//        binding.tiktokLayout.visibility = View.GONE
        binding.mediaWV.visibility = View.GONE

        binding.webviewOverlay.visibility = View.VISIBLE
    }

    private fun exitSearchMode() {
        binding.downloadLinksLayout.visibility = View.VISIBLE
//        binding.ytLayout.visibility = View.VISIBLE
//        binding.instaLayout.visibility = View.VISIBLE
//        binding.tiktokLayout.visibility = View.VISIBLE

        binding.webviewOverlay.visibility = View.GONE
    }

    private fun openSearch(query: String) {
        val url =
            if (query.startsWith("http")) query
            else "https://www.google.com/search?q=${URLEncoder.encode(query, "UTF-8")}"

        binding.mediaWV.visibility = View.VISIBLE
        binding.mediaWV.loadUrl(url)
        binding.searchTIET.clearFocus()
    }

}