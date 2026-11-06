package com.budgetsms.app.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.budgetsms.app.AppViewModel
import com.budgetsms.app.R
import com.budgetsms.app.ui.icons.AlertBell
import com.budgetsms.app.ui.icons.AnimatedCheck
import com.budgetsms.app.ui.icons.AnimatedNavIcon
import com.budgetsms.app.ui.icons.AppIcons
import com.budgetsms.app.ui.icons.SyncIcon
import com.budgetsms.app.ui.screens.*
import com.budgetsms.app.ui.theme.LocalMotion
import com.budgetsms.app.ui.theme.appBackground
import androidx.compose.ui.graphics.Color
import com.budgetsms.data.SettingKeys
import com.budgetsms.domain.appearance.AnimationLevel
import com.budgetsms.domain.appearance.AppearancePreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Tab(val route: String, val label: Int) {
    HOME("home", R.string.tab_home), ACTIVITY("activity", R.string.tab_activity), BUDGET("budget", R.string.tab_budget),
    GOALS("goals", R.string.tab_goals), MORE("more", R.string.tab_more);
    val icons get() = when (this) { HOME -> AppIcons.home; ACTIVITY -> AppIcons.activity; BUDGET -> AppIcons.budget; GOALS -> AppIcons.goals; MORE -> AppIcons.more }
}

@Composable
fun AppRoot(vm: AppViewModel, pref: AppearancePreference) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    if (snapshot.ledger == null) { Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }; return }
    when {
        locked -> LockScreen(vm)
        snapshot.settings[SettingKeys.ONBOARDING_DONE] != "true" -> OnboardingScreen(vm)
        else -> MainScaffold(vm, pref)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(vm: AppViewModel, pref: AppearancePreference) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: Tab.HOME.route
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.messages.collect { m -> snackbar.showSnackbar(if (m.arg != null) ctx.getString(m.res, m.arg) else ctx.getString(m.res)) } }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val isTab = Tab.entries.any { it.route == route }
    val alertCount = snapshot.ledger?.alerts?.size ?: 0
    val reviewCount = snapshot.ledger?.reviews?.map { it.eventId }?.distinct()?.size ?: 0

    val translucent = com.budgetsms.app.ui.theme.LocalGradients.current.background != com.budgetsms.domain.appearance.BackgroundStyle.PLAIN
    Row(Modifier.fillMaxSize().appBackground()) {
        if (wide) NavigationRail(Modifier.fillMaxHeight(), containerColor = if (translucent) Color.Transparent else MaterialTheme.colorScheme.surface) {
            Spacer(Modifier.height(24.dp))
            Tab.entries.forEach { t ->
                NavigationRailItem(selected = route == t.route, onClick = { nav.goTab(t) },
                    icon = { BadgedIcon(t, route == t.route, if (t == Tab.ACTIVITY) reviewCount else 0) }, label = { Text(stringResource(t.label)) })
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(titleFor(route))) },
                    navigationIcon = { if (!isTab) IconButton(onClick = { nav.popBackStack() }) { Icon(AppIcons.back, stringResource(R.string.back)) } },
                    actions = {
                        IconButton(onClick = { vm.syncDevice() }) { SyncIcon(busy, stringResource(R.string.sync_now)) }
                        IconButton(onClick = { nav.navigate("home") }) { AlertBell(alertCount, stringResource(R.string.alerts)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = if (translucent) Color.Transparent else MaterialTheme.colorScheme.surface),
                )
            },
            bottomBar = {
                if (!wide) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (translucent) 0.86f else 1f)) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(selected = route == t.route, onClick = { nav.goTab(t) },
                            icon = { BadgedIcon(t, route == t.route, if (t == Tab.ACTIVITY) reviewCount else 0) },
                            label = { Text(stringResource(t.label), maxLines = 1) })
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                AppNavHost(nav, vm, pref)
                SuccessOverlay(vm)
            }
        }
    }
}

@Composable
private fun BadgedIcon(t: Tab, selected: Boolean, badge: Int) {
    BadgedBox(badge = { if (badge > 0) Badge { Text(badge.toString()) } }) { AnimatedNavIcon(selected, t.icons, null) }
}

private fun NavHostController.goTab(t: Tab) = navigate(t.route) {
    popUpTo(graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true
}

private fun titleFor(route: String): Int = when {
    route == "home" -> R.string.app_name; route == "activity" -> R.string.tab_activity; route == "budget" -> R.string.tab_budget
    route == "goals" -> R.string.tab_goals; route == "more" -> R.string.tab_more; route.startsWith("event") -> R.string.transaction_details
    route == "obligations" -> R.string.obligations; route == "forecast" -> R.string.forecast; route == "reports" -> R.string.reports
    route == "appearance" -> R.string.appearance; route == "security" -> R.string.security; route == "senders" -> R.string.senders
    route == "profiles" -> R.string.profiles_accounts; route == "data" -> R.string.data_backup; route == "language" -> R.string.language
    route == "audit" -> R.string.audit_log; route == "transfers" -> R.string.transfers
    else -> R.string.app_name
}

@Composable
private fun AppNavHost(nav: NavHostController, vm: AppViewModel, pref: AppearancePreference) {
    val motion = LocalMotion.current
    val dur = if (motion == AnimationLevel.FULL) 260 else 0
    NavHost(nav, startDestination = "home",
        enterTransition = { if (dur == 0) EnterTransition.None else fadeIn(tween(dur)) + slideInHorizontally(tween(dur)) { it / 12 } },
        exitTransition = { if (dur == 0) ExitTransition.None else fadeOut(tween(dur / 2)) },
        popEnterTransition = { if (dur == 0) EnterTransition.None else fadeIn(tween(dur)) },
        popExitTransition = { if (dur == 0) ExitTransition.None else fadeOut(tween(dur / 2)) + slideOutHorizontally(tween(dur)) { it / 12 } },
    ) {
        composable("home") { HomeScreen(vm, onOpen = { nav.navigate(it) }) }
        composable("activity") { ActivityScreen(vm, onOpenEvent = { nav.navigate("event/$it") }) }
        composable("event/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
            EventDetailScreen(vm, e.arguments?.getString("id").orEmpty(), onDone = { nav.popBackStack() })
        }
        composable("budget") { BudgetScreen(vm) }
        composable("goals") { GoalsScreen(vm, onOpen = { nav.navigate(it) }) }
        composable("more") { MoreScreen(onOpen = { nav.navigate(it) }) }
        composable("obligations") { ObligationsScreen(vm) }
        composable("forecast") { ForecastScreen(vm) }
        composable("reports") { ReportsScreen(vm) }
        composable("transfers") { TransfersScreen(vm) }
        composable("appearance") { AppearanceScreen(vm, pref) }
        composable("language") { LanguageScreen(vm, pref) }
        composable("security") { SecurityScreen(vm, pref) }
        composable("senders") { SendersScreen(vm) }
        composable("profiles") { ProfilesScreen(vm) }
        composable("data") { DataScreen(vm) }
        composable("audit") { AuditScreen(vm) }
    }
}

/** Brief animated check after a successful confirmation (static when motion is off). */
@Composable
private fun BoxScope.SuccessOverlay(vm: AppViewModel) {
    val n by vm.success.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(n) { if (n > 0) { visible = true; delay(1100); visible = false } }
    AnimatedVisibility(visible, Modifier.align(Alignment.Center), enter = scaleIn() + fadeIn(), exit = fadeOut()) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp, shadowElevation = 8.dp) {
            AnimatedCheck(Modifier.padding(20.dp), 72.dp, play = visible)
        }
    }
}

@Composable
fun LockScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val bio = snapshot.settings[SettingKeys.BIOMETRIC] == "true"
    val title = stringResource(R.string.unlock_title)
    fun prompt() {
        val act = ctx as? FragmentActivity ?: return
        val can = BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) return
        BiometricPrompt(act, ContextCompat.getMainExecutor(ctx), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { vm.unlock() }
        }).authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title).setNegativeButtonText(ctx.getString(R.string.use_pin))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).build())
    }
    LaunchedEffect(bio) { if (bio) prompt() }
    Surface(Modifier.fillMaxSize().appBackground(), color = Color.Transparent) {
        Column(Modifier.fillMaxSize().padding(32.dp).imePadding(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(AppIcons.lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8); error = false }, label = { Text(stringResource(R.string.pin)) },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = error, supportingText = { if (error) Text(stringResource(R.string.pin_wrong)) }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { scope.launch { if (vm.verifyPin(pin)) vm.unlock() else { error = true; pin = ""; delay(400) } } }, enabled = pin.length >= 4) {
                Text(stringResource(R.string.unlock))
            }
            if (bio) TextButton(onClick = { prompt() }) { Icon(AppIcons.fingerprint, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.use_biometric)) }
        }
    }
}
