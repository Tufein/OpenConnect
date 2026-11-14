package nl.tufein.openconnect.ui

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
private enum class Tab(val route: String, val label: Int, val icon: PixelArt, val compact: Int = label) {
    Home("home", R.string.tab_home, PixelIcons.Home),
    Activities("activities", R.string.tab_activities, PixelIcons.Stopwatch, R.string.tab_activities_compact),
    Health("health", R.string.tab_health, PixelIcons.Heart, R.string.tab_health_compact),
    Insights("insights", R.string.tab_insights, PixelIcons.Star),
    /** Only for someone who chose the Oura Ring. */
    Oura(OURA_ROUTE, R.string.tab_oura, PixelIcons.Ring),
    Settings("settings", R.string.tab_settings, PixelIcons.Sliders, R.string.tab_settings_compact),
}

private const val DETAIL = "activity/{id}"

/**
 * The tab bar: a flat strip under a 2 dp ink line. The open tab is a block filled with ink and its icon and word in the
 * background colour, like the highlighted entry in the menu of a game; the other tabs are plain.
 */
@Composable
private fun HandheldBar(tabs: List<Tab>, route: String?, onOpen: (Tab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ink = inkColor()
    val shape = MaterialTheme.shapes.small
    // One label size for the whole bar: it starts at 17sp and steps down until the longest word fits its tab, so no
    // language, phone width or font scale ever cuts a label off (and the labels never differ in size).
    val labels = tabs.map { stringResource(if (tabs.size > 5) it.compact else it.label) }
    var labelSize by remember(labels) { mutableFloatStateOf(17f) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .drawBehind { drawLine(ink, Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), strokeWidth = 2.dp.toPx()) }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for ((index, tab) in tabs.withIndex()) {
            val selected = route == tab.route
            val content = if (selected) colors.background else colors.onSurfaceVariant
            Column(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (selected) colors.onBackground else Color.Transparent, shape)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onOpen(tab) })
                    .padding(horizontal = 1.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                PixelIcon(tab.icon, size = 24.dp, tint = content)
                Text(
                    labels[index],
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    color = content,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = labelSize.sp, lineHeight = 18.sp),
                    onTextLayout = { if (it.didOverflowWidth && labelSize > 10f) labelSize -= 0.5f },
                )
            }
        }
    }
}

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
        OnboardingScreen(sources, vm::setSources)
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
    val zones by vm.zones.collectAsState()
    val gear by vm.gear.collectAsState()
    val calendar by vm.calendar.collectAsState()
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
        bottomBar = { if (showBar) HandheldBar(tabs, route, ::openTab) },
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
                    zones = zones,
                    onZones = vm::zonesAction,
                    gear = gear,
                    onGear = vm::gearAction,
                    calendar = calendar,
                    onShiftCalendar = vm::shiftCalendar,
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
                    onShoe = { vm.gearAction(GearAction.Assign(id, it)) },
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
