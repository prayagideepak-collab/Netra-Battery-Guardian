# Guardian targeted stability baseline

Base: 9b20f3b13976a734afd58c0edec138ceefb66007 (released 1.0.1).
This is a repair of the same app, not a rebuild. No signing, package or preference-default changes. Owner added Bluetooth monitoring during this repair: optional BLUETOOTH_CONNECT (Android12+) and legacy BLUETOOTH (through Android11) are necessary for connected-device reads. No scan/location permission added.

## Feature and consumer map

| Feature | Implementation and consumers | Baseline finding |
| --- | --- | --- |
| Native hardware readings | BatteryTelemetryCollector -> UI, services, workers, two widgets | Preserve signed sensor readings, blank missing values, observed capacity only. Thermal NONE currently also represents unsupported APIs. |
| Milestones, charger and safety voice | AutomationRulesEngine -> ChargingVoiceAssistant; optional BatteryThresholdTtsService -> same assistant; PreferencesRepository -> Settings | Default step5 preserved. Threshold service only admits80/90/100 charge and20/15/10/5/1 discharge. Routine screen-off preference ignored. Voice policy conflict reported before changes. |
| Essential monitoring | BatteryMonitoringService, GuardianService -> shared singleton rules engine | Live toggle/Live Monitor start Monitoring; user Trip starts Guardian. They can coexist, not always both active. Same notification ID, coroutine per event. Rules mutex serializes but does not deduplicate. |
| Thermal detection and trend | ThermalMonitor, ThermalSafetyMonitorWorker, TemperatureControlEngine -> Heat/History, brightness | OS status/health classification and trend retained; no new universal critical Celsius limits. Thermal alerts remain essential. |
| Adaptive tiers | AdaptivePowerTierEngine -> UI/service DB batch, charts and ring | Thresholds/hysteresis retained. Storage/usage work not consistently gated; displayed suspension descriptions overstate enforcement. |
| Brightness | CoolingBrightnessController -> collector -> rules; Heat consent UI | Preserve write-ahead originals, explicit opt-in, manual override, pending restoration and conservative interrupted recovery. Existing fixture tests are not physical-device evidence. |
| Usage | AppPowerInspector -> foreground and service rules/Apps/optimizer | Permission check and foreground-only labels retained. Queries repeated per event and can throw on access changes. |
| Storage/cache | StorageInspector, DiskUsageHelper -> UI, AutomationRulesEngine, StorageAuditWorker | Verified recursive scans per evaluation; postponement text does not skip scan. Existing idle12h WorkManager job and100MiB threshold retained. Cleanup must stay within safe own-cache roots. |
| History/sessions | BatteryHistoryRepository, Room DAOs -> UI/charts/export | Existing schema/migrations retained. Repeated unchanged events still update sessions; hourly persistence has multiple consumers. |
| Idle maintenance | NetraApplication/BootReceiver/NetraWorkScheduler -> storage, retention, health, persistence, thermal | Preserve unique scheduled work and essential thermal checks. No reboot guarantee: OS limits/user force-stop still apply. |
| Reports/privacy | LocalReportExporter, SAF save picker, reset, backup rules | Preserve private files, no INTERNET, consented destination, formula escaping, reset cancellation. Manifest alone does not prove runtime privacy. |
| Widgets | GuardianBatteryWidget4x3 last reading; BatteryThermalWidgetProvider3x1 OS30min | Preserve both sizes/resizing/refresh choices. Real launcher validation not available. |
| UI/navigation | MainActivity,7screens, native charts, theme/i18n | Preserve layout/navigation; no redesign. Some stale thermal-limit and power-gate text needs factual correction only. |
| Location controls | prefs/AppsScreen/Trip foreground service | No actual location-request implementation found; controls cannot limit other apps GPS. Report limitation, do not invent new tracker. |
| Charging speed | existing classifier | Separate requested six-category feature is NOT part of this branch. |

## Evidence boundaries

Historical main CI44348833012 passed91tests. No new branch tests have run yet.
OWNER-REPORTED current released version device-tested by Deepak, problems found there (source confirmation recorded in conversation).
New corrective branch: NOT RUN - DEVICE TEST REQUIRED. No measured battery-drain or physical thermal-safety claim.
