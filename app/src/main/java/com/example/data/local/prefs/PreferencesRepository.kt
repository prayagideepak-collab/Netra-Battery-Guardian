package com.example.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AppLanguage
import com.example.domain.model.CoolingSensitivity
import com.example.domain.model.GuardianProfile
import com.example.domain.model.LocationSaverMode
import com.example.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.netraDataStore: DataStore<Preferences> by preferencesDataStore(name = "netra_prefs")

data class UserPreferences(
  val appLanguage: AppLanguage = AppLanguage.ENGLISH,
  val voiceLanguage: AppLanguage = AppLanguage.ENGLISH,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val voiceEnabled: Boolean = true,
  val voiceVolume: Float = 0.85f,
  val announceCharging: Boolean = true,
  val announceDischarging: Boolean = true,
  val announceWhileScreenOff: Boolean = true,
  val announcementStepPercent: Int = 5,
  val nightSilentEnabled: Boolean = true,
  val nightSilentStartMinutes: Int = 23 * 60, // 11:00 PM
  val nightSilentEndMinutes: Int = 6 * 60, // 6:00 AM
  val coolingSensitivity: CoolingSensitivity = CoolingSensitivity.BALANCED,
  val locationSaverMode: LocationSaverMode = LocationSaverMode.NORMAL,
  val locationSamplingIntervalMinutes: Int = 5,
  val emergencyLocationException: Boolean = true,
  val isTripActive: Boolean = false,
  val storageNotificationsEnabled: Boolean = true,
  val batterySaverThreshold: Int = 20,
  val lowBatteryAlertsEnabled: Boolean = true,
  val dataRetentionDays: Int = 30,
  val liveGuardianServiceEnabled: Boolean = false,
  val manualProfileOverride: GuardianProfile? = null,
  val lastScreenOffLevel: Int = -1,
  val lastScreenOffTimestamp: Long = 0L,
  val below35SinceMillis: Long = 0L,
  val observedCapacityMah: Int = -1,
  val cumulativeChargedPercent: Float = 0f,
  val designCapacityBaselineMah: Int = 0,
  val lastChargeLevelForCycles: Int = -1,
)

@Singleton
class PreferencesRepository @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private object Keys {
    val APP_LANGUAGE = stringPreferencesKey("app_language")
    val VOICE_LANGUAGE = stringPreferencesKey("voice_language")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
    val VOICE_VOLUME = floatPreferencesKey("voice_volume")
    val ANNOUNCE_CHARGING = booleanPreferencesKey("announce_charging")
    val ANNOUNCE_DISCHARGING = booleanPreferencesKey("announce_discharging")
    val ANNOUNCE_SCREEN_OFF = booleanPreferencesKey("announce_screen_off")
    val ANNOUNCEMENT_STEP = intPreferencesKey("announcement_step")
    val NIGHT_SILENT_ENABLED = booleanPreferencesKey("night_silent_enabled")
    val NIGHT_SILENT_START = intPreferencesKey("night_silent_start")
    val NIGHT_SILENT_END = intPreferencesKey("night_silent_end")
    val COOLING_SENSITIVITY = stringPreferencesKey("cooling_sensitivity")
    val LOCATION_SAVER_MODE = stringPreferencesKey("location_saver_mode")
    val LOCATION_SAMPLING_MIN = intPreferencesKey("location_sampling_min")
    val EMERGENCY_LOC_EXCEPTION = booleanPreferencesKey("emergency_loc_exception")
    val TRIP_ACTIVE = booleanPreferencesKey("trip_active")
    val STORAGE_NOTIFS = booleanPreferencesKey("storage_notifs")
    val BATTERY_SAVER_THRESHOLD = intPreferencesKey("battery_saver_threshold")
    val LOW_BATTERY_ALERTS = booleanPreferencesKey("low_battery_alerts")
    val DATA_RETENTION_DAYS = intPreferencesKey("data_retention_days")
    val LIVE_GUARDIAN_SERVICE = booleanPreferencesKey("live_guardian_service")
    val MANUAL_PROFILE_OVERRIDE = stringPreferencesKey("manual_profile_override")
    val LAST_SCREEN_OFF_LEVEL = intPreferencesKey("last_screen_off_level")
    val LAST_SCREEN_OFF_TIME = longPreferencesKey("last_screen_off_time")
    val BELOW_35_SINCE = longPreferencesKey("below_35_since")
    val OBSERVED_CAPACITY_MAH = intPreferencesKey("observed_capacity_mah")
    val CUMULATIVE_CHARGED_PCT = floatPreferencesKey("cumulative_charged_pct")
    val DESIGN_CAPACITY_BASELINE_MAH = intPreferencesKey("design_capacity_baseline_mah")
    val LAST_CHARGE_LEVEL_FOR_CYCLES = intPreferencesKey("last_charge_level_for_cycles")
  }

  val preferences: Flow<UserPreferences> =
    context.netraDataStore.data
      .catch { e ->
        if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
        else throw e
      }
      .map { p ->
        UserPreferences(
          appLanguage = enumValueOrDefault(p[Keys.APP_LANGUAGE], AppLanguage.ENGLISH),
          voiceLanguage = enumValueOrDefault(p[Keys.VOICE_LANGUAGE], AppLanguage.ENGLISH),
          themeMode = enumValueOrDefault(p[Keys.THEME_MODE], ThemeMode.SYSTEM),
          voiceEnabled = p[Keys.VOICE_ENABLED] ?: true,
          voiceVolume = (p[Keys.VOICE_VOLUME] ?: 0.85f).coerceIn(0f, 1f),
          announceCharging = p[Keys.ANNOUNCE_CHARGING] ?: true,
          announceDischarging = p[Keys.ANNOUNCE_DISCHARGING] ?: true,
          announceWhileScreenOff = p[Keys.ANNOUNCE_SCREEN_OFF] ?: true,
          announcementStepPercent = (p[Keys.ANNOUNCEMENT_STEP] ?: 5).coerceIn(1, 25),
          nightSilentEnabled = p[Keys.NIGHT_SILENT_ENABLED] ?: true,
          nightSilentStartMinutes = p[Keys.NIGHT_SILENT_START] ?: (23 * 60),
          nightSilentEndMinutes = p[Keys.NIGHT_SILENT_END] ?: (6 * 60),
          coolingSensitivity = enumValueOrDefault(p[Keys.COOLING_SENSITIVITY], CoolingSensitivity.BALANCED),
          locationSaverMode = enumValueOrDefault(p[Keys.LOCATION_SAVER_MODE], LocationSaverMode.NORMAL),
          locationSamplingIntervalMinutes = (p[Keys.LOCATION_SAMPLING_MIN] ?: 5).coerceIn(1, 60),
          emergencyLocationException = p[Keys.EMERGENCY_LOC_EXCEPTION] ?: true,
          isTripActive = p[Keys.TRIP_ACTIVE] ?: false,
          storageNotificationsEnabled = p[Keys.STORAGE_NOTIFS] ?: true,
          batterySaverThreshold = (p[Keys.BATTERY_SAVER_THRESHOLD] ?: 20).coerceIn(10, 40),
          lowBatteryAlertsEnabled = p[Keys.LOW_BATTERY_ALERTS] ?: true,
          dataRetentionDays = (p[Keys.DATA_RETENTION_DAYS] ?: 30).coerceIn(7, 90),
          liveGuardianServiceEnabled = p[Keys.LIVE_GUARDIAN_SERVICE] ?: false,
          manualProfileOverride = p[Keys.MANUAL_PROFILE_OVERRIDE]?.let { name ->
            runCatching { GuardianProfile.valueOf(name) }.getOrNull()
          },
          lastScreenOffLevel = p[Keys.LAST_SCREEN_OFF_LEVEL] ?: -1,
          lastScreenOffTimestamp = p[Keys.LAST_SCREEN_OFF_TIME] ?: 0L,
          below35SinceMillis = p[Keys.BELOW_35_SINCE] ?: 0L,
          observedCapacityMah = -1, // Previous cache included fabricated defaults.
          cumulativeChargedPercent = (p[Keys.CUMULATIVE_CHARGED_PCT] ?: 0f).coerceAtLeast(0f),
          designCapacityBaselineMah = (p[Keys.DESIGN_CAPACITY_BASELINE_MAH] ?: 0).coerceAtLeast(0),
          lastChargeLevelForCycles = p[Keys.LAST_CHARGE_LEVEL_FOR_CYCLES] ?: -1,
        )
      }

  suspend fun updateAppLanguage(language: AppLanguage) {
    context.netraDataStore.edit { it[Keys.APP_LANGUAGE] = language.name }
  }

  suspend fun updateVoiceLanguage(language: AppLanguage) {
    context.netraDataStore.edit { it[Keys.VOICE_LANGUAGE] = language.name }
  }

  suspend fun updateThemeMode(mode: ThemeMode) {
    context.netraDataStore.edit { it[Keys.THEME_MODE] = mode.name }
  }

  suspend fun updateVoiceEnabled(enabled: Boolean) {
    context.netraDataStore.edit { it[Keys.VOICE_ENABLED] = enabled }
  }

  suspend fun updateVoiceVolume(volume: Float) {
    context.netraDataStore.edit { it[Keys.VOICE_VOLUME] = volume.coerceIn(0f, 1f) }
  }

  suspend fun updateAnnounceCharging(enabled: Boolean) {
    context.netraDataStore.edit { it[Keys.ANNOUNCE_CHARGING] = enabled }
  }

  suspend fun updateAnnounceDischarging(enabled: Boolean) {
    context.netraDataStore.edit { it[Keys.ANNOUNCE_DISCHARGING] = enabled }
  }

  suspend fun updateAnnounceScreenOff(enabled: Boolean) {
    context.netraDataStore.edit { it[Keys.ANNOUNCE_SCREEN_OFF] = enabled }
  }

  suspend fun updateAnnouncementStep(step: Int) {
    context.netraDataStore.edit { it[Keys.ANNOUNCEMENT_STEP] = step.coerceIn(1, 25) }
  }

  suspend fun updateNightSilent(enabled: Boolean, startMin: Int, endMin: Int) {
    context.netraDataStore.edit {
      it[Keys.NIGHT_SILENT_ENABLED] = enabled
      it[Keys.NIGHT_SILENT_START] = startMin.coerceIn(0, 1439)
      it[Keys.NIGHT_SILENT_END] = endMin.coerceIn(0, 1439)
    }
  }

  suspend fun updateCoolingSensitivity(sensitivity: CoolingSensitivity) {
    context.netraDataStore.edit { it[Keys.COOLING_SENSITIVITY] = sensitivity.name }
  }

  suspend fun updateLocationSaverMode(mode: LocationSaverMode, samplingMinutes: Int? = null) {
    context.netraDataStore.edit {
      it[Keys.LOCATION_SAVER_MODE] = mode.name
      if (samplingMinutes != null) {
        it[Keys.LOCATION_SAMPLING_MIN] = samplingMinutes.coerceIn(1, 60)
      }
      if (mode != LocationSaverMode.TRIP) {
        it[Keys.TRIP_ACTIVE] = false
      }
    }
  }

  suspend fun setEmergencyLocationException(enabled: Boolean) {
    context.netraDataStore.edit { it[Keys.EMERGENCY_LOC_EXCEPTION] = enabled }
  }

  suspend fun setTripActive(active: Boolean) {
    context.netraDataStore.edit {
      it[Keys.TRIP_ACTIVE] = active
      if (active) {
        it[Keys.LOCATION_SAVER_MODE] = LocationSaverMode.TRIP.name
      }
    }
  }

  suspend fun updateStorageNotifications(enabled: Boolean) {
    context.netraDataStore.edit { it[Keys.STORAGE_NOTIFS] = enabled }
  }

  suspend fun updateBatterySaverThreshold(threshold: Int) {
    context.netraDataStore.edit { it[Keys.BATTERY_SAVER_THRESHOLD] = threshold.coerceIn(10, 40) }
  }

  suspend fun updateLowBatteryAlerts(enabled: Boolean) {
    context.netraDataStore.edit { it[Keys.LOW_BATTERY_ALERTS] = enabled }
  }

  suspend fun updateDataRetentionDays(days: Int) {
    context.netraDataStore.edit { it[Keys.DATA_RETENTION_DAYS] = days.coerceIn(7, 90) }
  }

  suspend fun updateLiveGuardianService(enabled: Boolean) {
    context.netraDataStore.edit { it[Keys.LIVE_GUARDIAN_SERVICE] = enabled }
  }

  suspend fun setManualProfileOverride(profile: GuardianProfile?) {
    context.netraDataStore.edit {
      if (profile == null) it.remove(Keys.MANUAL_PROFILE_OVERRIDE)
      else it[Keys.MANUAL_PROFILE_OVERRIDE] = profile.name
    }
  }

  suspend fun setScreenOffBaseline(level: Int, timestamp: Long) {
    context.netraDataStore.edit {
      it[Keys.LAST_SCREEN_OFF_LEVEL] = level
      it[Keys.LAST_SCREEN_OFF_TIME] = timestamp
    }
  }

  suspend fun setBelow35Since(timestamp: Long) {
    context.netraDataStore.edit { it[Keys.BELOW_35_SINCE] = timestamp }
  }

  suspend fun updateCycleTracking(
    cumulativeChargedPercent: Float,
    lastChargeLevel: Int,
    designCapacityBaselineMah: Int? = null,
  ) {
    context.netraDataStore.edit {
      it[Keys.CUMULATIVE_CHARGED_PCT] = cumulativeChargedPercent.coerceAtLeast(0f)
      it[Keys.LAST_CHARGE_LEVEL_FOR_CYCLES] = lastChargeLevel
      if (designCapacityBaselineMah != null && designCapacityBaselineMah > 0) {
        it[Keys.DESIGN_CAPACITY_BASELINE_MAH] = designCapacityBaselineMah
      }
    }
  }

  suspend fun resetAllPreferences() {
    context.netraDataStore.edit { it.clear() }
  }

  private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T {
    if (value == null) return default
    return runCatching { enumValueOf<T>(value) }.getOrDefault(default)
  }
}
