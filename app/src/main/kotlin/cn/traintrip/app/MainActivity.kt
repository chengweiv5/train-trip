package cn.traintrip.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import cn.traintrip.app.ui.TrainTripApp
import cn.traintrip.app.ui.TrainTripTheme

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT))
        setContent {
            val themeVm:ThemeViewModel=viewModel(factory=ThemeViewModel.factory(applicationContext))
            val theme by themeVm.state.collectAsStateWithLifecycle()
            TrainTripTheme(theme.choice) {
                val vm:AppViewModel=viewModel()
                TrainTripApp(vm,themeState=theme,onThemeSelect=themeVm::select,onThemeRetry=themeVm::reload,onThemeDismiss=themeVm::clearFeedback)
            }
        }
    }
}
