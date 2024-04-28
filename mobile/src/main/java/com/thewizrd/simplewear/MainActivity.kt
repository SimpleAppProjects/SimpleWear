package com.thewizrd.simplewear

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
                        .commit()
                }
            }
        }

        val appBarLayout = findViewById<AppBarLayout>(R.id.app_bar)
        appBarLayout.liftOnScrollTargetViewId = R.id.scrollView
        appBarLayout.isLiftOnScroll = true
    }
}