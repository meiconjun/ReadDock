package com.comichub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.comichub.app.ui.PageLoomApp
import com.comichub.app.ui.PageLoomTheme

class MainActivity : ComponentActivity() {
    private var appBackHandler: (() -> Boolean)? = null

    private val appBackCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (appBackHandler?.invoke() == true) return

            // No in-app page remains. Temporarily disable this callback so the
            // Activity's default behavior can finish the task normally.
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, appBackCallback)
        setContent {
            PageLoomTheme {
                PageLoomApp()
            }
        }
    }

    internal fun setAppBackHandler(handler: (() -> Boolean)?) {
        appBackHandler = handler
    }
}
