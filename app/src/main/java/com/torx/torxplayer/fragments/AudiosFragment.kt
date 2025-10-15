package com.torx.torxplayer.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.torx.torxplayer.R
import com.torx.torxplayer.databinding.FragmentAudiosBinding

class AudiosFragment : Fragment() {

    private lateinit var binding: FragmentAudiosBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentAudiosBinding.inflate(inflater, container, false)

        return binding.root
    }

    companion object {
        @JvmStatic
        fun newInstance() = AudiosFragment()
    }
}