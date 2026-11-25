package com.dailer.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.dailer.app.R
import com.dailer.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.relativesFragment,
                R.id.enrollFragment,
                R.id.qrGeneratorFragment,
                R.id.setupFragment
            )
        )

        binding.toolbar.setupWithNavController(navController, appBarConfiguration)
        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.visibility =
                if (destination.id == R.id.inCallFragment) View.GONE else View.VISIBLE
        }

        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "dailer" && data.host == "enroll") {
            val args = Bundle().apply {
                putString("inviteToken", data.getQueryParameter("token").orEmpty())
                putString("relativeName", data.getQueryParameter("name").orEmpty())
                putString("phoneNumber", data.getQueryParameter("phone").orEmpty())
                putString("relationship", data.getQueryParameter("relationship").orEmpty())
            }
            if (navController.currentDestination?.id == R.id.enrollFragment) {
                navController.popBackStack()
            }
            navController.navigate(R.id.enrollFragment, args)
        } else {
            navController.handleDeepLink(intent)
        }
    }
}
