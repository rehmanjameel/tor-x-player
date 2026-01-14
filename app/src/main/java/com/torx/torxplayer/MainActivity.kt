package com.torx.torxplayer

import android.Manifest
import android.app.PictureInPictureParams
import android.app.PictureInPictureUiState
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.Rational
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.messaging.FirebaseMessaging
import com.torx.torxplayer.databinding.ActivityMainBinding
import androidx.core.net.toUri
import androidx.media3.exoplayer.ExoPlayer
import com.torx.torxplayer.fragments.VideoPlayerFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private var player: ExoPlayer? = null

    var allowPip = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        //find the NavHostFragment and get its NavController
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        // Get reference to the BottomNavigationView from the layout
        val navView: BottomNavigationView = binding.bottomNavView
        binding.bottomNavView.itemRippleColor = null

        // Set item icon tint list to null (optional, to disable default icon tinting)
        navView.itemIconTintList = null

        // Set up the BottomNavigationView with the NavController for navigation
        navView.setupWithNavController(navController)

        // hide the bottom nav view in custom fragments
        navController.addOnDestinationChangedListener { _, destination, _ ->

            when (destination.id) {
                R.id.videosFragment,
                R.id.audiosFragment,
                R.id.privateFilesFragment -> {
                    binding.bottomNavView.visibility = View.VISIBLE
                }

                else -> {
                    binding.bottomNavView.visibility = View.GONE
                }
            }
        }

        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("TAG", "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result

            // Log and toast
            val msg = token
            Log.d("TAG", msg)
//            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
        })
        FirebaseMessaging.getInstance().subscribeToTopic("allUsers")

        intent?.extras?.getString("link")?.let { link ->
            val browserIntent = Intent(Intent.ACTION_VIEW, link.toUri())
            startActivity(browserIntent)
            finish()
        }

//        askNotificationPermission()

//        requestMediaPermissions()
    }

    private var blockNextPip = false

    fun blockNextPip() {
        blockNextPip = true
    }


    fun attachPlayer(exoPlayer: ExoPlayer?) {
        player = exoPlayer
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (blockNextPip) {
            blockNextPip = false
            return
        }

        if (
            allowPip &&
            !isInPictureInPictureMode &&
            player != null &&
            player!!.isPlaying
        ) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun enterPipIfNeeded() {
        if (player?.isPlaying == true) {

            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()

            enterPictureInPictureMode(params)
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onPictureInPictureUiStateChanged(
        pipState: PictureInPictureUiState
    ) {
        super.onPictureInPictureUiStateChanged(pipState)


        notifyPlayerFragmentPipChanged(pipState.isTransitioningToPip)

        if (!isInPictureInPictureMode) {
            // ✅ We are BACK from PiP
            allowPip = true
            blockNextPip = false
        }
    }


    private fun notifyPlayerFragmentPipChanged(isInPip: Boolean) {
        val navHost =
            supportFragmentManager.findFragmentById(R.id.navHostFragment)

        navHost?.childFragmentManager?.fragments
            ?.filterIsInstance<VideoPlayerFragment>()
            ?.firstOrNull()
            ?.onPipModeChanged(isInPip)

    }

    fun setPipAllowed(allowed: Boolean) {
        allowPip = allowed
    }

    fun resetBlockNextPip() {
        blockNextPip = false
    }


    override fun onPause() {
        super.onPause()
//        if (!isInPictureInPictureMode) {
//            player?.pause()
//        }
    }

    override fun onResume() {
        super.onResume()
    }
}
