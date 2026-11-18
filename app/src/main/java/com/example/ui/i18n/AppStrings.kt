package com.example.ui.i18n

import com.example.domain.model.AppLanguage
import com.example.domain.model.ChargingSpeedClass
import com.example.domain.model.GuardianProfile
import com.example.domain.model.LocationSaverMode
import com.example.domain.model.TemperatureZone
import com.example.domain.model.ThermalLevel

class AppStrings(private val language: AppLanguage) {
  val isHindi: Boolean
    get() = language == AppLanguage.HINDI

  val appTitle: String
    get() = if (isHindi) "नेत्रा बैटरी गार्जियन" else "Netra Battery Guardian"

  val offlineBadge: String
    get() = if (isHindi) "100% ऑफ़लाइन • निजी" else "100% Offline • On-Device"

  val navHome: String get() = if (isHindi) "होम" else "Home"
  val navBattery: String get() = if (isHindi) "बैटरी" else "Battery"
  val navHeat: String get() = if (isHindi) "गर्मी" else "Heat"
  val navApps: String get() = if (isHindi) "ऐप्स" else "Apps"
  val navStorage: String get() = if (isHindi) "स्टोरेज" else "Storage"
  val navSettings: String get() = if (isHindi) "सेटिंग्स" else "Settings"

  val charging: String get() = if (isHindi) "चार्जिंग चालू" else "Charging"
  val discharging: String get() = if (isHindi) "बैटरी पर" else "Discharging"
  val timeToFull: String get() = if (isHindi) "फुल होने में समय" else "Est. time to full"
  val estBackup: String get() = if (isHindi) "अनुमानित बैकअप" else "Est. battery backup"
  val approximateLabel: String get() = if (isHindi) "(अनुमान)" else "(Estimate)"
  val temperature: String get() = if (isHindi) "तापमान" else "Temp"
  val voltage: String get() = if (isHindi) "वोल्टेज" else "Voltage"
  val current: String get() = if (isHindi) "करंट" else "Current"
  val netPower: String get() = if (isHindi) "नेट पावर" else "Net Power"
  val health: String get() = if (isHindi) "हेल्थ" else "Health"
  val thermalStatus: String get() = if (isHindi) "थर्मल स्तर" else "Thermal"
  val activeProfile: String get() = if (isHindi) "सक्रिय प्रोफ़ाइल" else "Active Profile"
  val quickActions: String get() = if (isHindi) "त्वरित नियंत्रण" else "Quick Actions"
  val whyButton: String get() = if (isHindi) "क्यों?" else "Why?"

  val screenOnVsOff: String get() = if (isHindi) "स्क्रीन ऑन बनाम ऑफ़" else "Screen-On vs Screen-Off"
  val todaysDrainGraph: String get() = if (isHindi) "आज का बैटरी ग्राफ़" else "Today's Battery Trend"
  val topAppsTitle: String get() = if (isHindi) "शीर्ष फ़ोरग्राउंड उपयोग ऐप्स" else "Top Foreground-Use Apps"
  val storageSummary: String get() = if (isHindi) "स्टोरेज सारांश" else "Storage Summary"

  val quickBatterySaver: String get() = if (isHindi) "बैटरी सेवर" else "Battery Saver"
  val quickCooling: String get() = if (isHindi) "कूलिंग मोड" else "Cooling Mode"
  val quickLocationSaver: String get() = if (isHindi) "लोकेशन सेवर" else "Location Saver"
  val quickNightSilent: String get() = if (isHindi) "नाइट साइलेंट" else "Night Silent"
  val quickStorageClean: String get() = if (isHindi) "स्टोरेज क्लीन" else "Storage Clean"
  val liveMonitorTitle: String get() = if (isHindi) "लाइव सर्विस मॉनिटर" else "Live Service Monitor"
  val liveServiceAction: String get() = if (isHindi) "लाइव मॉनिटर" else "Live Service"

  fun speedLabel(speed: ChargingSpeedClass): String =
    when (speed) {
      ChargingSpeedClass.UNAVAILABLE -> "Unavailable"
      ChargingSpeedClass.NOT_CHARGING -> if (isHindi) "चार्जिंग नहीं" else "Not Charging"
      ChargingSpeedClass.DISCHARGING_WHILE_PLUGGED ->
        if (isHindi) "चार्जर लगा है पर घट रही है" else "Discharging While Plugged"
      ChargingSpeedClass.SLOW -> if (isHindi) "धीमी / ट्रिकल चार्जिंग" else "Slow / Trickle"
      ChargingSpeedClass.STANDARD -> if (isHindi) "सामान्य चार्जिंग" else "Standard Charging"
      ChargingSpeedClass.FAST -> if (isHindi) "तेज़ चार्जिंग" else "Fast Charging"
      ChargingSpeedClass.SUPER_FAST -> if (isHindi) "सुपर फ़ास्ट" else "Super Fast"
      ChargingSpeedClass.ULTRA_FAST -> if (isHindi) "अल्ट्रा फ़ास्ट" else "Ultra Fast"
      ChargingSpeedClass.EXTREME_FAST -> if (isHindi) "एक्सट्रीम फ़ास्ट" else "Extreme Fast"
    }

  fun profileLabel(profile: GuardianProfile): String =
    when (profile) {
      GuardianProfile.NORMAL -> if (isHindi) "सामान्य (Normal)" else "Normal"
      GuardianProfile.BATTERY_SAVER -> if (isHindi) "बैटरी सेवर" else "Battery Saver"
      GuardianProfile.ULTRA_BATTERY_SAVER -> if (isHindi) "अल्ट्रा बैटरी सेवर" else "Ultra Battery Saver"
      GuardianProfile.SLEEP -> if (isHindi) "स्लीप मोड" else "Sleep Mode"
      GuardianProfile.GAMING -> if (isHindi) "गेमिंग मोड" else "Gaming Mode"
      GuardianProfile.TRAVEL -> if (isHindi) "ट्रैवल मोड" else "Travel Mode"
      GuardianProfile.COOLING -> if (isHindi) "कूलिंग मोड" else "Cooling Mode"
      GuardianProfile.CHARGING -> if (isHindi) "चार्जिंग मोड" else "Charging Mode"
    }

  fun zoneLabel(zone: TemperatureZone): String =
    when (zone) {
      TemperatureZone.UNAVAILABLE -> "Unavailable"
      TemperatureZone.TOO_COLD -> if (isHindi) "बहुत ठंडा (<0°C)" else "Too Cold (<0°C)"
      TemperatureZone.COLD -> if (isHindi) "ठंडा (0–15°C)" else "Cold (0–15°C)"
      TemperatureZone.NORMAL -> if (isHindi) "सामान्य (16–35°C)" else "Ideal / Normal"
      TemperatureZone.WARM -> if (isHindi) "हल्का गर्म (36–39°C)" else "Warm"
      TemperatureZone.HOT -> if (isHindi) "गर्म (40–42°C)" else "Hot • Cooling Active"
      TemperatureZone.VERY_HOT -> if (isHindi) "बहुत गर्म (43–45°C)" else "Very Hot"
      TemperatureZone.CRITICAL -> if (isHindi) "गंभीर तापमान (46–49°C)" else "Critical Heat"
      TemperatureZone.EMERGENCY -> if (isHindi) "आपातकालीन (50°C+)" else "Emergency (50°C+)"
    }

  fun thermalLabel(thermal: ThermalLevel): String =
    when (thermal) {
      ThermalLevel.NONE -> if (isHindi) "सामान्य" else "NONE"
      ThermalLevel.LIGHT -> if (isHindi) "हल्का" else "LIGHT"
      ThermalLevel.MODERATE -> if (isHindi) "मध्यम" else "MODERATE"
      ThermalLevel.SEVERE -> if (isHindi) "अधिक" else "SEVERE"
      ThermalLevel.CRITICAL -> if (isHindi) "गंभीर" else "CRITICAL"
      ThermalLevel.EMERGENCY -> if (isHindi) "आपातकालीन" else "EMERGENCY"
      ThermalLevel.SHUTDOWN -> if (isHindi) "शटडाउन" else "SHUTDOWN"
    }

  fun locationModeLabel(mode: LocationSaverMode): String =
    when (mode) {
      LocationSaverMode.NORMAL -> if (isHindi) "सामान्य" else "Normal"
      LocationSaverMode.BATTERY_SAVER -> if (isHindi) "बैटरी सेवर" else "Battery Saver"
      LocationSaverMode.SAMPLING -> if (isHindi) "सैंपलिंग मोड" else "Sampling Mode"
      LocationSaverMode.TRIP -> if (isHindi) "ट्रिप मोड" else "Trip Mode"
    }

  val privacyPoints: List<Pair<String, String>>
    get() =
      if (isHindi) {
        listOf(
          "कोई अकाउंट या लॉगिन नहीं" to "Google साइन-इन, OTP, ईमेल या किसी भी खाते की आवश्यकता नहीं।",
          "100% ऑफ़लाइन और क्लाउड-मुक्त" to "ऐप में इंटरनेट अनुमति तक नहीं है। कोई डेटा बाहर नहीं भेजा जाता।",
          "कोई विज्ञापन या ट्रैकिंग नहीं" to "कोई एनालिटिक्स, SDK ट्रैकर या विज्ञापन शामिल नहीं हैं।",
          "बैटरी, तापमान और लोकेशन सिर्फ़ फ़ोन में" to "सभी रिकॉर्ड केवल आपके डिवाइस के Room/DataStore में रहते हैं।",
          "पूरा नियंत्रण आपके हाथ में" to "आप कभी भी सारा स्थानीय डेटा साफ़ कर सकते हैं या CSV/PDF रिपोर्ट निर्यात कर सकते हैं।",
        )
      } else {
        listOf(
          "No Account or Login Required" to "Zero sign-in, Google Auth, OTP, or email registration.",
          "100% Offline • Zero Cloud Storage" to "Netra does not even request INTERNET permission. Nothing leaves your device.",
          "No Ads & No Third-Party Tracking" to "Zero analytics SDKs, ad networks, or telemetry uploads.",
          "Battery, Heat & Location Stay Local" to "All snapshots, heat timelines, and logs live strictly inside on-device Room & DataStore.",
          "Full User Control" to "Export your local CSV/PDF report or delete all stored local data anytime with one tap.",
        )
      }
}
