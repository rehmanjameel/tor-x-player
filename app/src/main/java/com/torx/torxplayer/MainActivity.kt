package com.torx.torxplayer

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.PictureInPictureUiState
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
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
        })
        FirebaseMessaging.getInstance().subscribeToTopic("allUsers")

        intent?.extras?.getString("link")?.let { link ->
            val browserIntent = Intent(Intent.ACTION_VIEW, link.toUri())
            startActivity(browserIntent)
            finish()
        }
    }

    private var blockNextPip = false


    fun attachPlayer(exoPlayer: ExoPlayer?) {
        player = exoPlayer
    }

    private var pipOwnerTag: String? = null

    fun setPipOwner(owner: String?) {
        pipOwnerTag = owner
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        // 🔥 PiP ONLY if VideoPlayerFragment owns it
        if (pipOwnerTag == VideoPlayerFragment::class.java.name &&
            !isInPictureInPictureMode &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }
    }

//    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
//    override fun onPictureInPictureUiStateChanged(pipState: PictureInPictureUiState) {
//        super.onPictureInPictureUiStateChanged(pipState)
//
//        val isInPip = isInPictureInPictureMode // 🔥 REAL STATE
//
//        supportFragmentManager
//            .findFragmentById(R.id.navHostFragment)
//            ?.childFragmentManager
//            ?.fragments
//            ?.filterIsInstance<VideoPlayerFragment>()
//            ?.firstOrNull()
//            ?.onPipModeChanged(isInPip)
//    }

    // new buttons

    fun onPipPlayPause() {
        findPlayerFragment()?.togglePlayPauseFromPip()
    }

    fun onPipNext() {
        findPlayerFragment()?.playNextFromPip()
    }

    fun onPipPrevious() {
        findPlayerFragment()?.playPreviousFromPip()
    }

    private fun findPlayerFragment(): VideoPlayerFragment? {
        val navHost =
            supportFragmentManager.findFragmentById(R.id.navHostFragment)

        return navHost
            ?.childFragmentManager
            ?.fragments
            ?.filterIsInstance<VideoPlayerFragment>()
            ?.firstOrNull()
    }

////////////////////
    fun setPipAllowed(allowed: Boolean) {
        allowPip = allowed
    }

    fun resetBlockNextPip() {
        blockNextPip = false
    }

    override fun onResume() {
        super.onResume()
    }

    private fun createPipIntent(action: String): PendingIntent {
        val intent = Intent(action)
        intent.setPackage(packageName)

        return PendingIntent.getBroadcast(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun updatePipActions(isPlaying: Boolean) {

//        if (!isInPictureInPictureMode) return   // 🔥 required

        val playPauseIcon = if (isPlaying)
            R.drawable.baseline_pause_circle_filled_24
        else
            R.drawable.baseline_play_arrow_24

        val actions = listOf(
            RemoteAction(
                Icon.createWithResource(this, R.drawable.baseline_fast_rewind_24),
                "Previous",
                "Previous",
                createPipIntent(ACTION_PIP_PREVIOUS)
            ),
            RemoteAction(
                Icon.createWithResource(this, playPauseIcon),
                if (isPlaying) "Pause" else "Play",
                if (isPlaying) "Pause" else "Play",
                createPipIntent(ACTION_PIP_PLAY_PAUSE)
            ),
            RemoteAction(
                Icon.createWithResource(this, R.drawable.baseline_fast_forward_24),
                "Next",
                "Next",
                createPipIntent(ACTION_PIP_NEXT)
            )
        )

        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(actions)
            .build()

        setPictureInPictureParams(params)
    }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PIP_PLAY_PAUSE -> onPipPlayPause()
                ACTION_PIP_NEXT -> onPipNext()
                ACTION_PIP_PREVIOUS -> onPipPrevious()
            }
        }
    }

    ///////////
    companion object {
        const val ACTION_PIP_PLAY_PAUSE = "pip_play_pause"
        const val ACTION_PIP_NEXT = "pip_next"
        const val ACTION_PIP_PREVIOUS = "pip_previous"

    }
//////////

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStart() {
        super.onStart()

        val filter = IntentFilter().apply {
            addAction(ACTION_PIP_PLAY_PAUSE)
            addAction(ACTION_PIP_NEXT)
            addAction(ACTION_PIP_PREVIOUS)
        }
        registerReceiver(
            pipReceiver,
            filter,
            RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(pipReceiver)

        if (
            pipOwnerTag == VideoPlayerFragment::class.java.name &&
            !isInPictureInPictureMode &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (!hasFocus &&
            pipOwnerTag == VideoPlayerFragment::class.java.name &&
            !isInPictureInPictureMode &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            try {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                )
            } catch (e: IllegalStateException) {
                Log.e("PIP", "Failed to enter PiP", e)
            }
        }
    }


}
