package com.torx.torxplayer

import android.view.View

// create an interface for onClickListener
// so that we can handle data most effectively in MainActivity.kt
interface OptionsMenuClickListener {
    fun onOptionsMenuClicked(position: Int, anchorView: View)
}