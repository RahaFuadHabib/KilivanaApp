package com.example.farmapp  //This identifies the Kotlin package containing MainActivity

import android.os.Bundle  //Holds state information that Android can pass to an activity during recreation.
import androidx.activity.ComponentActivity //Base Android activity class supporting lifecycle-aware components.
import androidx.activity.compose.setContent  //Installs the Jetpack Compose UI inside the activity.
import androidx.activity.enableEdgeToEdge  //Allows app content to draw behind system bars, with appropriate inset handling.
import androidx.activity.viewModels  //Obtains an activity-scoped ViewModel using lazy initialization.
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.Composable   //Marks a function that contributes to the Compose UI.
import androidx.compose.ui.tooling.preview.Preview  //Enables Android Studio to preview a composable.
import com.example.farmapp.ui.navigation.AppNavigation
import com.example.farmapp.ui.theme.FarmAppTheme
import com.example.farmapp.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() { //this declares a class called MainActivity that inherits from ComponentActivity.
    //The colon : indicates inheritance in this context.

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModel.factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Share the activity's ViewModel with Compose; restoration runs only once.
        val startupAuthViewModel = authViewModel
        splashScreen.setKeepOnScreenCondition {
            startupAuthViewModel.uiState.value.isInitializing
        }
        setTitle(R.string.app_name)
        enableEdgeToEdge()
        setContent {
            FarmAppTheme {
                AppNavigation(authViewModel = startupAuthViewModel)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppNavigationPreview() {
    FarmAppTheme {
        AppNavigation()
    }
}
