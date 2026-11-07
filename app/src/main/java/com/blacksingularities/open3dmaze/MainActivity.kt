package com.blacksingularities.open3dmaze

import com.blacksingularities.open3dmaze.feedback.Feedback
import com.blacksingularities.open3dmaze.feedback.CrashReportPrompt
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.blacksingularities.open3dmaze.data.Store
import com.blacksingularities.open3dmaze.ui.App
import com.blacksingularities.open3dmaze.ui.MazeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Feedback.install(this, "open3dmaze")
        Store.init(applicationContext)
        enableEdgeToEdge()
        // Gra steruje się pochyleniem, więc ekran nie może gasnąć w trakcie.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        setContent { MazeTheme { CrashReportPrompt(); App() } }
    }
}
