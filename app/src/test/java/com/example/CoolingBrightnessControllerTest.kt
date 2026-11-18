package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.system.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CoolingBrightnessControllerTest {
  private lateinit var context: Context
  private class Io : BrightnessIo {
    var b = 200; var m = 1; var permission = true; var writes = 0
    var failBrightness = false; var failMode = false
    override fun permitted() = permission
    override fun brightness() = b
    override fun mode() = m
    override fun brightness(value: Int): Boolean { writes++; if (failBrightness) return false; b = value; return true }
    override fun mode(value: Int): Boolean { writes++; if (failMode) return false; m = value; return true }
  }
  @Before fun setup() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("guardian_cooling_consent_v1", Context.MODE_PRIVATE).edit().clear().commit()
  }
  @Test fun `permission alone never opts in and off checks do not write settings`() {
    val io = Io(); val controller = CoolingBrightnessController(context, io)
    assertFalse(controller.state.value.enabled)
    repeat(3) { assertFalse(controller.reconcile(true)) }
    assertEquals(0, io.writes)
    assertEquals(200, io.b)
  }
  @Test fun `originals and consent survive recreation and are restored on cooling end`() {
    val io = Io(); val original = CoolingBrightnessController(context, io)
    original.setEnabled(true)
    assertTrue(original.reconcile(true))
    assertEquals(95, io.b); assertEquals(0, io.m)
    val restarted = CoolingBrightnessController(context, io)
    assertTrue(restarted.state.value.enabled)
    restarted.reconcile(false)
    assertEquals(200, io.b); assertEquals(1, io.m)
    assertFalse(restarted.state.value.restorePending)
  }
  @Test fun `later manual change relinquishes both fields until the cooling session ends`() {
    val io = Io(); val controller = CoolingBrightnessController(context, io)
    controller.setEnabled(true); controller.reconcile(true)
    io.b = 120
    assertFalse(controller.reconcile(true))
    assertEquals(120, io.b); assertEquals(0, io.m)
    assertFalse(controller.reconcile(true))
    controller.setEnabled(false)
    assertEquals(120, io.b); assertEquals(0, io.m)
  }
  @Test fun `permission loss keeps originals pending until explicit retry`() {
    val io = Io(); val controller = CoolingBrightnessController(context, io)
    controller.setEnabled(true); controller.reconcile(true)
    io.permission = false
    controller.setEnabled(false)
    assertTrue(controller.state.value.restorePending)
    val restarted = CoolingBrightnessController(context, io)
    io.permission = true; restarted.retryRestore()
    assertEquals(200, io.b); assertEquals(1, io.m)
    assertFalse(restarted.state.value.enabled)
    assertFalse(restarted.state.value.restorePending)
  }
  @Test fun `incomplete writes roll back and persistence failure prevents any new setting write`() {
    val io = Io(); val controller = CoolingBrightnessController(context, io)
    controller.setEnabled(true)
    io.failBrightness = true
    assertFalse(controller.reconcile(true))
    assertEquals(200, io.b); assertEquals(1, io.m)
    val blocked = CoolingBrightnessController(context, io) { false }
    val before = io.writes
    assertFalse(blocked.reconcile(true))
    assertEquals(before, io.writes)
  }
  @Test fun `unknown temperature never reports a thermal session ended`() {
    val io = Io(); val controller = CoolingBrightnessController(context, io)
    controller.setEnabled(true); controller.reconcile(true)
    controller.reconcile(null)
    assertEquals(95, io.b); assertEquals(0, io.m)
    controller.reconcile(false)
    assertEquals(200, io.b); assertEquals(1, io.m)
  }
  @Test fun `failed restore persists recovery phase across process recreation`() {
    val io = Io(); val controller = CoolingBrightnessController(context, io)
    controller.setEnabled(true); controller.reconcile(true)
    io.failMode = true; controller.setEnabled(false)
    assertEquals(200, io.b); assertEquals(0, io.m)
    assertTrue(controller.state.value.restorePending)
    io.failMode = false
    CoolingBrightnessController(context, io).retryRestore()
    assertEquals(200, io.b); assertEquals(1, io.m)
  }
  @Test fun `failed tighter target restores prior dim and original mode rather than discarding ledger`() {
    val io = Io(); val controller = CoolingBrightnessController(context, io)
    controller.setEnabled(true); assertTrue(controller.reconcile(true, 95))
    io.failBrightness = true
    assertFalse(controller.reconcile(true, 90))
    assertTrue(controller.state.value.restorePending)
    io.failBrightness = false
    CoolingBrightnessController(context, io).retryRestore()
    assertEquals(200, io.b); assertEquals(1, io.m)
  }
  @Test fun `stop restores originals without revoking consent while reset revokes it`() {
    val io = Io(); val controller = CoolingBrightnessController(context, io)
    controller.setEnabled(true); controller.reconcile(true)
    controller.retryRestore()
    assertEquals(200, io.b); assertEquals(1, io.m)
    assertTrue(controller.state.value.enabled)
    controller.reconcile(true)
    controller.setEnabled(false)
    assertFalse(CoolingBrightnessController(context, io).state.value.enabled)
    assertEquals(200, io.b); assertEquals(1, io.m)
  }

}
