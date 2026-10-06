package com.codenameakshay.async_wallpaper

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperRotationStoreTest {
  @Test
  fun `failed synchronous config commit restores memory and leaves fresh state on old config`() {
    val preferences = MemorySharedPreferences()
    val store = WallpaperRotationStore(preferences)
    val oldConfig = config("/files/wallpaper_rotation/wallpaper_0.jpg", target = 0)
    val newConfig = config("/files/wallpaper_rotation/generation-new/wallpaper_0.jpg", target = 1)
    assertTrue(store.saveConfig(oldConfig))

    preferences.failNextCommit = true
    val saved = store.saveConfig(newConfig)

    assertFalse(saved)
    assertEquals(oldConfig.localSources, store.getConfig()!!.localSources)
    assertEquals(oldConfig.target, store.getConfig()!!.target)
    val reconstructed = WallpaperRotationStore(preferences.reopen())
    assertEquals(oldConfig.localSources, reconstructed.getConfig()!!.localSources)
    assertEquals(oldConfig.target, reconstructed.getConfig()!!.target)
  }

  @Test
  fun `config and pending schedule marker commit together and stop writes pending intent`() {
    val preferences = MemorySharedPreferences()
    val store = WallpaperRotationStore(preferences)

    assertTrue(store.saveConfig(config("/files/generation-one/wallpaper.jpg", target = 0)))
    assertTrue(store.areSchedulesPending())
    assertTrue(store.clearSchedulesPending())
    assertFalse(store.areSchedulesPending())

    assertTrue(store.stopRotation())
    assertFalse(store.isRunning())
    assertTrue(store.areSchedulesPending())
    val reconstructed = WallpaperRotationStore(preferences.reopen())
    assertFalse(reconstructed.isRunning())
    assertTrue(reconstructed.areSchedulesPending())
  }

  @Test
  fun `failed stop commit restores running config in memory and after reconstruction`() {
    val preferences = MemorySharedPreferences()
    val store = WallpaperRotationStore(preferences)
    val config = config("/files/generation-running/wallpaper.jpg", target = 1)
    assertTrue(store.saveConfig(config))
    assertTrue(store.clearSchedulesPending())

    preferences.failNextCommit = true
    assertFalse(store.stopRotation())

    assertTrue(store.isRunning())
    assertFalse(store.areSchedulesPending())
    assertEquals(config.localSources, store.getConfig()!!.localSources)
    val reconstructed = WallpaperRotationStore(preferences.reopen())
    assertTrue(reconstructed.isRunning())
    assertFalse(reconstructed.areSchedulesPending())
    assertEquals(config.localSources, reconstructed.getConfig()!!.localSources)
  }

  @Test
  fun `failed pending marker clear remains pending in memory and after reconstruction`() {
    val preferences = MemorySharedPreferences()
    val store = WallpaperRotationStore(preferences)
    assertTrue(store.saveConfig(config("/files/generation-one/wallpaper.jpg", target = 0)))

    preferences.failNextCommit = true
    assertFalse(store.clearSchedulesPending())

    assertTrue(store.areSchedulesPending())
    assertTrue(WallpaperRotationStore(preferences.reopen()).areSchedulesPending())
  }

  @Test
  fun `pending repair uses latest persisted config even when triggered by stale generation`() {
    val preferences = MemorySharedPreferences()
    val store = WallpaperRotationStore(preferences)
    assertTrue(store.saveConfig(config("/files/generation-old/wallpaper.jpg", target = 0)))
    val staleWorkerGeneration = store.getGeneration()
    assertTrue(store.clearSchedulesPending())
    val currentConfig = config("/files/generation-current/wallpaper.jpg", target = 1)
    assertTrue(store.saveConfig(currentConfig))
    assertTrue(store.getGeneration() != staleWorkerGeneration)
    var reconciled: StoredWallpaperRotationConfig? = null

    val recovered = recoverPendingRotationSchedules(store) { latestConfig ->
      reconciled = latestConfig
    }

    assertTrue(recovered)
    assertEquals(currentConfig.localSources, reconciled!!.localSources)
    assertEquals(currentConfig.target, reconciled!!.target)
    assertFalse(store.areSchedulesPending())
  }

  @Test
  fun `pending stop repair reconciles cancellation and does not need a playlist config`() {
    val preferences = MemorySharedPreferences()
    val store = WallpaperRotationStore(preferences)
    assertTrue(store.saveConfig(config("/files/generation-old/wallpaper.jpg", target = 0)))
    assertTrue(store.stopRotation())
    var receivedStoppedConfig = false

    val recovered = recoverPendingRotationSchedules(store) { currentConfig ->
      receivedStoppedConfig = currentConfig == null
    }

    assertTrue(recovered)
    assertTrue(receivedStoppedConfig)
    assertFalse(store.isRunning())
    assertFalse(store.areSchedulesPending())
  }

  @Test
  fun `failed pending repair preserves marker for a fresh process retry`() {
    val preferences = MemorySharedPreferences()
    val store = WallpaperRotationStore(preferences)
    assertTrue(store.saveConfig(config("/files/generation-current/wallpaper.jpg", target = 1)))

    val error = runCatching {
      recoverPendingRotationSchedules(store) { error("WorkManager operation failed") }
    }.exceptionOrNull()

    assertTrue(error is IllegalStateException)
    assertTrue(store.areSchedulesPending())
    assertTrue(WallpaperRotationStore(preferences.reopen()).areSchedulesPending())
  }

  private fun config(path: String, target: Int) = StoredWallpaperRotationConfig(
    localSources = listOf(path),
    requestedSourceCount = 1,
    target = target,
    intervalMinutes = 60,
    enableIntervalTrigger = true,
    enableChargingTrigger = false,
    enableTimeOfDayTrigger = false,
    activeHoursStart = 6,
    activeHoursEnd = 23,
    orderType = WallpaperRotationStore.ORDER_TYPE_SEQUENTIAL,
  )
}

private class MemorySharedPreferences(
  private val diskValues: MutableMap<String, Any?> = mutableMapOf(),
) : SharedPreferences {
  private val values = diskValues.toMutableMap()
  var failNextCommit = false

  override fun getAll(): MutableMap<String, *> = values.toMutableMap()
  override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
  override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
    (values[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues
  override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
  override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
  override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
  override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
  override fun contains(key: String): Boolean = values.containsKey(key)
  override fun edit(): SharedPreferences.Editor = MemoryEditor()
  override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
  override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

  fun reopen() = MemorySharedPreferences(diskValues.toMutableMap())

  private inner class MemoryEditor : SharedPreferences.Editor {
    private val changes = mutableMapOf<String, Any?>()
    private var clears = false

    override fun putString(key: String, value: String?): SharedPreferences.Editor = change(key, value)
    override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor =
      change(key, values?.toSet())
    override fun putInt(key: String, value: Int): SharedPreferences.Editor = change(key, value)
    override fun putLong(key: String, value: Long): SharedPreferences.Editor = change(key, value)
    override fun putFloat(key: String, value: Float): SharedPreferences.Editor = change(key, value)
    override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = change(key, value)
    override fun remove(key: String): SharedPreferences.Editor = change(key, null)
    override fun clear(): SharedPreferences.Editor = apply { clears = true }

    override fun commit(): Boolean {
      update(values)
      if (failNextCommit) {
        failNextCommit = false
        return false
      }
      update(diskValues)
      return true
    }

    override fun apply() {
      update(values)
      update(diskValues)
    }

    private fun change(key: String, value: Any?) = apply {
      changes[key] = value
    }

    private fun update(destination: MutableMap<String, Any?>) {
      if (clears) destination.clear()
      changes.forEach { (key, value) ->
        if (value == null) destination.remove(key) else destination[key] = value
      }
    }
  }
}
