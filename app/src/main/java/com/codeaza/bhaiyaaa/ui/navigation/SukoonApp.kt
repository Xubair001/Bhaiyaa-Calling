package com.codeaza.bhaiyaaa.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.codeaza.bhaiyaaa.ui.SukoonViewModel
import com.codeaza.bhaiyaaa.ui.LockState
import com.codeaza.bhaiyaaa.ui.assistant.AssistantViewModel
import com.codeaza.bhaiyaaa.ui.models.ModelManagerViewModel
import com.codeaza.bhaiyaaa.ui.theme.Motion
import com.codeaza.bhaiyaaa.ui.onboarding.OnboardingScreen
import com.codeaza.bhaiyaaa.ui.screens.AboutScreen
import com.codeaza.bhaiyaaa.ui.screens.AppearanceSettingsScreen
import com.codeaza.bhaiyaaa.ui.screens.AssistantScreen
import com.codeaza.bhaiyaaa.ui.screens.CallDetailScreen
import com.codeaza.bhaiyaaa.ui.screens.CallsScreen
import com.codeaza.bhaiyaaa.ui.screens.ContactDetailScreen
import com.codeaza.bhaiyaaa.ui.screens.ContactsScreen
import com.codeaza.bhaiyaaa.ui.screens.DataSettingsScreen
import com.codeaza.bhaiyaaa.ui.screens.HomeScreen
import com.codeaza.bhaiyaaa.ui.screens.InsightsScreen
import com.codeaza.bhaiyaaa.ui.screens.LicencesScreen
import com.codeaza.bhaiyaaa.ui.screens.MemoryScreen
import com.codeaza.bhaiyaaa.ui.screens.ModelManagerScreen
import com.codeaza.bhaiyaaa.ui.screens.MoreScreen
import com.codeaza.bhaiyaaa.ui.screens.NotificationSettingsScreen
import com.codeaza.bhaiyaaa.ui.screens.PersonalitySettingsScreen
import com.codeaza.bhaiyaaa.ui.prayer.PrayerViewModel
import com.codeaza.bhaiyaaa.ui.prayer.QuietTimesFocus
import com.codeaza.bhaiyaaa.ui.recordings.VoiceRecordingViewModel
import com.codeaza.bhaiyaaa.ui.screens.PrayerSettingsScreen
import com.codeaza.bhaiyaaa.ui.screens.PrivacyCenterScreen
import com.codeaza.bhaiyaaa.ui.screens.QiblaScreen
import com.codeaza.bhaiyaaa.ui.screens.PrivacyLockScreen
import com.codeaza.bhaiyaaa.ui.screens.RemindersScreen
import com.codeaza.bhaiyaaa.ui.screens.SearchScreen
import com.codeaza.bhaiyaaa.ui.screens.SecuritySettingsScreen
import com.codeaza.bhaiyaaa.ui.screens.SettingsScreen
import com.codeaza.bhaiyaaa.ui.screens.VipAlertSettingsScreen
import com.codeaza.bhaiyaaa.ui.screens.VipScreen
import com.codeaza.bhaiyaaa.ui.screens.VoiceRecordingsScreen

/**
 * The app shell.
 *
 * Three gates run before the main graph, in order: onboarding (first run), then
 * the privacy lock (if enabled), then the navigation host itself.
 */
@Composable
fun SukoonApp(
    viewModel: SukoonViewModel = viewModel(),
    assistantViewModel: AssistantViewModel = viewModel(),
    modelViewModel: ModelManagerViewModel = viewModel(),
    prayerViewModel: PrayerViewModel = viewModel(),
    recordingViewModel: VoiceRecordingViewModel = viewModel(),
    /** Set when a call-note prompt was tapped; opens that call once resolved. */
    pendingNoteNumber: String? = null,
    onPendingNoteHandled: () -> Unit = {}
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val settingsLoaded by viewModel.settingsLoaded.collectAsStateWithLifecycle()

    // Hold the splash colour rather than guessing. Rendering before settings
    // load meant onboarding appeared for a frame on every launch.
    if (!settingsLoaded) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    if (!settings.onboardingComplete) {
        OnboardingScreen(
            onFinished = { viewModel.setOnboardingComplete() },
            onPermissionsChanged = { viewModel.onPermissionsChanged() }
        )
        return
    }

    if (lockState == LockState.LOCKED) {
        PrivacyLockScreen(
            onUnlocked = { viewModel.onBiometricSuccess() },
            verifyPin = { pin -> viewModel.verifyPin(pin) }
        )
        return
    }

    MainScaffold(
        viewModel,
        assistantViewModel,
        modelViewModel,
        prayerViewModel,
        recordingViewModel,
        pendingNoteNumber,
        onPendingNoteHandled
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    viewModel: SukoonViewModel,
    assistantViewModel: AssistantViewModel,
    modelViewModel: ModelManagerViewModel,
    prayerViewModel: PrayerViewModel,
    recordingViewModel: VoiceRecordingViewModel,
    pendingNoteNumber: String?,
    onPendingNoteHandled: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }

    val message by viewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it.text)
            viewModel.consumeMessage()
        }
    }
    val prayerMessage by prayerViewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(prayerMessage) {
        prayerMessage?.let {
            snackbarHostState.showSnackbar(it)
            prayerViewModel.consumeMessage()
        }
    }
    val recordingMessage by recordingViewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(recordingMessage) {
        recordingMessage?.let {
            snackbarHostState.showSnackbar(it)
            recordingViewModel.consumeMessage()
        }
    }
    val modelMessage by modelViewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(modelMessage) {
        modelMessage?.let {
            snackbarHostState.showSnackbar(it)
            modelViewModel.consumeMessage()
        }
    }

    /**
     * Follows a tapped call-note prompt through to the call itself.
     *
     * Resolved here rather than in the notification because the call-log row
     * does not exist yet when the prompt is posted. If it still cannot be
     * found - the log was cleared, or the permission is not granted - the tap
     * simply opens the app, which is a better outcome than an error.
     */
    LaunchedEffect(pendingNoteNumber) {
        val number = pendingNoteNumber ?: return@LaunchedEffect
        viewModel.latestCallIdFor(number)?.let { navController.navigate(Routes.callDetail(it)) }
        onPendingNoteHandled()
    }

    val isTopLevel = currentRoute in BottomDestination.routes

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleFor(currentRoute)) },
                navigationIcon = {
                    if (!isTopLevel && currentRoute != null) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(com.codeaza.bhaiyaaa.R.string.cd_back)
                            )
                        }
                    }
                },
                actions = {
                    if (isTopLevel) {
                        IconButton(onClick = { navController.navigate(Routes.SEARCH) }) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = stringResource(com.codeaza.bhaiyaaa.R.string.action_search)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (isTopLevel) {
                NavigationBar {
                    BottomDestination.entries.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(destination.route) {
                                        // Single-top with state restoration: tapping a
                                        // tab returns to where you were in it, and never
                                        // stacks duplicate copies of a tab.
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon
                                    else destination.unselectedIcon,
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SukoonNavHost(
                navController,
                viewModel,
                assistantViewModel,
                modelViewModel,
                prayerViewModel,
                recordingViewModel
            )
        }
    }
}

@Composable
private fun SukoonNavHost(
    navController: NavHostController,
    viewModel: SukoonViewModel,
    assistantViewModel: AssistantViewModel,
    modelViewModel: ModelManagerViewModel,
    prayerViewModel: PrayerViewModel,
    recordingViewModel: VoiceRecordingViewModel
) {
    fun openContact(phoneNumber: String) =
        navController.navigate(Routes.contactDetail(phoneNumber))

    fun openCall(callId: Long) = navController.navigate(Routes.callDetail(callId))

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        // Two different moves, deliberately given two different motions.
        //
        // The bottom-bar tabs are peers, so switching between them only
        // crossfades - sliding would imply one sits inside the other, and the
        // direction would be a lie because there is no order to Home, Calls
        // and Contacts.
        //
        // Opening a detail or settings screen slides, because there the
        // hierarchy is real, and the direction is what tells you which way you
        // moved. The slide is a fifth of the width rather than the full
        // distance: a whole-screen slide at this duration reads as sluggish,
        // and the fade is doing most of the work anyway.
        enterTransition = {
            if (isLateral()) {
                fadeIn(tween(Motion.SCREEN_ENTER, easing = Motion.Standard))
            } else {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(Motion.SCREEN_ENTER, easing = Motion.Entering),
                    initialOffset = { it / 5 }
                ) + fadeIn(tween(Motion.SCREEN_ENTER, easing = Motion.Entering))
            }
        },
        exitTransition = {
            if (isLateral()) {
                fadeOut(tween(Motion.SCREEN_EXIT, easing = Motion.Standard))
            } else {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(Motion.SCREEN_EXIT, easing = Motion.Leaving),
                    targetOffset = { it / 5 }
                ) + fadeOut(tween(Motion.SCREEN_EXIT, easing = Motion.Leaving))
            }
        },
        popEnterTransition = {
            if (isLateral()) {
                fadeIn(tween(Motion.SCREEN_ENTER, easing = Motion.Standard))
            } else {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(Motion.SCREEN_ENTER, easing = Motion.Entering),
                    initialOffset = { it / 5 }
                ) + fadeIn(tween(Motion.SCREEN_ENTER, easing = Motion.Entering))
            }
        },
        popExitTransition = {
            if (isLateral()) {
                fadeOut(tween(Motion.SCREEN_EXIT, easing = Motion.Standard))
            } else {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(Motion.SCREEN_EXIT, easing = Motion.Leaving),
                    targetOffset = { it / 5 }
                ) + fadeOut(tween(Motion.SCREEN_EXIT, easing = Motion.Leaving))
            }
        }
    ) {

        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                prayerViewModel = prayerViewModel,
                onOpenPrayer = {
                    navController.navigate(Routes.prayerSettings(QuietTimesFocus.PRAYER_TIMES))
                },
                onOpenQibla = { navController.navigate(Routes.QIBLA) },
                onOpenCalls = { navController.navigate(Routes.CALLS) },
                onOpenVip = { navController.navigate(Routes.VIP) },
                onOpenInsights = { navController.navigate(Routes.INSIGHTS) },
                onOpenMemory = { navController.navigate(Routes.MEMORY) },
                onOpenContact = ::openContact
            )
        }

        composable(Routes.CALLS) {
            CallsScreen(viewModel = viewModel, onOpenCall = ::openCall)
        }

        composable(Routes.CONTACTS) {
            ContactsScreen(viewModel = viewModel, onOpenContact = ::openContact)
        }

        composable(Routes.ASSISTANT) {
            AssistantScreen(
                viewModel = assistantViewModel,
                onOpenModels = { navController.navigate(Routes.SETTINGS_MODELS) }
            )
        }

        composable(Routes.MORE) {
            MoreScreen(
                viewModel = viewModel,
                onOpenQibla = { navController.navigate(Routes.QIBLA) },
                onOpenVip = { navController.navigate(Routes.VIP) },
                onOpenMemory = { navController.navigate(Routes.MEMORY) },
                onOpenReminders = { navController.navigate(Routes.REMINDERS) },
                onOpenInsights = { navController.navigate(Routes.INSIGHTS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPrivacyCenter = { navController.navigate(Routes.PRIVACY_CENTER) }
            )
        }

        composable(Routes.VIP) {
            VipScreen(
                viewModel = viewModel,
                onOpenContact = ::openContact,
                onOpenAlertSettings = { navController.navigate(Routes.SETTINGS_VIP_ALERTS) }
            )
        }

        composable(Routes.MEMORY) { MemoryScreen(viewModel) }

        composable(Routes.REMINDERS) { RemindersScreen(viewModel) }

        composable(Routes.INSIGHTS) { InsightsScreen(viewModel) }

        composable(Routes.QIBLA) {
            QiblaScreen(
                viewModel = prayerViewModel,
                onOpenPrayerSettings = {
                    navController.navigate(Routes.prayerSettings(QuietTimesFocus.PRAYER_TIMES))
                }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                viewModel = viewModel,
                onOpenContact = ::openContact,
                onOpenCall = ::openCall
            )
        }

        composable(Routes.PRIVACY_CENTER) {
            PrivacyCenterScreen(
                viewModel = viewModel,
                onOpenData = { navController.navigate(Routes.SETTINGS_DATA) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onOpenNotifications = { navController.navigate(Routes.SETTINGS_NOTIFICATIONS) },
                onOpenVipAlerts = { navController.navigate(Routes.SETTINGS_VIP_ALERTS) },
                // No stated intent from a settings list: the screen falls back
                // to its own default order.
                onOpenPrayer = { navController.navigate(Routes.prayerSettings()) },
                onOpenAppearance = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                onOpenPersonality = { navController.navigate(Routes.SETTINGS_PERSONALITY) },
                onOpenSecurity = { navController.navigate(Routes.SETTINGS_SECURITY) },
                onOpenData = { navController.navigate(Routes.SETTINGS_DATA) },
                onOpenModels = { navController.navigate(Routes.SETTINGS_MODELS) },
                onOpenAbout = { navController.navigate(Routes.SETTINGS_ABOUT) }
            )
        }

        composable(Routes.SETTINGS_NOTIFICATIONS) { NotificationSettingsScreen(viewModel) }
        composable(Routes.SETTINGS_VIP_ALERTS) { VipAlertSettingsScreen(viewModel) }
        composable(Routes.SETTINGS_APPEARANCE) { AppearanceSettingsScreen(viewModel) }
        composable(Routes.SETTINGS_PERSONALITY) { PersonalitySettingsScreen(viewModel) }
        composable(Routes.SETTINGS_SECURITY) { SecuritySettingsScreen(viewModel) }
        composable(Routes.SETTINGS_DATA) { DataSettingsScreen(viewModel) }
        composable(
            route = Routes.SETTINGS_PRAYER,
            arguments = listOf(
                navArgument(Routes.ARG_FOCUS) {
                    type = NavType.StringType
                    // Defaulted rather than required: the plain route still
                    // resolves, and a caller with nothing to say about intent
                    // does not have to invent one.
                    defaultValue = QuietTimesFocus.NONE.name
                }
            )
        ) { entry ->
            PrayerSettingsScreen(
                viewModel = prayerViewModel,
                initialFocus = QuietTimesFocus.from(entry.arguments?.getString(Routes.ARG_FOCUS)),
                onOpenRecordings = { navController.navigate(Routes.SETTINGS_RECORDINGS) }
            )
        }
        composable(Routes.SETTINGS_RECORDINGS) {
            VoiceRecordingsScreen(viewModel = recordingViewModel)
        }
        composable(Routes.SETTINGS_MODELS) { ModelManagerScreen(modelViewModel) }
        composable(Routes.SETTINGS_ABOUT) {
            AboutScreen(onOpenLicences = { navController.navigate(Routes.SETTINGS_LICENCES) })
        }
        composable(Routes.SETTINGS_LICENCES) { LicencesScreen() }

        composable(
            route = Routes.CONTACT_DETAIL,
            arguments = listOf(navArgument(Routes.ARG_PHONE_NUMBER) { type = NavType.StringType })
        ) { entry ->
            val encoded = entry.arguments?.getString(Routes.ARG_PHONE_NUMBER).orEmpty()
            ContactDetailScreen(
                // Numbers contain '+' which the route encodes; decode it back
                // before it is used as a database key.
                phoneNumber = Uri.decode(encoded),
                viewModel = viewModel,
                onOpenCall = ::openCall
            )
        }

        composable(
            route = Routes.CALL_DETAIL,
            arguments = listOf(navArgument(Routes.ARG_CALL_ID) { type = NavType.LongType })
        ) { entry ->
            CallDetailScreen(
                callId = entry.arguments?.getLong(Routes.ARG_CALL_ID) ?: -1L,
                viewModel = viewModel,
                recordingViewModel = recordingViewModel,
                onOpenContact = ::openContact
            )
        }
    }
}

private fun titleFor(route: String?): String = when {
    route == null -> "Sukoon"
    route == Routes.HOME -> "Sukoon"
    route == Routes.CALLS -> "Calls"
    route == Routes.CONTACTS -> "Contacts"
    route == Routes.ASSISTANT -> "Assistant"
    route == Routes.MORE -> "More"
    route == Routes.VIP -> "VIP contacts"
    route == Routes.MEMORY -> "Memory"
    route == Routes.REMINDERS -> "Reminders"
    route == Routes.QIBLA -> "Qibla"
    route == Routes.INSIGHTS -> "Insights"
    route == Routes.SEARCH -> "Search"
    route == Routes.PRIVACY_CENTER -> "Privacy Center"
    route == Routes.SETTINGS -> "Settings"
    route == Routes.SETTINGS_NOTIFICATIONS -> "Notifications"
    route == Routes.SETTINGS_VIP_ALERTS -> "VIP alerts"
    route == Routes.SETTINGS_APPEARANCE -> "Appearance"
    route == Routes.SETTINGS_PERSONALITY -> "Personality"
    route == Routes.SETTINGS_SECURITY -> "Security"
    route == Routes.SETTINGS_DATA -> "Data"
    route.startsWith("settings/prayer") -> "Quiet times"
    route == Routes.SETTINGS_RECORDINGS -> "Recordings"
    route == Routes.SETTINGS_MODELS -> "AI models"
    route == Routes.SETTINGS_ABOUT -> "About"
    route == Routes.SETTINGS_LICENCES -> "Licences"
    route.startsWith("contact/") -> "Contact"
    route.startsWith("call/") -> "Call"
    else -> "Sukoon"
}

/** Reads the two ends of the move off the transition scope for [NavMotion]. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isLateral(): Boolean =
    NavMotion.isLateral(initialState.destination.route, targetState.destination.route)
