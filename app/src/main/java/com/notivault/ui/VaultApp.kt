package com.notivault.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings as SettingsIcon
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import com.notivault.ui.motion.LocalNavAnimatedScope
import com.notivault.ui.motion.LocalSharedTransitionScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.notivault.data.prefs.Settings as VaultSettings
import com.notivault.ui.apps.AppsScreen
import com.notivault.ui.conversation.ConversationScreen
import com.notivault.ui.conversation.ConversationsScreen
import com.notivault.ui.detail.MessageScreen
import com.notivault.ui.lock.LockScreen
import com.notivault.ui.onboarding.OnboardingScreen
import com.notivault.ui.onboarding.PinSetup
import com.notivault.ui.search.SearchScreen
import com.notivault.ui.settings.SettingsScreen
import com.notivault.ui.theme.Motion
import com.notivault.ui.theme.NotiVaultTheme
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.notivault.data.prefs.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import javax.inject.Inject

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Timeline("timeline", "Vault", Icons.Outlined.Inbox),
    Chats("chats", "Chats", Icons.AutoMirrored.Outlined.Chat),
    Search("search", "Search", Icons.Outlined.Search),
    Apps("apps", "Apps", Icons.Outlined.Apps),
    Settings("settings", "Settings", Icons.Outlined.SettingsIcon),
}

private enum class Gate { Loading, Onboarding, Locked, Open }

@Composable
fun VaultApp(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val s = settings
    NotiVaultTheme(mode = s?.themeMode ?: com.notivault.data.prefs.ThemeMode.System, dynamicColor = s?.dynamicColor == true) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val gate = when {
                s == null -> Gate.Loading
                !s.onboardingDone -> Gate.Onboarding
                s.lockEnabled && locked -> Gate.Locked
                else -> Gate.Open
            }
            AnimatedContent(gate, transitionSpec = { fadeIn(tween(Motion.standard)) togetherWith fadeOut(tween(Motion.standard)) }, label = "gate") { g ->
                when (g) {
                    Gate.Loading -> Box(Modifier.fillMaxSize())
                    Gate.Onboarding -> OnboardingScreen()
                    Gate.Locked -> LockScreen(biometricEnabled = s?.biometricEnabled == true)
                    Gate.Open -> MainShell(vm, s ?: VaultSettings())
                }
            }
        }
    }
}

@HiltViewModel
class PinChangeViewModel @Inject constructor(val store: SettingsStore) : ViewModel()

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MainShell(vm: MainViewModel, settings: VaultSettings) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val listenerOn by vm.listenerEnabled.collectAsStateWithLifecycle()
    val deepLink by vm.deepLinkMessage.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = Tab.entries.any { it.route == route }
    val openListenerSettings = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

    LaunchedEffect(deepLink) {
        deepLink?.let { nav.navigate("message/$it"); vm.openMessage(null) }
    }

    Scaffold(
        bottomBar = { if (showBar) BottomBar(nav, route) },
        containerColor = MaterialTheme.colorScheme.background,
        // Detail screens own a Scaffold with a TopAppBar; tab screens pad for the status bar themselves.
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
        NavHost(
            nav, startDestination = Tab.Timeline.route, modifier = Modifier.padding(pad),
            enterTransition = { slideInHorizontally(tween(Motion.emphasized, easing = Motion.EmphasizedEasing)) { it / 6 } + fadeIn(tween(Motion.standard)) },
            exitTransition = { fadeOut(tween(Motion.standard)) },
            popEnterTransition = { fadeIn(tween(Motion.standard)) },
            popExitTransition = { slideOutHorizontally(tween(Motion.standard)) { it / 6 } + fadeOut(tween(Motion.standard)) },
        ) {
            screen(Tab.Timeline.route) {
                TabPage { TimelineScreenEntry(listenerOn, openListenerSettings) { nav.navigate("message/$it") } }
            }
            screen(Tab.Chats.route) { TabPage { ConversationsScreen(onOpen = { nav.navigate("conversation/$it") }) } }
            screen(Tab.Search.route) { TabPage { SearchScreen(onOpenMessage = { nav.navigate("message/$it") }) } }
            screen(Tab.Apps.route) { TabPage { AppsScreen() } }
            screen(Tab.Settings.route) { TabPage { SettingsScreen(listenerOn, onSetPin = { nav.navigate("setpin") }) } }
            screen("conversation/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                ConversationScreen(onBack = { nav.popBackStack() }, onOpenMessage = { nav.navigate("message/$it") })
            }
            screen("message/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                MessageScreen(onBack = { nav.popBackStack() }, onOpenConversation = { nav.navigate("conversation/$it") })
            }
            screen("setpin") {
                val pvm: PinChangeViewModel = hiltViewModel()
                val scope = rememberCoroutineScope()
                Box(Modifier.systemBarsPadding()) {
                    PinSetup(allowSkip = false, onDone = { pin ->
                        if (pin != null) scope.launch { pvm.store.setPin(pin); nav.popBackStack() } else nav.popBackStack()
                    })
                }
            }
        }
        }
        }
    }
}

/** Every destination exposes its AnimatedContentScope so shared-element morphs can find their partner. */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) }
}

@Composable
private fun TabPage(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize().statusBarsPadding()) { content() }

@Composable
private fun TimelineScreenEntry(listenerOn: Boolean, onFix: () -> Unit, onOpen: (Long) -> Unit) =
    com.notivault.ui.timeline.TimelineScreen(listenerOn = listenerOn, onFixListener = onFix, onOpenMessage = onOpen)

@Composable
private fun BottomBar(nav: NavHostController, route: String?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Tab.entries.forEach { t ->
            NavigationBarItem(
                selected = route == t.route,
                onClick = {
                    nav.navigate(t.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(t.icon, null) },
                label = { Text(t.label) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
            )
        }
    }
}
