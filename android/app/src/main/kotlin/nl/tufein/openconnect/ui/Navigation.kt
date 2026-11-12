package nl.tufein.openconnect.ui

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
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

private enum class Tab(val route: String, val label: Int, val icon: ImageVector) {
    Home("home", R.string.tab_home, Icons.Filled.Home),
    Activities("activities", R.string.tab_activities, Icons.Filled.LocationOn),
    Health("health", R.string.tab_health, Icons.Filled.Favorite),
    Reports("reports", R.string.tab_reports, Icons.Filled.DateRange),
    Settings("settings", R.string.tab_settings, Icons.Filled.Settings),
}

private const val DETAIL = "activity/{id}"

@Composable
fun OpenConnectRoot(
    vm: AppViewModel,
    onImport: () -> Unit,
    onExport: (activityId: String, format: String) -> Unit,
) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = Tab.entries.any { it.route == route }

    val home by vm.home.collectAsState()
    val activities by vm.activities.collectAsState()
    val detail by vm.detail.collectAsState()
    val health by vm.health.collectAsState()
    val reports by vm.reports.collectAsState()
    val busy by vm.busy.collectAsState()
    val settings = vm.settings
    var refreshKey by remember { mutableIntStateOf(0) }
    val usingClaude = remember(refreshKey, settings.providerChoice, settings.hasApiKey) {
        settings.reportProvider().name == "anthropic"
    }

    fun openActivity(id: String) {
        vm.openActivity(id)
        nav.navigate("activity/" + Uri.encode(id))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    for (tab in Tab.entries) {
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            // Plain font and a little smaller than the monospace headings: "Activiteiten" and "Instellingen"
                            // must fit a fifth of a narrow phone without being cut off.
                            label = {
                                Text(
                                    stringResource(tab.label),
                                    maxLines = 1,
                                    softWrap = false,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Default, fontSize = 11.sp),
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
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
                HomeScreen(home, ::openActivity, vm::loadDemo, onImport, padding)
            }
            composable(Tab.Activities.route) {
                ActivitiesScreen(activities, vm::loadActivities, ::openActivity, vm::loadDemo, onImport, padding)
            }
            composable(Tab.Health.route) { HealthScreen(health, vm::loadDemo, onImport, padding) }
            composable(Tab.Reports.route) {
                ReportsScreen(reports, busy, usingClaude, vm::shiftWeek, vm::generateWeeklyReport, ::openActivity, padding)
            }
            composable(Tab.Settings.route) {
                SettingsScreen(
                    ui = SettingsUi(
                        language = settings.reportLanguage,
                        provider = settings.providerChoice,
                        model = settings.model,
                        hasApiKey = settings.hasApiKey,
                        canStoreApiKey = settings.canStoreApiKey,
                        versionName = BuildConfig.VERSION_NAME,
                    ),
                    onLanguage = { settings.reportLanguage = it; refreshKey++ },
                    onProvider = { settings.providerChoice = it; refreshKey++ },
                    onModel = { settings.model = it },
                    onSaveKey = { settings.apiKey = it; refreshKey++ },
                    onRemoveKey = { settings.apiKey = null; refreshKey++ },
                    onDemo = vm::loadDemo,
                    onImport = onImport,
                    onClear = vm::clearAll,
                    contentPadding = padding,
                )
            }
            composable(DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { backStack ->
                val id = backStack.arguments?.getString("id").orEmpty()
                ActivityDetailScreen(
                    state = detail,
                    busy = busy,
                    usingClaude = usingClaude,
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
