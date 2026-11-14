package nl.tufein.openconnect.ui

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import nl.tufein.openconnect.BuildConfig
import nl.tufein.openconnect.R

/** [compact] is the label when six tabs share the bar (with the Oura tab), so long Dutch words still fit. */
private enum class Tab(val route: String, val label: Int, val icon: ImageVector, val compact: Int = label) {
    Home("home", R.string.tab_home, Icons.Filled.Home),
    Activities("activities", R.string.tab_activities, Icons.Filled.LocationOn, R.string.tab_activities_compact),
    Health("health", R.string.tab_health, Icons.Filled.Favorite, R.string.tab_health_compact),
    Insights("insights", R.string.tab_insights, Icons.Filled.Star),
    /** Only for someone who chose the Oura Ring. */
    Oura(OURA_ROUTE, R.string.tab_oura, RingIcon),
    Settings("settings", R.string.tab_settings, Icons.Filled.Settings, R.string.tab_settings_compact),
}

private const val DETAIL = "activity/{id}"

/** A plain ring for the Oura tab; the standard icon set has none. */
private val RingIcon: ImageVector = ImageVector.Builder("Ring", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black), pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd) {
        moveTo(12f, 3f)
        arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 11.99f, y1 = 3f)
        close()
        moveTo(12f, 7f)
        arcTo(5f, 5f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 12.01f, y1 = 7f)
        close()
    }
}.build()

@Composable
fun OpenConnectRoot(
    vm: AppViewModel,
    onImport: () -> Unit,
    onExport: (activityId: String, format: String) -> Unit,
    onExportCsv: (kind: String) -> Unit,
    onBackup: () -> Unit = {},
    onRestore: () -> Unit = {},
    /** Picks a watch face file (.prg) to copy to the watch over USB. */
    onWatchFace: () -> Unit = {},
    /** Whether the phone has a screen lock or fingerprint the app lock can use. */
    lockAvailable: Boolean = false,
    /** Switches the lock on (after the person passed the check) or off. */
    onAppLock: (Boolean) -> Unit = {},
) {
    val sources by vm.sources.collectAsState()
    if (!sources.onboarded) {
        val firstLook by vm.look.collectAsState()
        OnboardingScreen(sources, vm::setSources, firstLook.design, vm::setDesign)
        return
    }
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(vm) {
        vm.navigation.collect { target ->
            nav.navigate(target) { launchSingleTop = true }
        }
    }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabs = Tab.entries.filter { it != Tab.Oura || sources.oura }
    val showBar = tabs.any { it.route == route }

    val home by vm.home.collectAsState()
    val activities by vm.activities.collectAsState()
    val detail by vm.detail.collectAsState()
    val health by vm.health.collectAsState()
    val reports by vm.reports.collectAsState()
    val overview by vm.overview.collectAsState()
    val nudges by vm.nudges.collectAsState()
    val plan by vm.plan.collectAsState()
    val lock by vm.lock.collectAsState()
    val healthConnect by vm.healthConnect.collectAsState()
    val syncLog by vm.syncLog.collectAsState()
    val trophies by vm.trophies.collectAsState()
    val coach by vm.coach.collectAsState()
    val ai by vm.ai.collectAsState()
    val garmin by vm.garmin.collectAsState()
    val oura by vm.oura.collectAsState()
    val theme by vm.theme.collectAsState()
    val look by vm.look.collectAsState()
    val watchFace by vm.watchFace.collectAsState()
    val goals by vm.goals.collectAsState()
    val now by vm.now.collectAsState()
    val busy by vm.busy.collectAsState()
    val watch by vm.watch.collectAsState()
    var insightsTab by rememberSaveable { mutableStateOf(InsightsTab.Week) }
    var healthFocus by remember { mutableStateOf<nl.tufein.openconnect.core.ChartMetric?>(null) }

    fun openTab(tab: Tab) {
        nav.navigate(tab.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openInsights(which: InsightsTab) {
        insightsTab = which
        openTab(Tab.Insights)
    }

    fun openWatch() {
        vm.refreshWatches()
        nav.navigate(WATCH_ROUTE) { launchSingleTop = true }
    }

    fun openGarmin() {
        nav.navigate(GARMIN_ROUTE) { launchSingleTop = true }
    }

    fun openHealth(metric: nl.tufein.openconnect.core.ChartMetric) {
        healthFocus = metric
        openTab(Tab.Health)
    }

    fun openOura() = openTab(Tab.Oura)

    fun openActivity(id: String) {
        vm.openActivity(id)
        nav.navigate("activity/" + Uri.encode(id))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) {
                // Each design marks the open tab its own way: a pill (Clean), only the colour (Dashboard) or the arcade block (Retro).
                val design = LocalDesign.current
                NavigationBar(containerColor = if (design == Design.DASHBOARD) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceContainer) {
                    for (tab in tabs) {
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { openTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            // Plain font and a little smaller than the monospace headings: "Activiteiten" and "Instellingen"
                            // must fit a fifth of a narrow phone without being cut off.
                            label = {
                                Text(
                                    stringResource(if (tabs.size > 5) tab.compact else tab.label),
                                    maxLines = 1,
                                    softWrap = false,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Default, fontSize = 11.sp),
                                )
                            },
                            colors = if (design == Design.DASHBOARD) NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            ) else NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.Home.route) {
            composable(Tab.Home.route) {
                HomeScreen(
                    state = home,
                    garmin = garmin,
                    now = now,
                    onOpenActivity = ::openActivity,
                    onDemo = vm::loadDemo,
                    onImport = onImport,
                    onWatch = ::openWatch,
                    onGarmin = ::openGarmin,
                    onSync = vm::syncGarmin,
                    onTrophies = { openInsights(InsightsTab.Trophies) },
                    onCoach = { openInsights(InsightsTab.Coach) },
                    contentPadding = padding,
                    onOpenHealth = ::openHealth,
                    onOpenActivities = { openTab(Tab.Activities) },
                    onOpenInsights = { openInsights(InsightsTab.Week) },
                    useGarmin = sources.garmin,
                    onOura = if (sources.oura) ::openOura else null,
                )
            }
            composable(Tab.Activities.route) {
                ActivitiesScreen(activities, vm::loadActivities, ::openActivity, vm::loadDemo, onImport, padding)
            }
            composable(Tab.Health.route) { HealthScreen(health, vm::loadDemo, onImport, padding, focus = healthFocus, onFocusDone = { healthFocus = null }) }
            composable(Tab.Insights.route) {
                InsightsScreen(
                    tab = insightsTab,
                    onTab = { insightsTab = it },
                    reports = reports,
                    busy = busy,
                    active = ai.active,
                    trophies = trophies,
                    coach = coach,
                    plan = plan,
                    onCreatePlan = vm::createPlan,
                    onDeletePlan = vm::deletePlan,
                    onRace = vm::raceAction,
                    onSendWorkouts = vm::sendPlanToWatch,
                    onShiftWeek = vm::shiftWeek,
                    onPeriod = vm::setReportPeriod,
                    onGenerate = vm::generateWeeklyReport,
                    onOpenActivity = ::openActivity,
                    onAsk = vm::askCoach,
                    onClearCoach = vm::clearCoach,
                    onOpenAiSettings = { openTab(Tab.Settings) },
                    contentPadding = padding,
                    onOpenHealth = ::openHealth,
                    onOpenActivities = { openTab(Tab.Activities) },
                )
            }
            composable(Tab.Settings.route) {
                SettingsScreen(
                    ui = SettingsUi(ai = ai, theme = theme, versionName = BuildConfig.VERSION_NAME, goals = goals, overview = overview, syncLog = syncLog, nudges = nudges, appLock = lock.enabled, lockAvailable = lockAvailable, sources = sources, look = look, watchFace = watchFace, watchData = watchFaceData(home, now)),
                    onLook = vm::setLook,
                    onDesign = vm::setDesign,
                    onWatchFaceLook = vm::setWatchFace,
                    onWatchFace = onWatchFace,
                    onSources = vm::setSources,
                    onAppLock = onAppLock,
                    onNudges = vm::setNudges,
                    health = healthConnect,
                    onHealthChecked = vm::refreshHealth,
                    onHealthExport = vm::exportHealth,
                    onHealthImport = vm::importHealth,
                    onHealthAuto = vm::setHealthAuto,
                    onBackup = onBackup,
                    onRestore = onRestore,
                    onClearLog = vm::clearSyncLog,
                    onTheme = vm::setTheme,
                    onGoals = vm::setGoals,
                    onLanguage = vm::setAiLanguage,
                    onChoice = vm::setAiChoice,
                    onSaveKey = vm::saveAiKey,
                    onCheckKey = vm::checkAiKey,
                    onForgetKey = vm::forgetAiKey,
                    onModel = vm::setAiModel,
                    onDemo = vm::loadDemo,
                    onImport = onImport,
                    onExportDaily = { onExportCsv(CSV_DAILY) },
                    onExportActivities = { onExportCsv(CSV_ACTIVITIES) },
                    onClear = vm::clearAll,
                    onWatch = ::openWatch,
                    onGarmin = ::openGarmin,
                    onOura = ::openOura,
                    contentPadding = padding,
                )
            }
            composable(Tab.Oura.route) {
                OuraScreen(
                    ui = oura,
                    onSaveClient = vm::saveOuraClient,
                    loginUrl = vm::ouraLoginUrl,
                    onPasteRedirect = vm::finishOuraLogin,
                    onSync = vm::syncOura,
                    onDisconnect = vm::disconnectOura,
                    onForget = vm::forgetOura,
                    contentPadding = padding,
                )
            }
            composable(GARMIN_ROUTE) {
                GarminScreen(
                    ui = garmin,
                    onBack = { nav.popBackStack() },
                    onSaveKey = vm::saveGarminKey,
                    onForgetKey = vm::forgetGarminKey,
                    onDays = vm::setGarminDays,
                    onAutoSync = vm::setGarminAutoSync,
                    onSync = vm::syncGarmin,
                    onCancel = vm::cancelGarmin,
                    contentPadding = padding,
                )
            }
            composable(WATCH_ROUTE) {
                // The list of watches goes stale when the cable moves, so look again whenever the screen opens.
                LaunchedEffect(Unit) { vm.refreshWatches() }
                // Leaving the screen, by the back button or the arrow, ends a live heart-rate connection.
                androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.stopLive() } }
                WatchScreen(
                    ui = watch,
                    onBack = { nav.popBackStack() },
                    onSearchUsb = vm::refreshWatches,
                    onRead = vm::readWatch,
                    onScan = vm::scanSensors,
                    onConnect = vm::connectSensor,
                    onStopLive = vm::stopLive,
                    onSaveLive = vm::saveLive,
                    onReadFolder = vm::readFolder,
                    onFindGarmin = vm::findGarmin,
                    onStopFindGarmin = vm::stopFindGarmin,
                    onSyncGarmin = vm::syncGarmin,
                    onForgetGarmin = vm::forgetGarmin,
                    onGarminArchive = vm::setGarminArchive,
                    onGarminBackground = vm::setGarminBackground,
                    contentPadding = padding,
                )
            }
            composable(DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { backStack ->
                val id = backStack.arguments?.getString("id").orEmpty()
                ActivityDetailScreen(
                    state = detail,
                    busy = busy,
                    active = ai.active,
                    onBack = { nav.popBackStack() },
                    onGenerate = vm::generateActivityReport,
                    onExport = { onExport(id, it) },
                    onDelete = { vm.deleteActivity(id); nav.popBackStack() },
                    contentPadding = padding,
                )
            }
        }
    }
}

/** The numbers the watch face preview shows: today's, from the same data as Home. */
private fun watchFaceData(home: HomeState, now: java.time.Instant, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): WatchFaceData {
    val today = home.overview?.today
    val local = now.atZone(zone)
    return WatchFaceData(
        time = String.format(java.util.Locale.ROOT, "%02d:%02d", local.hour, local.minute),
        date = Fmt.shortDate(local.toLocalDate()),
        battery = today?.bodyBatteryHigh,
        heart = today?.restingHr?.takeIf { it > 0 },
        stress = today?.avgStress,
        floors = today?.floorsClimbed,
        steps = today?.steps,
        stepsGoal = (today?.stepsGoal ?: 10_000),
    )
}
