package com.feerlessleadr.sightline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.feerlessleadr.sightline.ui.AppViewModel
import com.feerlessleadr.sightline.ui.CameraListScreen
import com.feerlessleadr.sightline.ui.LoginScreen
import com.feerlessleadr.sightline.ui.SightlineTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SightlineTheme {
                val state by viewModel.state.collectAsState()
                val session = state.session
                if (session == null) {
                    LoginScreen(state.form, viewModel::signIn)
                } else {
                    CameraListScreen(
                        session = session,
                        onSelectGroup = viewModel::selectGroup,
                        onRefresh = viewModel::refresh,
                        onSignOut = viewModel::signOut,
                    )
                }
            }
        }
    }
}
