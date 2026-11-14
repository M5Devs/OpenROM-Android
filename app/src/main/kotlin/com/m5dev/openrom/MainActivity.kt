// OpenROM-Android — Universal ROM toolkit for Android
// M5 Dev | GPL v3
package com.m5dev.openrom

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.m5dev.openrom.databinding.ActivityMainBinding

/**
 * The single activity. Hosts the navigation graph and the bottom bar, and
 * forwards ROMs handed over by other apps (VIEW/SEND intents) to the Convert
 * screen.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val host = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = host.navController
        binding.bottomNav.setupWithNavController(navController)

        handleIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Routes a shared or opened ROM to the Convert screen. The URI is placed on
     * the nav back-stack arguments so the fragment picks it up after creation.
     */
    private fun handleIntent(intent: android.content.Intent?) {
        val uri: Uri? = when (intent?.action) {
            android.content.Intent.ACTION_VIEW -> intent.data
            android.content.Intent.ACTION_SEND ->
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
            else -> null
        } ?: return

        val args = Bundle().apply { putString("incoming_uri", uri.toString()) }
        navController.navigate(R.id.convertFragment, args)
    }
}
