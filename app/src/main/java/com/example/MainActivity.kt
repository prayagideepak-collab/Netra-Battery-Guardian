package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.i18n.AppStrings
import com.example.ui.screens.AppsScreen
import com.example.ui.screens.BatteryScreen
import com.example.ui.screens.HeatScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LiveMonitorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StorageScreen
import com.example.ui.theme.NetraBatteryTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object BatteryRoute
@Serializable object HeatRoute
@Serializable object AppsRoute
@Serializable object StorageRoute
@Serializable object SettingsRoute
@Serializable object LiveMonitorRoute

private data class NavTab(
  val route: Any,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val tag: String,
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  private val viewModel: MainViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val state by viewModel.uiState.collectAsStateWithLifecycle()
      val strings = remember(state.prefs.appLanguage) { AppStrings(state.prefs.appLanguage) }

      val lifecycleOwner = LocalLifecycleOwner.current
      DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
          when (event) {
            Lifecycle.Event.ON_START -> viewModel.onForegroundStarted()
            Lifecycle.Event.ON_STOP -> viewModel.onForegroundStopped()
            else -> Unit
          }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
      }

      // Request POST_NOTIFICATIONS politely on Android 13+
      val context = LocalContext.current
      val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
      ) {}
      LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
          ) == PackageManager.PERMISSION_GRANTED
          if (!granted) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          }
        }
      }

      NetraBatteryTheme(themeMode = state.prefs.themeMode) {
        NetraRootScaffold(
          viewModel = viewModel,
          strings = strings,
        )
      }
    }
  }
}

@Composable
private fun NetraRootScaffold(
  viewModel: MainViewModel,
  strings: AppStrings,
) {
  val csvSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
    viewModel.exportCsvReport(uri)
  }
  val pdfSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
    viewModel.exportPdfReport(uri)
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(state.statusMessage) {
    state.statusMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.consumeStatusMessage()
    }
  }

  val tabs = remember(strings) {
    listOf(
      NavTab(HomeRoute, strings.navHome, Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
      NavTab(BatteryRoute, strings.navBattery, Icons.Rounded.BatteryChargingFull, Icons.Outlined.BatteryChargingFull, "nav_battery"),
      NavTab(HeatRoute, strings.navHeat, Icons.Rounded.Whatshot, Icons.Outlined.Whatshot, "nav_heat"),
      NavTab(AppsRoute, strings.navApps, Icons.Rounded.Apps, Icons.Outlined.Apps, "nav_apps"),
      NavTab(StorageRoute, strings.navStorage, Icons.Rounded.SdStorage, Icons.Outlined.SdStorage, "nav_storage"),
      NavTab(SettingsRoute, strings.navSettings, Icons.Rounded.Settings, Icons.Outlined.Settings, "nav_settings"),
    )
  }

  fun navigateTo(route: Any) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 700.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      bottomBar = {
        if (!useRail) {
          NavigationBar {
            tabs.forEach { tab ->
              val selected = backStackEntry?.destination?.hasRoute(tab.route::class) == true
              NavigationBarItem(
                selected = selected,
                onClick = { navigateTo(tab.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                  )
                },
                label = {
                  Text(
                    text = tab.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                alwaysShowLabel = true,
                modifier = Modifier.testTag(tab.tag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useRail) {
          NavigationRail {
            tabs.forEach { tab ->
              val selected = backStackEntry?.destination?.hasRoute(tab.route::class) == true
              NavigationRailItem(
                selected = selected,
                onClick = { navigateTo(tab.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                  )
                },
                label = { Text(tab.label, maxLines = 1) },
                modifier = Modifier.testTag(tab.tag),
              )
            }
          }
        }

        NavHost(
          navController = navController,
          startDestination = HomeRoute,
          modifier = Modifier.weight(1f).fillMaxSize(),
        ) {
          composable<HomeRoute> {
            HomeScreen(
              state = state,
              strings = strings,
              onRefresh = { viewModel.refreshNow(forceSnapshot = true) },
              onToggleBatterySaver = viewModel::toggleBatterySaverQuick,
              onToggleCooling = viewModel::toggleCoolingModeQuick,
              onToggleLocationSaver = viewModel::toggleLocationSaverQuick,
              onToggleNightSilent = viewModel::toggleNightSilentQuick,
              onOpenStorageTab = { navigateTo(StorageRoute) },
              onOpenBatteryTab = { navigateTo(BatteryRoute) },
              onOpenHeatTab = { navigateTo(HeatRoute) },
              onOpenAppsTab = { navigateTo(AppsRoute) },
              onOpenLiveMonitor = { navController.navigate(LiveMonitorRoute) },
            )
          }
          composable<BatteryRoute> {
            BatteryScreen(
              state = state,
              strings = strings,
              onOpenLiveMonitor = { navController.navigate(LiveMonitorRoute) },
              onOptimizerAction = viewModel::handleOptimizerAction,
              onExportCsv = { csvSave.launch("netra_battery_report.csv") },
              onQueueChargingCsvExport = viewModel::queueChargingCsvExport,
            )
          }
          composable<LiveMonitorRoute> {
            LiveMonitorScreen(
              latestHourlySnapshot = state.hourlySnapshots.firstOrNull(),
              animationsAllowed = state.powerBudget.activeTier != com.example.domain.model.AdaptivePowerTier.SURVIVAL,
              strings = strings,
              onNavigateBack = { navController.navigateUp() },
              onOpenBatteryHistory = { navigateTo(BatteryRoute) },
              telemetry = state.telemetry,
              chargingDiagnosis = state.chargingDiagnosis,
              historySnapshots = if (state.snapshots24h.isNotEmpty()) state.snapshots24h else state.hourlySnapshots,
            )
          }
          composable<HeatRoute> {
            HeatScreen(
              state = state,
              strings = strings,
              onToggleCoolingMode = viewModel::toggleCoolingModeQuick,
              onOpenWriteSettings = viewModel::openWriteSettings,
              onSetCoolingBrightnessAllowed = viewModel::setCoolingBrightnessAllowed,
              onRetryCoolingBrightnessRestore = viewModel::retryCoolingBrightnessRestore,
            )
          }
          composable<AppsRoute> {
            AppsScreen(
              state = state,
              strings = strings,
              onOpenUsageAccess = viewModel::openUsageAccessSettings,
              onOpenAppInfo = viewModel::openAppInfo,
              onOpenAppNotifications = viewModel::openAppNotifications,
              onOpenBatteryOptimizationSettings = viewModel::openBatteryOptimizationSettings,
              onOpenBatterySaverSettings = viewModel::openBatterySaverSettings,
              onOpenLocationSettings = viewModel::openLocationSettings,
              onOpenDataSaverSettings = viewModel::openDataSaverSettings,
              onSetLocationMode = viewModel::setLocationSaverMode,
              onSetEmergencyException = viewModel::setEmergencyLocationException,
              onToggleTripMode = viewModel::toggleTripMode,
            )
          }
          composable<StorageRoute> {
            StorageScreen(
              state = state,
              strings = strings,
              onCleanOwnCache = viewModel::cleanAppOwnCache,
              onOpenStorageTarget = viewModel::openStorageTarget,
            )
          }
          composable<SettingsRoute> {
            SettingsScreen(
              state = state,
              strings = strings,
              onSetAppLanguage = viewModel::setAppLanguage,
              onSetVoiceLanguage = viewModel::setVoiceLanguage,
              onSetThemeMode = viewModel::setThemeMode,
              onSetVoiceEnabled = viewModel::setVoiceEnabled,
              onSetVoiceVolume = viewModel::setVoiceVolume,
              onSetAnnounceCharging = viewModel::setAnnounceCharging,
              onSetAnnounceDischarging = viewModel::setAnnounceDischarging,
              onSetAnnounceScreenOff = viewModel::setAnnounceScreenOff,
              onSetAnnouncementStep = viewModel::setAnnouncementStep,
              onSetNightSilent = viewModel::setNightSilent,
              onSetCoolingSensitivity = viewModel::setCoolingSensitivity,
              onSetStorageNotifications = viewModel::setStorageNotifications,
              onSetBatterySaverThreshold = viewModel::setBatterySaverThreshold,
              onSetLowBatteryAlerts = viewModel::setLowBatteryAlerts,
              onSetDataRetentionDays = viewModel::setDataRetentionDays,
              onSetLiveGuardianService = viewModel::setLiveGuardianService,
              onTestVoice = viewModel::testVoiceAnnouncement,
              onExportCsv = { csvSave.launch("netra_battery_report.csv") },
              onExportPdf = { pdfSave.launch("netra_battery_report.pdf") },
              onClearAllData = viewModel::clearAllLocalData,
              onQueueChargingCsvExport = viewModel::queueChargingCsvExport,
            )
          }
        }
      }
    }
  }
}
