package com.example

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.prefs.PreferencesRepository
import com.example.data.local.prefs.UserPreferences
import com.example.domain.model.AppLanguage
import com.example.domain.model.ChargingSpeedClass
import com.example.service.NotificationHelper
import com.example.voice.ChargingVoiceAssistant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ChargingEnergyVoiceTest {
  private val app get() = ApplicationProvider.getApplicationContext<Application>()
  private fun assistant() = ChargingVoiceAssistant(app, NotificationHelper(app))
  private fun field(name: String) = ChargingVoiceAssistant::class.java.getDeclaredField(name).apply { isAccessible = true }

  @Test fun independentToggleDefaultsOnAndSavedOffSurvivesRepositoryRecreation() = runBlocking {
    val r = PreferencesRepository(app)
    assertTrue(UserPreferences().announceChargingSpeed)
    r.updateAnnounceCharging(false)
    r.updateAnnounceChargingSpeed(false)
    assertFalse(PreferencesRepository(app).preferences.first().announceChargingSpeed)
    r.updateAnnounceCharging(true)
    assertFalse(r.preferences.first().announceChargingSpeed)
    r.updateAnnounceChargingSpeed(true)
    assertTrue(r.preferences.first().announceChargingSpeed)
  }

  @Test fun typePhrasesAreShortAndUnsupportedClassesHaveNoPhrase() {
    val a=assistant()
    val expected=mapOf(ChargingSpeedClass.SLOW to "Slow charging", ChargingSpeedClass.STANDARD to "Normal charging",
      ChargingSpeedClass.FAST to "Fast charging", ChargingSpeedClass.ULTRA_FAST to "Ultra Fast charging",
      ChargingSpeedClass.HYPER_FAST to "Hyper Fast charging", ChargingSpeedClass.EXTREME_FAST to "Extreme Fast charging")
    for((speed,text) in expected) assertEquals(text,a.speedPhrase(speed,AppLanguage.ENGLISH))
    for(speed in ChargingSpeedClass.entries.filter { it !in expected }) assertNull(a.speedPhrase(speed,AppLanguage.ENGLISH))
    a.shutdown()
  }

  @Test fun optionalTypeSpeechRemainsDeferredInAllModesUntilMeasuredBudgetExists() {
    val a=assistant()
    repeat(10) { a.observeChargingSpeed(ChargingSpeedClass.FAST,UserPreferences(nightSilentEnabled=false),true,System.nanoTime()/1_000_000L) }
    assertFalse(a.assistantState.value.isTtsBound)
    assertEquals(0,a.assistantState.value.announcementCount)
    a.shutdown()
  }

  @Test fun staleLazyInitSpeedNeverDispatchesOrBecomesFallback() {
    val a=assistant()
    field("currentSpeedKey").set(a,"charging_speed_FAST")
    field("speedObservedAtElapsedMillis").set(a,System.nanoTime()/1_000_000L-20_000)
    a.speak("charging_speed_FAST","Fast charging",UserPreferences(nightSilentEnabled=false),false,false,true,true,"Netra Charging")
    assertEquals(0,a.assistantState.value.announcementCount)
    assertFalse(a.assistantState.value.isTtsBound)
    a.onInit(TextToSpeech.ERROR)
    assertNull(field("pendingUtterance").get(a))
    a.shutdown()
  }

  @Test fun budgetOrToggleOffDropsPendingTypeWithoutASecondQueue() {
    val a=assistant()
    val clazz=Class.forName("com.example.voice.ChargingVoiceAssistant\$PendingUtterance")
    val ctor=clazz.declaredConstructors.first().apply { isAccessible=true }
    field("currentSpeedKey").set(a,"charging_speed_FAST")
    field("pendingUtterance").set(a,ctor.newInstance("charging_speed_FAST","Fast charging",UserPreferences(),false,"Netra Charging"))
    a.observeChargingSpeed(ChargingSpeedClass.UNAVAILABLE,UserPreferences(announceChargingSpeed=false),true,System.nanoTime()/1_000_000L)
    assertNull(field("pendingUtterance").get(a))
    assertNull(field("currentSpeedKey").get(a))
    a.shutdown()
  }

  @Test fun plugEventCannotSmuggleSpeedWhenIndependentToggleIsOff() {
    val a=assistant()
    a.announceChargerConnected(ChargingSpeedClass.EXTREME_FAST,UserPreferences(announceChargingSpeed=false,nightSilentEnabled=false),true)
    assertEquals("Charger connected.",a.assistantState.value.lastSpokenMessage)
    a.shutdown()
  }
}
