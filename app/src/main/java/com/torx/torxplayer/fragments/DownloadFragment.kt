package com.torx.torxplayer.fragments

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.torx.torxplayer.R
import com.torx.torxplayer.databinding.FragmentDownloadBinding
import com.torx.torxplayer.utils.MediaDownloader
import org.json.JSONArray
import java.net.URL
import java.net.URLEncoder

class DownloadFragment : Fragment() {

    private val args: DownloadFragmentArgs by navArgs()
    private lateinit var binding: FragmentDownloadBinding

    private var isSearchMode = false
    private var isWebView = false

    private lateinit var suggestionsAdapter: ArrayAdapter<String>
    private val suggestions = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = FragmentDownloadBinding.inflate(inflater, container, false)

        setupWebView()
        setupSearch()
        setupClicks()
        setupBackPress()

        binding.btnDownload.setOnClickListener {

            val url = binding.etUrl.text.toString().trim()

            when {
                url.endsWith(".mp4") || url.endsWith(".mp3") -> {
                    val fileName = "media_${System.currentTimeMillis()}.mp4"
                    MediaDownloader.download(requireContext(), url, fileName)
                }

                else -> {
                    Toast.makeText(
                        requireContext(),
                        "Only direct media links are supported",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }


        return binding.root
    }

    // -------------------- WEBVIEW --------------------

    private fun setupWebView() {
        binding.mediaWV.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        binding.mediaWV.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBar.visibility = View.GONE
                binding.webviewOverlay.visibility = View.GONE
            }
        }
    }

    // -------------------- SEARCH --------------------

    private fun setupSearch() {

        suggestionsAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            suggestions
        )
        binding.searchTIET.setAdapter(suggestionsAdapter)
        binding.searchTIET.threshold = 1

        binding.searchTIET.setOnClickListener {
            if (!isSearchMode) {
                enterSearchMode()
            }
        }

        binding.searchTIET.setOnItemClickListener { _, _, position, _ ->
            val query = suggestionsAdapter.getItem(position) ?: return@setOnItemClickListener
            openSearch(query)
        }

        binding.searchTIET.setOnEditorActionListener { _, _, _ ->
            openSearch(binding.searchTIET.text.toString())
            true
        }

        binding.searchTIET.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                if (query.length < 2) return

                fetchGoogleSuggestions(query) { result ->
                    suggestions.clear()
                    suggestions.addAll(result)
                    suggestionsAdapter.notifyDataSetChanged()

                    if (isSearchMode && binding.searchTIET.hasFocus()) {
                        binding.searchTIET.post {
                            binding.searchTIET.showDropDown()
                        }
                    }
                }
            }
        })
    }

    // -------------------- SEARCH MODE --------------------

    private fun enterSearchMode() {
        isSearchMode = true

        binding.downloadLinksLayout.visibility = View.GONE
        binding.mediaWV.visibility = View.GONE

        // ❗ IMPORTANT: overlay must be GONE for suggestions
//        binding.webviewOverlay.visibility = View.GONE
        binding.searchTV.visibility = View.VISIBLE

        binding.searchTIET.requestFocus()
        showKeyboard()
    }

    private fun exitSearchMode() {
        isSearchMode = false

//        binding.downloadLinksLayout.visibility = View.VISIBLE
        binding.searchTV.visibility = View.GONE

        binding.searchTIET.clearFocus()
        binding.searchTIET.dismissDropDown()
        hideKeyboard()
    }

    // -------------------- SEARCH ACTION --------------------

    private fun openSearch(query: String) {
        if (query.isBlank()) return

        val url =
            if (query.startsWith("http"))
                query
            else
                "https://www.google.com/search?q=${URLEncoder.encode(query, "UTF-8")}"

        isWebView = true
        binding.mediaWV.visibility = View.VISIBLE
        binding.webviewOverlay.visibility = View.VISIBLE

        binding.mediaWV.loadUrl(url)
        exitSearchMode()
    }

    // -------------------- BUTTONS --------------------

    private fun setupClicks() {

        binding.backArrow.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.fbLayout.setOnClickListener {
            loadCleanUrl("https://snapsave.app/")
        }

        binding.ytLayout.setOnClickListener {
            loadCleanUrl("https://turboscribe.ai/downloader/youtube/video")
        }

        binding.instaLayout.setOnClickListener {
            loadCleanUrl("https://fastdl.app/en2")
        }

        binding.tiktokLayout.setOnClickListener {
            loadCleanUrl("https://ssstik.io/en-1")
        }
    }

    private fun loadCleanUrl(url: String) {
        isWebView = true
        binding.mediaWV.visibility = View.VISIBLE
        binding.webviewOverlay.visibility = View.VISIBLE
        binding.mediaWV.loadUrl(url)
    }

    // -------------------- BACK PRESS --------------------

    private fun setupBackPress() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        isSearchMode -> exitSearchMode()
                        isWebView -> {
                            binding.mediaWV.visibility = View.GONE
                            isWebView = false
                        }
                        else -> {
                            isEnabled = false
//                            if (args.isVideo) {
////                                val action = DownloadFragmentDirections.actionDownloadFragmentToVideosFragment()
//                                findNavController().navigate(R.id.action_downloadFragment_to_videosFragment)
//                                findNavController().popBackStack()
//                            } else {
////                                val action = DownloadFragmentDirections.actionDownloadFragmentToAudiosFragment()
//                                findNavController().navigate(R.id.action_downloadFragment_to_audiosFragment)
//                                findNavController().popBackStack()
//
//                            }
//                            findNavController().popBackStack()
                            requireActivity()
                                .onBackPressedDispatcher
                                .onBackPressed()
                        }
                    }
                }
            }
        )
    }

    // -------------------- GOOGLE SUGGESTIONS --------------------

    private fun fetchGoogleSuggestions(
        query: String,
        callback: (List<String>) -> Unit
    ) {
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

    // -------------------- KEYBOARD --------------------

    private fun showKeyboard() {
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)
                    as InputMethodManager
        imm.showSoftInput(binding.searchTIET, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)
                    as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchTIET.windowToken, 0)
    }
}
