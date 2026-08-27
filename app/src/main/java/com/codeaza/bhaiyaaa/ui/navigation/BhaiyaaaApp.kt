package com.codeaza.bhaiyaaa.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.codeaza.bhaiyaaa.ui.AppViewModel
import com.codeaza.bhaiyaaa.ui.screens.AssistantScreen
import com.codeaza.bhaiyaaa.ui.screens.CallsScreen
import com.codeaza.bhaiyaaa.ui.screens.ContactDetailScreen
import com.codeaza.bhaiyaaa.ui.screens.ContactsScreen
import com.codeaza.bhaiyaaa.ui.screens.HomeScreen
import com.codeaza.bhaiyaaa.ui.screens.InsightsScreen
import com.codeaza.bhaiyaaa.ui.screens.PrivacyCenterScreen
import com.codeaza.bhaiyaaa.ui.screens.PrivacyLockScreen
import com.codeaza.bhaiyaaa.ui.screens.SettingsScreen
import com.codeaza.bhaiyaaa.ui.screens.VipScreen
import com.codeaza.bhaiyaaa.util.SecurePrefs

sealed class Destination(val route: String, val label: String) {
    object Home : Destination("home", "Home")
    object Calls : Destination("calls", "Calls")
    object Vip : Destination("vip", "VIP")
    object Assistant : Destination("assistant", "Assistant")
    object Contacts : Destination("contacts", "Contacts")
    object Settings : Destination("settings", "Settings")
}

private val bottomNavItems = listOf(
    Destination.Home, Destination.Calls, Destination.Vip,
    Destination.Assistant, Destination.Contacts, Destination.Settings
)

@Composable
fun BhaiyaaaApp() {
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(!SecurePrefs.isLockEnabled(context)) }

    if (!unlocked) {
        PrivacyLockScreen(onUnlocked = { unlocked = true })
        return
    }

    val navController = rememberNavController()
    val viewModel: AppViewModel = viewModel()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination

            NavigationBar {
                bottomNavItems.forEach { destination ->
                    val selected = currentRoute?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            val icon = when (destination) {
                                Destination.Home -> Icons.Filled.Home
                                Destination.Calls -> Icons.Filled.Call
                                Destination.Vip -> Icons.Filled.Star
                                Destination.Assistant -> Icons.Filled.Chat
                                Destination.Contacts -> Icons.Filled.Person
                                Destination.Settings -> Icons.Filled.Settings
                            }
                            Icon(icon, contentDescription = destination.label)
                        },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Destination.Home.route) {
                HomeScreen(viewModel) { navController.navigate("insights") }
            }
            composable(Destination.Calls.route) { CallsScreen(viewModel) }
            composable(Destination.Vip.route) {
                VipScreen(viewModel) { phoneNumber -> navController.navigate("contact/$phoneNumber") }
            }
            composable(Destination.Assistant.route) { AssistantScreen(viewModel) }
            composable(Destination.Contacts.route) {
                ContactsScreen(viewModel) { phoneNumber -> navController.navigate("contact/$phoneNumber") }
            }
            composable(Destination.Settings.route) {
                SettingsScreen(onOpenPrivacyCenter = { navController.navigate("privacy_center") })
            }
            composable("insights") {
                InsightsScreen(viewModel) { navController.popBackStack() }
            }
            composable("privacy_center") {
                PrivacyCenterScreen(viewModel) { navController.popBackStack() }
            }
            composable(
                route = "contact/{phoneNumber}",
                arguments = listOf(navArgument("phoneNumber") { type = NavType.StringType })
            ) { backStackEntry ->
                val phoneNumber = backStackEntry.arguments?.getString("phoneNumber") ?: ""
                ContactDetailScreen(viewModel, phoneNumber) { navController.popBackStack() }
            }
        }
    }
}
