package com.torx.torxplayer.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import com.arconn.devicedesk.utils.AppGlobals
import com.torx.torxplayer.R
import com.torx.torxplayer.databinding.FragmentPrivateFilesBinding

class PrivateFilesFragment : Fragment() {

    private lateinit var binding: FragmentPrivateFilesBinding

    private lateinit var dots: List<View>
//    private lateinit var title: TextView
    private var enteredPin = ""
    private var tempPin = ""
    private var isSettingPin = false
    private var isConfirmingPin = false
    private val appGlobals = AppGlobals()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentPrivateFilesBinding.inflate(inflater, container, false)

        dots = listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4)

        val savedPin = appGlobals.getValueString("user_pin")
        isSettingPin = savedPin == null

        if (isSettingPin) {
            binding.tvTitle.text = "Create PIN"
        } else {
            binding.tvTitle.text = "Enter PIN"
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupKeypad(view)
    }
    private fun setupKeypad(view: View) {
        val buttons = mapOf(
            R.id.btn0 to "0", R.id.btn1 to "1", R.id.btn2 to "2",
            R.id.btn3 to "3", R.id.btn4 to "4", R.id.btn5 to "5",
            R.id.btn6 to "6", R.id.btn7 to "7", R.id.btn8 to "8",
            R.id.btn9 to "9"
        )

        // Assign numbers to TextViews inside each CardView
        for ((id, number) in buttons) {
            val card = view.findViewById<CardView>(id)
            val text = card.findViewById<TextView>(R.id.tvKey)
            text.text = number
            card.setOnClickListener { onDigitPressed(number) }
        }

        val btnDel = view.findViewById<CardView>(R.id.btnDel)
        val tvDel = btnDel.findViewById<TextView>(R.id.tvKey)
        tvDel.text = "⌫"
        btnDel.setOnClickListener { onDeletePressed() }
    }

    private fun onDigitPressed(digit: String) {
        if (enteredPin.length < 4) {
            enteredPin += digit
            updateDots()
        }

        if (enteredPin.length == 4) {
            if (isSettingPin) {
                if (!isConfirmingPin) {
                    // Step 1: Enter new PIN
                    tempPin = enteredPin
                    enteredPin = ""
                    isConfirmingPin = true
                    binding.tvTitle.text = "Confirm PIN"
                    updateDots()
                } else {
                    // Step 2: Confirm PIN
                    if (enteredPin == tempPin) {
                        appGlobals.saveString("user_pin", enteredPin)
                        Toast.makeText(requireContext(), "PIN set successfully!", Toast.LENGTH_SHORT).show()
//                        requireActivity().finish() // or open your private files activity
                    } else {
                        Toast.makeText(requireContext(), "PINs do not match!", Toast.LENGTH_SHORT).show()
                        enteredPin = ""
                        tempPin = ""
                        isConfirmingPin = false
                        binding.tvTitle.text = "Create PIN"
                        updateDots()
                    }
                }
            } else {
                // Unlock mode
                val savedPin = appGlobals.getValueString("user_pin")
                if (enteredPin == savedPin) {
                    Toast.makeText(requireContext(), "Unlocked!", Toast.LENGTH_SHORT).show()
//                    requireActivity().finish() // or navigate to your main/private files activity
                } else {
                    Toast.makeText(requireContext(), "Wrong PIN!", Toast.LENGTH_SHORT).show()
                    enteredPin = ""
                    updateDots()
                }
            }
        }
    }

    private fun onDeletePressed() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            updateDots()
        }
    }

    private fun updateDots() {
        for (i in dots.indices) {
            if (i < enteredPin.length)
                dots[i].setBackgroundResource(R.drawable.pin_dot_active)
            else
                dots[i].setBackgroundResource(R.drawable.pin_dot_inactive)
        }
    }
}
