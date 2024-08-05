package com.thewizrd.simplewear

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.MenuProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.color.DynamicColors
import com.thewizrd.simplewear.updates.InAppUpdateManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    companion object {
        private const val INSTALL_REQUESTCODE = 168
    }

    private lateinit var inAppUpdateManager: InAppUpdateManager
    private var isReadyToView = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Note: needed due to splash screen theme
        DynamicColors.applyIfAvailable(this)

        inAppUpdateManager = InAppUpdateManager.create(applicationContext)

        // Stop activity from rendering until next activity or if immediate update available
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                return if (isReadyToView) {
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    true
                } else {
                    false
                }
            }
        })

        setContentView(R.layout.activity_main)

        lifecycleScope.launch {
            if (inAppUpdateManager.shouldStartImmediateUpdateFlow()) {
                inAppUpdateManager.startImmediateUpdateFlow(this@MainActivity, INSTALL_REQUESTCODE)
            } else {
                // Check if fragment exists
                if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, PermissionCheckFragment())
                        .runOnCommit {
                            isReadyToView = true
                        }
                        .commitAllowingStateLoss()
                }
            }
        }

        val appBarLayout = findViewById<AppBarLayout>(R.id.app_bar)
        appBarLayout.liftOnScrollTargetViewId = R.id.scrollView
        appBarLayout.isLiftOnScroll = true

        setSupportActionBar(findViewById(R.id.toolbar))
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.actions, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                when (menuItem.itemId) {
                    R.id.timed_actions -> {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, TimedActionsFragment())
                            .addToBackStack("timedActions")
                            .commit()
                        return true
                    }
                }

                return false
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.setGroupVisible(
                    R.id.action_group,
                    supportFragmentManager.backStackEntryCount == 0
                )
            }
        })
        supportActionBar?.setDefaultDisplayHomeAsUpEnabled(true)

        supportFragmentManager.addOnBackStackChangedListener {
            supportActionBar?.setDisplayHomeAsUpEnabled(supportFragmentManager.backStackEntryCount > 0)
            invalidateMenu()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                supportFragmentManager.popBackStack()
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()

        // Checks that the update is not stalled during 'onResume()'.
        // However, you should execute this check at all entry points into the app.
        inAppUpdateManager.resumeUpdateIfStarted(this, INSTALL_REQUESTCODE)
        isReadyToView = true
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == INSTALL_REQUESTCODE) {
            if (resultCode != RESULT_OK) {
                // Update flow failed; exit
                finishAffinity()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}