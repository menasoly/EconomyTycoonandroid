package app.many.economytycoon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.many.economytycoon.android.AppContainer
import app.many.economytycoon.ui.compose.app.AppHost
import app.many.economytycoon.ui.compose.app.EconomyApp

class MainActivity : ComponentActivity() {
    private lateinit var host: AppHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        host = AppContainer.host(this)
        setContent { EconomyApp(host) }
    }

    /** Leaving the app writes the running game to its quit slot, so "Continue" always finds the latest state. */
    override fun onStop() {
        super.onStop()
        host.saveQuietly()
    }
}
