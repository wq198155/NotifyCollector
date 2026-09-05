package com.example.notifycollector.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.example.notifycollector.ui.theme.NotifyCollectorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 调试/演示辅助：通过 Intent extra startRoute 指定初始页。
        // 例：`adb shell am start -n .../MainActivity --es startRoute settings`
        val startRoute = intent?.getStringExtra("startRoute")
        setContent {
            NotifyCollectorTheme {
                AppNav(startRoute = startRoute)
            }
        }
    }
}

@Composable
fun AppNav(startRoute: String? = null) {
    val nav: NavHostController = rememberNavController()
    LaunchedEffect(startRoute) {
        if (!startRoute.isNullOrEmpty()) {
            nav.navigate(startRoute) {
                popUpTo("home") { inclusive = true }
            }
        }
    }
    NavHost(navController = nav, startDestination = "home") {
        composable("home") { HomeScreen(nav) }
        composable("settings") { SettingsScreen(nav) }
        composable("recyclebin") { RecycleBinScreen(nav) }
        composable("add") { AddGroupScreen(nav) }
        composable("presets") { PresetsScreen(nav) }
        composable(
            "edit/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.LongType })
        ) {
            AddGroupScreen(nav, it.arguments!!.getLong("groupId"))
        }
        composable(
            "group/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.LongType })
        ) {
            GroupDetailScreen(nav, it.arguments!!.getLong("groupId"))
        }
        composable(
            "detail/{notifId}",
            arguments = listOf(navArgument("notifId") { type = NavType.LongType })
        ) {
            NotificationDetailScreen(nav, it.arguments!!.getLong("notifId"))
        }
    }
}
