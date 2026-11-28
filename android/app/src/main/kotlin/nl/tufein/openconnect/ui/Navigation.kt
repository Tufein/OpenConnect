package nl.tufein.openconnect.ui

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
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

/** [compact] is the label when more than five tabs share the bar (with the Oura tab), so long Dutch words still fit. */
private enum class Tab(val route: String, val label: Int, val icon: LineGlyph, val compact: Int = label) {
    Home("home", R.string.tab_home, LineGlyph.Home),
    Activities("activities", R.string.tab_activities, LineGlyph.Activities, R.string.tab_activities_compact),
    Health("health", R.string.tab_health, LineGlyph.Health, R.string.tab_health_compact),
    Insights("insights", R.string.tab_insights, LineGlyph.Insights),
    /** Only for someone who chose the Oura Ring. */
    Oura(OURA_ROUTE, R.string.tab_oura, LineGlyph.Ring),

    Settings("settings", R.string.tab_settings, LineGlyph.Sliders, R.string.tab_settings_compact),
}

private const val DETAIL = "activity/{id}"

/** The privacy page: every subject of the register, with a detail page per subject. */
const val PRIVACY_ROUTE = "privacy"

/**
 * The retro tab bar: a flat strip under a 2 dp ink line. The open tab is a block filled with ink and its icon and word
 * in the background colour, like the highlighted entry in the menu of a game; the other tabs are plain.
 */
@Composable
private fun RetroBar(tabs: List<Tab>, route: String?, onOpen: (Tab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ink = inkColor()
    val shape = MaterialTheme.shapes.small
    // One label size for the whole bar: it starts at 17sp (14sp in clear letters) and steps down until the longest word fits its tab, so no
    // language, phone width or font scale ever cuts a label off (and the labels never differ in size).
    val labels = tabs.map { stringResource(if (tabs.size > 5) it.compact else it.label) }
    val letters = LocalLetters.current
    var labelSize by remember(labels, letters) { mutableFloatStateOf(if (letters == Letters.CLEAR) 14f else 17f) }
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
                    .semantics(mergeDescendants = true) {}
                    .padding(horizontal = 1.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                PixelIcon(tab.icon.pixelArt(), size = 24.dp, tint = content)
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

/**
 * The modern tab bar: one softly lifted strip with a small rounded marker under the open tab and smooth line icons, so
 * the bottom of the screen looks like a current Android app rather than a game menu. Labels, roles and the auto-shrink
 * rule are the same as in [RetroBar], so Dutch words fit at any font scale and TalkBack still hears a tab.
 */
@Composable
private fun ModernBar(tabs: List<Tab>, route: String?, onOpen: (Tab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val labels = tabs.map { stringResource(if (tabs.size > 5) it.compact else it.label) }
    val letters = LocalLetters.current
    var labelSize by remember(labels, letters) { mutableFloatStateOf(13f) }
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .drawBehind { drawLine(colors.outlineVariant, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for ((index, tab) in tabs.withIndex()) {
                val selected = route == tab.route
                val content by animateColorAsState(
                    if (selected) colors.primary else colors.onSurfaceVariant,
                    tween(200),
                    label = "tabContent",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onOpen(tab) })
                    .semantics(mergeDescendants = true) {}
                        .padding(horizontal = 1.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    LineIcon(tab.icon, size = 24.dp, tint = content)
                    Text(
                        labels[index],
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        color = content,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = labelSize.sp, lineHeight = 16.sp),
                        onTextLayout = { if (it.didOverflowWidth && labelSize > 10f) labelSize -= 0.5f },
                    )
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .width(18.dp)
                            .height(2.dp)
                            .clip(PillShape)
                            .background(if (selected) colors.primary else Color.Transparent),
                    )
                }
            }
        }
    }
}

/**
 * The editorial tab bar: a floating pill with a hairline and a filled sage pill behind the open tab. The icons are
 * smooth line drawings at 24 dp, not pixel art, so the bottom of the screen reads as a quiet modern app. It keeps the
 * labels and the auto-shrink rule of the other bars, so every translation fits and every tab stays at least 56 dp high.
 */
@Composable
private fun EditorialBar(tabs: List<Tab>, route: String?, onOpen: (Tab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val labels = tabs.map { stringResource(if (tabs.size > 5) it.compact else it.label) }
    var labelSize by remember(labels) { mutableFloatStateOf(11f) }
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(8.dp, PillShape)
                .clip(PillShape)
                .background(colors.surface)
                .border(1.dp, colors.outlineVariant, PillShape)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for ((index, tab) in tabs.withIndex()) {
                val selected = route == tab.route
                val fill by animateColorAsState(
                    if (selected) colors.primary else Color.Transparent,
                    tween(220),
                    label = "tabFill",
                )
                val content by animateColorAsState(
                    if (selected) colors.onPrimary else colors.onSurfaceVariant,
                    tween(220),
                    label = "tabContent",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clip(PillShape)
                        .background(fill, PillShape)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onOpen(tab) })
                    .semantics(mergeDescendants = true) {}
                        .padding(horizontal = 2.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    LineIcon(tab.icon, size = 24.dp, tint = content)
                    Text(
                        labels[index],
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        color = content,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = labelSize.sp, lineHeight = 15.sp, letterSpacing = 0.2.sp),
                        onTextLayout = { if (it.didOverflowWidth && labelSize > 9f) labelSize -= 0.5f },
                    )
                }
            }
        }
    }
}

/** The style follows the same choice as the rest of the app. */
@Composable
private fun TabBar(tabs: List<Tab>, route: String?, onOpen: (Tab) -> Unit) {
    when (LocalStyle.current) {
        Style.RETRO -> RetroBar(tabs, route, onOpen)
        Style.EDITORIAL -> EditorialBar(tabs, route, onOpen)
        else -> ModernBar(tabs, route, onOpen)
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
    onExportAll: () -> Unit = {},
    /** Whether the phone has a screen lock or fingerprint the app lock can use. */
    lockAvailable: Boolean = false,
    /** Switches the lock on (after the person passed the check) or off. */
    onAppLock: (Boolean) -> Unit = {},
) {
    val sources by vm.sources.collectAsState()
    val look by vm.look.collectAsState()
    val homePrefs by vm.homePrefs.collectAsState()
    if (!sources.onboarded) {
        OnboardingScreen(sources, look.style, look.palette, vm::setStyle, vm::setLook, vm::setSources)
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
    val journal by vm.journal.collectAsState()
    val map by vm.map.collectAsState()
    val score by vm.score.collectAsState()
    val year by vm.year.collectAsState()
    val lookBack by vm.lookBack.collectAsState()
    val lock by vm.lock.collectAsState()
    val healthConnect by vm.healthConnect.collectAsState()
    val importState by vm.importState.collectAsState()
    val syncLog by vm.syncLog.collectAsState()
    val trophies by vm.trophies.collectAsState()
    val coach by vm.coach.collectAsState()
    val ai by vm.ai.collectAsState()
    val garmin by vm.garmin.collectAsState()
    val oura by vm.oura.collectAsState()
    val days by vm.days.collectAsState()
    val theme by vm.theme.collectAsState()
    val appLanguage by vm.appLanguage.collectAsState()
    val appIcon by vm.appIcon.collectAsState()
    val goals by vm.goals.collectAsState()
    val now by vm.now.collectAsState()
    val busy by vm.busy.collectAsState()
    val watch by vm.watch.collectAsState()
    val battery by vm.battery.collectAsState()
    // The front page and the Watch screen both show the stored charge; re-read it when a watch or Oura sync finishes.
    LaunchedEffect(watch, oura.lastSync) { vm.refreshBattery() }
    val forward by vm.forward.collectAsState()
    val forwardLog by vm.forwardLog.collectAsState()
    val forwardApps by vm.forwardApps.collectAsState()
    var insightsTab by rememberSaveable { mutableStateOf(InsightsTab.Week) }
    // The heatmap reads many tracks: it is only worked out while the Map tab is open (and again after the data changed).
    LaunchedEffect(insightsTab, map.loaded) { if (insightsTab == InsightsTab.HeatMap && !map.loaded) vm.loadMap() }
    LaunchedEffect(insightsTab, year.loaded) { if (insightsTab == InsightsTab.Year && !year.loaded) vm.loadYear() }
    // Look back reads every stored activity, so it is only worked out once its tab is opened (and again after the data changed).
    LaunchedEffect(insightsTab, lookBack.loaded) { if (insightsTab == InsightsTab.LookBack && !lookBack.loaded) vm.loadLookBack() }
    var healthFocus by remember { mutableStateOf<nl.tufein.openconnect.core.ChartMetric?>(null) }

    fun openTab(tab: Tab) {
        if (tab == Tab.Oura) vm.syncOuraIfDue()
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
        bottomBar = { if (showBar) TabBar(tabs, route, ::openTab) },
    ) { padding ->
        // On a tablet or an unfolded phone the screens stop at a reading width in the middle, instead of stretching across.
        NavHost(
            nav,
            startDestination = Tab.Home.route,
            modifier = Modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp),
        ) {
            composable(Tab.Home.route) {
                HomeScreen(
                    state = home,
                    garmin = garmin,
                    now = now,
                    battery = battery,
                    onOpenActivity = ::openActivity,
                    onDemo = vm::loadDemo,
                    onImport = onImport,
                    onWatch = ::openWatch,
                    onGarmin = ::openGarmin,
                    onSync = vm::syncGarmin,
                    onTrophies = { openInsights(InsightsTab.Trophies) },
                    contentPadding = padding,
                    onOpenHealth = ::openHealth,
                    onOpenActivities = { openTab(Tab.Activities) },
                    onOpenInsights = { openInsights(InsightsTab.Week) },
                    onOpenForm = { openInsights(InsightsTab.Form) },
                    onToggleJournal = vm::toggleJournal,
                    useGarmin = sources.garmin,
                    onOura = if (sources.oura) ::openOura else null,
                    homePrefs = homePrefs,
                    onHomeMetric = vm::setHomeMetric,
                    live = watch.live,
                    onScanLive = vm::scanSensors,
                    onConnectLive = vm::connectSensor,
                    onStopLive = vm::stopLive,
                    onSaveLive = vm::saveLiveRecording,
                )
            }
            composable(Tab.Activities.route) {
                ActivitiesScreen(activities, vm::loadActivities, ::openActivity, vm::loadDemo, onImport, vm::addManualActivity, padding)
            }
            composable(Tab.Health.route) { HealthScreen(health, vm::loadDemo, onImport, padding, focus = healthFocus, onFocusDone = { healthFocus = null }, onSaveBody = vm::saveBody, onAddWater = vm::addWater) }
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
                    onSaveWorkout = vm::saveOwnWorkout,
                    onDeleteWorkout = vm::deleteOwnWorkout,
                    onApplyPlanChange = vm::applyPlanChange,
                    onDismissPlanChange = vm::dismissPlanChange,
                    zones = zones,
                    onZones = vm::zonesAction,
                    gear = gear,
                    onGear = vm::gearAction,
                    calendar = calendar,
                    onShiftCalendar = vm::shiftCalendar,
                    form = home.form,
                    advice = home.advice,
                    journal = journal,
                    monotony = home.monotony,
                    ramp = home.ramp,
                    map = map,
                    score = score,
                    onScoreYear = vm::loadScore,
                    year = year,
                    onYear = vm::loadYear,
                    lookBack = lookBack,
                    onLookBackSport = vm::chooseLookBackSport,
                    onLoadDemoYears = vm::loadDemoYears,
                    day = days,
                    onInitials = vm::setInitials,
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
                    ui = SettingsUi(ai = ai, theme = theme, versionName = BuildConfig.VERSION_NAME, goals = goals, overview = overview, syncLog = syncLog, nudges = nudges, appLock = lock.enabled, lockAvailable = lockAvailable, sources = sources, look = look, appIcon = appIcon, fileImport = importState, home = homePrefs, appLanguage = appLanguage, onAppLanguage = vm::setAppLanguage),
                    onLook = vm::setLook,
                    onHomeMetric = vm::setHomeMetric,
                    onHomeBattery = vm::setHomeBattery,
                    onHomeJournal = vm::setHomeJournal,
                    onHomeLast7 = vm::setHomeLast7,
                    onHomeLive = vm::setHomeLive,
                    onLetters = vm::setLetters,
                    onScanlines = vm::setScanlines,
                    onSounds = vm::setRetroSounds,
                    onPlayTune = vm::playTune,
                    onLevelUps = vm::setLevelUps,
                    onStyle = vm::setStyle,
                    onSignals = vm::setSignals,
                    onRings = vm::setRings,
                    onHero = vm::setHero,
                    onAppIcon = vm::setAppIcon,
                    onSources = vm::setSources,
                    onAppLock = onAppLock,
                    onNudges = vm::setNudges,
                    health = healthConnect,
                    onHealthChecked = vm::refreshHealth,
                    onHealthExport = vm::exportHealth,
                    onHealthImport = vm::importHealth,
                    onHealthAuto = vm::setHealthAuto,
                    onHealthAutoImport = vm::setHealthAutoImport,
                    onBackup = onBackup,
                    onRestore = onRestore,
                    onExportAll = onExportAll,
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
                    onCancelImport = vm::cancelImport,
                    onExportDaily = { onExportCsv(CSV_DAILY) },
                    onExportActivities = { onExportCsv(CSV_ACTIVITIES) },
                    onClear = vm::clearAll,
                    onWatch = ::openWatch,
                    onGarmin = ::openGarmin,
                    onOura = ::openOura,
                    onAbout = { nav.navigate(ABOUT_ROUTE) { launchSingleTop = true } },
                    onPrivacy = { nav.navigate(PRIVACY_ROUTE) { launchSingleTop = true } },
                    contentPadding = padding,
                )
            }
            composable(ABOUT_ROUTE) {
                AboutScreen(
                    ui = SettingsUi(ai = ai, theme = theme, versionName = BuildConfig.VERSION_NAME, goals = goals, overview = overview, syncLog = syncLog, nudges = nudges, appLock = lock.enabled, lockAvailable = lockAvailable, sources = sources, look = look, appIcon = appIcon, appLanguage = appLanguage),
                    onBack = { nav.popBackStack() },
                    onPrivacy = { nav.navigate(PRIVACY_ROUTE) { launchSingleTop = true } },
                    contentPadding = padding,
                )
            }
            composable(PRIVACY_ROUTE) {
                PrivacyScreen(
                    onBack = { nav.popBackStack() },
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
                // Re-read the stored charge when the screen opens and whenever a watch or Oura sync finishes; the
                // navigation root also refreshes it, but opening this screen after a test or a setting changed must
                // show the values already stored.
                LaunchedEffect(watch, oura.lastSync) { vm.refreshBattery() }
                WatchScreen(
                    ui = watch,
                    battery = battery,
                    onBack = { nav.popBackStack() },
                    onSearchUsb = vm::refreshWatches,
                    onRead = vm::readWatch,
                    onScan = vm::scanSensors,
                    onConnect = vm::connectSensor,
                    onStopLive = vm::stopLive,
                    onSaveLive = vm::saveLive,
                    onAutoLive = vm::setAutoLive,
                    onReadFolder = vm::readFolder,
                    onFindGarmin = vm::findGarmin,
                    onStopFindGarmin = vm::stopFindGarmin,
                    onSyncGarmin = vm::syncGarmin,
                    onForgetGarmin = vm::forgetGarmin,
                    onGarminArchive = vm::setGarminArchive,
                    onGarminBackground = vm::setGarminBackground,
                    forward = forward,
                    forwardLog = forwardLog,
                    onForwardEnabled = vm::setForward,
                    onForwardApps = { nav.navigate(WATCH_APPS_ROUTE) { launchSingleTop = true } },
                    onForwardTest = vm::testForward,
                    onForwardReconnect = vm::forwardReconnect,
                    onForwardHide = vm::setForwardHide,
                    onForwardScreenOff = vm::setForwardScreenOff,
                    onForwardRefresh = vm::refreshForward,
                    contentPadding = padding,
                )
            }
            composable(WATCH_APPS_ROUTE) {
                LaunchedEffect(Unit) { vm.loadForwardApps() }
                NotificationAppsScreen(
                    apps = forwardApps,
                    chosen = forward.apps,
                    onBack = { nav.popBackStack() },
                    onChoose = vm::setForwardApp,
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
