package dev.izumi.appops

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.izumi.appops.model.DeviceSummary
import dev.izumi.appops.presentation.home.HomeScreen
import dev.izumi.appops.ui.theme.AppOpsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppOpsTheme {
                HomeScreen(
                    device = DeviceSummary(
                        manufacturer = Build.MANUFACTURER,
                        model = Build.MODEL,
                        androidVersion = Build.VERSION.RELEASE,
                        apiLevel = Build.VERSION.SDK_INT,
                    ),
                )
            }
        }
    }
}
