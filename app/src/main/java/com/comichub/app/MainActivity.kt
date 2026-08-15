package com.comichub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.comichub.app.ui.ComicHubApp
import com.comichub.app.ui.ComicHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComicHubTheme {
                ComicHubApp()
            }
        }
    }
}
