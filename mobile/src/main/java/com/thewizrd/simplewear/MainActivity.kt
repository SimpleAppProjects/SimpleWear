package com.thewizrd.simplewear

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.color.DynamicColors

class MainActivity : AppCompatActivity() {
    private var isReadyToView = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val splashScreen = installSplashScreen()

        // Note: needed due to splash screen theme
        DynamicColors.applyIfAvailable(this)

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

        // Check if fragment exists
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PermissionCheckFragment())
                .runOnCommit {
                    isReadyToView = true
                }
                .commit()
        }

        val appBarLayout = findViewById<AppBarLayout>(R.id.app_bar)
        appBarLayout.liftOnScrollTargetViewId = R.id.scrollView
        appBarLayout.isLiftOnScroll = true
    }
}