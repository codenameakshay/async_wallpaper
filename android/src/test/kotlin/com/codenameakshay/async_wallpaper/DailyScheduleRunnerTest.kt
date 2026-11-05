package com.codenameakshay.async_wallpaper

import java.io.File
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DailyScheduleRunnerTest {
  @get:Rule val temporary = TemporaryFolder()
  private val utc = TimeZone.getTimeZone("UTC")
  private val noonMs = 12 * 60 * 60_000L

  @Test
  fun `stop cancels the alarm and removes cache even when preference commit fails`() {
    val preferences = MemorySharedPreferences()
    val store = DailyScheduleStore(preferences)
    val cache = temporary.newFolder("cache")
    assertTrue(store.save(schedule(cache), 0))
    var cancelled = false

    preferences.failNextCommit = true
    val stopped = stopDailySchedule(store, { cancelled = true }, { cache.deleteRecursively() })

    assertFalse(stopped)
    assertTrue(cancelled)
    assertFalse(cache.exists())
  }

  @Test
  fun `stop removes cache and clears state even when alarm cancellation throws`() {
    val store = DailyScheduleStore(MemorySharedPreferences())
    val cache = temporary.newFolder("cache")
    assertTrue(store.save(schedule(cache), 0))

    val failure = runCatching {
      stopDailySchedule(store, { error("alarm service failed") }, { cache.deleteRecursively() })
    }.exceptionOrNull()

    assertNotNull(failure)
    assertFalse(store.isRunning())
    assertFalse(cache.exists())
  }

  @Test
  fun `failed cache deletion must not report a successful stop`() {
    val store = DailyScheduleStore(MemorySharedPreferences())
    assertTrue(store.save(schedule(temporary.newFolder("cache")), 0))

    val result = runCatching { stopDailySchedule(store, {}, { false }) }

    assertFalse(result.getOrDefault(false))
    assertFalse(store.isRunning())
  }

  @Test
  fun `refresh reapplies owning entry even when last applied index matches`() {
    val store = DailyScheduleStore(MemorySharedPreferences())
    val schedule = schedule(temporary.newFolder("cache"))
    assertTrue(store.save(schedule, 0))
    val applied = mutableListOf<String>()

    val result = applyDailyScheduleCurrent(store, noonMs, utc, force = true,
      arm = { _, _, _ -> }, cancelAlarm = {}, apply = { path, _ -> applied.add(path); true },
    )

    assertEquals(DailyScheduleApplyOutcomeData.APPLIED, result.outcome)
    assertEquals(listOf(schedule.entries[0].path), applied)
  }

  @Test
  fun `missing cache fails before arming and cancels the existing alarm`() {
    val store = DailyScheduleStore(MemorySharedPreferences())
    val schedule = schedule(temporary.newFolder("cache"))
    assertTrue(store.save(schedule, 0))
    store.setNextSwitchEpochMs(99L)
    assertTrue(File(schedule.entries[1].path).delete())
    var armed = false
    var cancelled = false

    val result = applyDailyScheduleCurrent(store, noonMs, utc,
      arm = { _, _, _ -> armed = true }, cancelAlarm = { cancelled = true },
      apply = { _, _ -> error("Must not apply an incomplete cache") },
    )

    assertEquals(DailyScheduleApplyOutcomeData.FAILED, result.outcome)
    assertEquals(0L, result.entryIndex)
    assertFalse(armed)
    assertTrue(cancelled)
    assertEquals(0L, store.getNextSwitchEpochMs())
    assertTrue(store.getLastError()!!.contains("missing"))
  }

  @Test
  fun `corrupt running schedule reports failed rather than not running and cancels alarm`() {
    val preferences = MemorySharedPreferences()
    val store = DailyScheduleStore(preferences)
    assertTrue(store.save(schedule(temporary.newFolder("cache")), 0))
    assertTrue(preferences.edit().putString("entries", "not json").commit())
    var cancelled = false

    val result = applyDailyScheduleCurrent(store, noonMs, utc,
      arm = { _, _, _ -> error("Must not arm invalid state") }, cancelAlarm = { cancelled = true },
      apply = { _, _ -> error("Must not apply invalid state") },
    )

    assertEquals(DailyScheduleApplyOutcomeData.FAILED, result.outcome)
    assertTrue(cancelled)
    assertNotNull(store.getLastError())
  }

  @Test
  fun `failed applied-index commit reports failure and retries the owning wallpaper`() {
    val preferences = MemorySharedPreferences()
    val store = DailyScheduleStore(preferences)
    assertTrue(store.save(schedule(temporary.newFolder("cache")), 1))
    var applies = 0

    preferences.failNextCommit = true
    val failed = applyDailyScheduleCurrent(store, noonMs, utc,
      arm = { _, _, _ -> }, cancelAlarm = {}, apply = { _, _ -> applies++; true },
    )
    assertEquals(DailyScheduleApplyOutcomeData.FAILED, failed.outcome)
    assertEquals(1, store.getLastAppliedIndex())

    val retried = applyDailyScheduleCurrent(store, noonMs, utc,
      arm = { _, _, _ -> }, cancelAlarm = {}, apply = { _, _ -> applies++; true },
    )
    assertEquals(DailyScheduleApplyOutcomeData.APPLIED, retried.outcome)
    assertEquals(2, applies)
    assertEquals(0, store.getLastAppliedIndex())
  }

  @Test
  fun `failed rotation stop preserves previous daily state and cache`() {
    assertFailedHandoffPreservesDaily { false }
  }

  @Test
  fun `throwing rotation stop preserves previous daily state and cache`() {
    assertFailedHandoffPreservesDaily { error("rotation cancellation failed") }
  }

  @Test
  fun `status detects a missing cache before an apply is attempted`() {
    val store = DailyScheduleStore(MemorySharedPreferences())
    val schedule = schedule(temporary.newFolder("cache"))
    assertTrue(store.save(schedule, 0))
    store.setNextSwitchEpochMs(123L)
    assertTrue(File(schedule.entries[1].path).delete())

    val status = dailyScheduleStatus(store, false, noonMs, utc)

    assertEquals(WallpaperScheduleModeData.DAILY_SCHEDULE, status.mode)
    assertTrue(status.lastError!!.contains("missing"))
    assertEquals(0L, status.nextSwitchEpochMs)
  }

  @Test
  fun `status explains corrupt running preferences rather than returning silent none`() {
    val preferences = MemorySharedPreferences()
    val store = DailyScheduleStore(preferences)
    assertTrue(store.save(schedule(temporary.newFolder("cache")), 0))
    assertTrue(preferences.edit().putString("entries", "not json").commit())

    val status = dailyScheduleStatus(store, false, noonMs, utc)

    assertEquals(WallpaperScheduleModeData.NONE, status.mode)
    assertNotNull(status.lastError)
  }

  @Test
  fun `owner lookup and alarm maths use the same clock sample at a switch boundary`() {
    val store = DailyScheduleStore(MemorySharedPreferences())
    val schedule = schedule(temporary.newFolder("cache"))
    assertTrue(store.save(schedule, 1))
    val justBeforeDay = 7 * 60 * 60_000L - 1_000L
    var nextSwitch: Long? = null

    applyDailyScheduleCurrent(store, justBeforeDay, utc,
      arm = { entries, sampledNow, sampledZone ->
        nextSwitch = DailyScheduleMath.nextSwitchMs(entries.minutes, sampledNow, sampledZone)
      },
      cancelAlarm = {}, apply = { _, _ -> true },
    )

    assertEquals(7 * 60 * 60_000L, nextSwitch)
  }

  @Test
  fun `successful handoff reconciles current owner after slow rotation cancellation`() {
    val store = DailyScheduleStore(MemorySharedPreferences())
    val root = temporary.newFolder("cache")
    val generation = File(root, "generation-new").apply { mkdir() }
    val schedule = schedule(generation)
    val prepared = PreparedRotationGeneration(generation, schedule.entries.map { it.path })
    var now = 7 * 60 * 60_000L - 1_000L
    var visibleWallpaper = schedule.entries[1].path

    assertTrue(commitDailyScheduleGeneration(root, prepared, store, schedule, 1,
      stopRotation = { now += 2_000L; true },
      reconcileCurrent = {
        applyDailyScheduleCurrent(store, now, utc,
          arm = { _, _, _ -> }, cancelAlarm = {},
          apply = { path, _ -> visibleWallpaper = path; true },
        ).outcome != DailyScheduleApplyOutcomeData.FAILED
      },
    ))

    assertEquals(schedule.entries[0].path, visibleWallpaper)
    assertEquals(0, store.getLastAppliedIndex())
  }

  @Test
  fun `broadcast deadline releases pending result while enqueue executor is blocked`() {
    var queued: (() -> Unit)? = null
    var timedOut: (() -> Unit)? = null
    var finished = 0
    var enqueued = false
    dispatchDailyScheduleBroadcast(
      execute = { queued = it }, deadline = { timedOut = it },
      enqueueRecovery = { enqueued = true }, finish = { finished++ },
    )

    assertNotNull(timedOut)
    timedOut!!()
    assertEquals(1, finished)
    queued!!()
    assertTrue(enqueued)
    assertEquals(1, finished)
  }

  @Test
  fun `broadcast applies at once and skips the deferred job when the apply succeeds`() {
    var enqueued = 0
    var finished = 0
    var applied = 0
    dispatchDailyScheduleBroadcast(
      execute = { it() }, deadline = {},
      enqueueRecovery = { enqueued++ }, finish = { finished++ },
      applyNow = { applied++; true },
    )

    assertEquals(1, applied)
    assertEquals(0, enqueued)
    assertEquals(1, finished)
  }

  @Test
  fun `broadcast falls back to the deferred job when the lock stays busy or the apply throws`() {
    var enqueued = 0
    dispatchDailyScheduleBroadcast(
      execute = { it() }, deadline = {}, enqueueRecovery = { enqueued++ }, finish = {},
      applyNow = { false },
    )
    dispatchDailyScheduleBroadcast(
      execute = { it() }, deadline = {}, enqueueRecovery = { enqueued++ }, finish = {},
      applyNow = { error("decode failed") },
    )

    assertEquals(2, enqueued)
  }

  @Test
  fun `a failed or blocked broadcast apply leaves the switch to the durable job`() {
    assertTrue(broadcastApplyHandled(DailyScheduleApplyOutcomeData.APPLIED))
    assertTrue(broadcastApplyHandled(DailyScheduleApplyOutcomeData.ALREADY_CURRENT))
    assertTrue(broadcastApplyHandled(DailyScheduleApplyOutcomeData.NOT_RUNNING))
    assertFalse(broadcastApplyHandled(DailyScheduleApplyOutcomeData.FAILED))
    assertFalse(broadcastApplyHandled(null))
  }

  @Test
  fun `tryWithLock gives up when another thread holds the coordinator`() {
    val held = CountDownLatch(1)
    val release = CountDownLatch(1)
    val holder = Thread {
      WallpaperRotationCoordinator.withLock {
        held.countDown()
        release.await(5, TimeUnit.SECONDS)
      }
    }.apply { start() }
    try {
      assertTrue(held.await(5, TimeUnit.SECONDS))
      assertEquals(null, WallpaperRotationCoordinator.tryWithLock(50L) { "ran" })
    } finally {
      release.countDown()
      holder.join(5_000)
    }
    assertEquals("ran", WallpaperRotationCoordinator.tryWithLock(50L) { "ran" })
  }

  @Test
  fun `broadcast rejection still finishes the pending result exactly once`() {
    var timedOut: (() -> Unit)? = null
    var finished = 0
    runCatching {
      dispatchDailyScheduleBroadcast(
        execute = { error("executor stopped") }, deadline = { timedOut = it },
        enqueueRecovery = {}, finish = { finished++ },
      )
    }

    assertEquals(1, finished)
    timedOut?.invoke()
    assertEquals(1, finished)
  }

  @Test
  fun `failed daily replacement restores the visible wallpaper as well as preferences`() {
    val store = DailyScheduleStore(MemorySharedPreferences())
    val root = temporary.newFolder("cache")
    val previousDirectory = File(root, "generation-old").apply { mkdir() }
    val previous = schedule(previousDirectory)
    assertTrue(store.save(previous, 0))
    val newDirectory = File(root, "generation-new").apply { mkdir() }
    val replacement = schedule(newDirectory)
    val prepared = PreparedRotationGeneration(newDirectory, replacement.entries.map { it.path })
    var visibleWallpaper = replacement.entries[0].path

    assertFalse(commitDailyScheduleGeneration(root, prepared, store, replacement, 0,
      stopRotation = { false },
      restoreCurrent = {
        applyDailyScheduleCurrent(store, noonMs, utc, force = true,
          arm = { _, _, _ -> }, cancelAlarm = {},
          apply = { path, _ -> visibleWallpaper = path; true },
        ).outcome != DailyScheduleApplyOutcomeData.FAILED
      },
    ))

    assertEquals(previous.entries[0].path, visibleWallpaper)
  }

  @Test
  fun `failed current reconciliation rolls back before pruning previous generation`() {
    listOf<() -> Boolean>({ false }, { error("alarm service failed") }).forEachIndexed { index, reconcile ->
      val store = DailyScheduleStore(MemorySharedPreferences())
      val root = temporary.newFolder("cache-$index")
      val old = schedule(File(root, "generation-old").apply { mkdir() })
      assertTrue(store.save(old, 0))
      val newDirectory = File(root, "generation-new").apply { mkdir() }
      val replacement = schedule(newDirectory)
      var restored = false

      val started = commitDailyScheduleGeneration(root,
        PreparedRotationGeneration(newDirectory, replacement.entries.map { it.path }),
        store, replacement, 0, stopRotation = { true }, reconcileCurrent = reconcile,
        restoreCurrent = { restored = true; true },
      )

      assertFalse(started)
      assertEquals(old, store.getSchedule())
      assertTrue(old.entries.all { File(it.path).isFile })
      assertTrue(restored)
    }
  }

  @Test
  fun `failed wallpaper restoration invalidates last applied index for manual retry`() {
    val store = DailyScheduleStore(MemorySharedPreferences())
    val root = temporary.newFolder("cache")
    val old = schedule(File(root, "generation-old").apply { mkdir() })
    assertTrue(store.save(old, 0))
    val newDirectory = File(root, "generation-new").apply { mkdir() }
    val replacement = schedule(newDirectory)

    assertFalse(commitDailyScheduleGeneration(root,
      PreparedRotationGeneration(newDirectory, replacement.entries.map { it.path }),
      store, replacement, 0, stopRotation = { false }, restoreCurrent = { false },
    ))

    assertEquals(null, store.getLastAppliedIndex())
    assertTrue(store.getLastError()!!.contains("restore"))
    val applied = applyDailyScheduleCurrent(store, noonMs, utc,
      arm = { _, _, _ -> }, cancelAlarm = {}, apply = { _, _ -> true },
    )
    assertEquals(DailyScheduleApplyOutcomeData.APPLIED, applied.outcome)
  }

  @Test
  fun `failed configuration save retains candidate files and never stops rotation`() {
    val preferences = MemorySharedPreferences()
    val store = DailyScheduleStore(preferences)
    val root = temporary.newFolder("cache")
    val previous = schedule(File(root, "generation-old").apply { mkdir() })
    assertTrue(store.save(previous, 0))
    val newDirectory = File(root, "generation-new").apply { mkdir() }
    val replacement = schedule(newDirectory)

    preferences.failNextCommit = true
    assertFalse(commitDailyScheduleGeneration(root,
      PreparedRotationGeneration(newDirectory, replacement.entries.map { it.path }),
      store, replacement, 0, stopRotation = { error("Must not stop rotation on failed save") },
    ))

    assertEquals(previous, store.getSchedule())
    assertTrue(previous.entries.all { File(it.path).isFile })
    assertTrue(replacement.entries.all { File(it.path).isFile })
  }

  @Test
  fun `broadcast can finish while its background enqueue is already blocked`() {
    val executor = Executors.newSingleThreadExecutor()
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    val completed = CountDownLatch(1)
    var timeout: (() -> Unit)? = null
    val finishes = java.util.concurrent.atomic.AtomicInteger()
    try {
      dispatchDailyScheduleBroadcast(
        execute = { executor.execute(it) }, deadline = { timeout = it },
        enqueueRecovery = { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) },
        finish = { finishes.incrementAndGet(); completed.countDown() },
      )
      assertTrue(entered.await(5, TimeUnit.SECONDS))
      timeout!!()
      assertTrue(completed.await(5, TimeUnit.SECONDS))
      assertEquals(1, finishes.get())
      release.countDown()
      executor.submit {}.get(5, TimeUnit.SECONDS)
      assertEquals(1, finishes.get())
    } finally {
      release.countDown()
      executor.shutdownNow()
    }
  }

  @Test
  fun `durable recovery after stop is successful without applying or rearming`() {
    val store = DailyScheduleStore(MemorySharedPreferences())
    val cache = temporary.newFolder("cache")
    assertTrue(store.save(schedule(cache), 0))
    assertTrue(stopDailySchedule(store, {}, { cache.deleteRecursively() }))

    assertTrue(recoverDailySchedule {
      applyDailyScheduleCurrent(store, noonMs, utc, force = true,
        arm = { _, _, _ -> error("Stopped recovery must not rearm") }, cancelAlarm = {},
        apply = { _, _ -> error("Stopped recovery must not apply") },
      ).also { assertEquals(DailyScheduleApplyOutcomeData.NOT_RUNNING, it.outcome) }
    })
    assertFalse(cache.exists())
    assertFalse(store.isRunning())
  }

  @Test
  fun `durable recovery retries thrown infrastructure failure but not a missing cache`() {
    assertFalse(recoverDailySchedule { error("alarm service failed") })
    val store = DailyScheduleStore(MemorySharedPreferences())
    val schedule = schedule(temporary.newFolder("cache"))
    assertTrue(store.save(schedule, 0))
    assertTrue(File(schedule.entries[0].path).delete())

    assertTrue(recoverDailySchedule {
      applyDailyScheduleCurrent(store, noonMs, utc, force = true,
        arm = { _, _, _ -> error("Missing cache must not rearm") }, cancelAlarm = {},
        apply = { _, _ -> error("Missing cache must not apply") },
      ).also { assertEquals(DailyScheduleApplyOutcomeData.FAILED, it.outcome) }
    })
  }

  @Test
  fun `rollback to empty state cancels an alarm armed by failed reconciliation`() {
    val store = DailyScheduleStore(MemorySharedPreferences())
    val root = temporary.newFolder("cache")
    val directory = File(root, "generation-new").apply { mkdir() }
    val schedule = schedule(directory)
    var armed = false

    assertFalse(commitDailyScheduleGeneration(root,
      PreparedRotationGeneration(directory, schedule.entries.map { it.path }),
      store, schedule, 0, stopRotation = { true },
      reconcileCurrent = { armed = true; false },
      restoreCurrent = {
        applyDailyScheduleCurrent(store, noonMs, utc, force = true,
          arm = { _, _, _ -> armed = true }, cancelAlarm = { armed = false },
          apply = { _, _ -> error("No previous schedule must not apply") },
        ).outcome == DailyScheduleApplyOutcomeData.NOT_RUNNING
      },
    ))

    assertFalse(armed)
    assertFalse(store.isRunning())
  }

  @Test
  fun `wrongly typed persisted schedule fails and cancels rather than requesting endless recovery`() {
    val preferences = MemorySharedPreferences()
    val store = DailyScheduleStore(StrictSchedulePreferences(preferences))
    assertTrue(store.save(schedule(temporary.newFolder("cache")), 0))
    assertTrue(preferences.edit().putInt("entries", 99).commit())
    var cancelled = false

    assertTrue(recoverDailySchedule {
      applyDailyScheduleCurrent(store, noonMs, utc, force = true,
        arm = { _, _, _ -> error("Invalid prefs must not arm") },
        cancelAlarm = { cancelled = true },
        apply = { _, _ -> error("Invalid prefs must not apply") },
      ).also { assertEquals(DailyScheduleApplyOutcomeData.FAILED, it.outcome) }
    })
    assertTrue(cancelled)
    assertTrue(store.getLastError()!!.contains("invalid"))
  }

  @Test
  fun `failed daily deactivation for rotation retains cache for snapshot rollback`() {
    val preferences = MemorySharedPreferences()
    val store = DailyScheduleStore(preferences)
    val cache = temporary.newFolder("cache")
    val previous = schedule(cache)
    assertTrue(store.save(previous, 0))
    val snapshot = store.snapshot()
    var cancelled = false

    preferences.failNextCommit = true
    assertFalse(suspendDailyScheduleForRotation(store,
      cancelAlarm = { cancelled = true },
    ))

    assertTrue(cancelled)
    assertTrue(previous.entries.all { File(it.path).isFile })
    assertTrue(store.restore(snapshot))
    val restored = applyDailyScheduleCurrent(store, noonMs, utc, force = true,
      arm = { _, _, _ -> }, cancelAlarm = {}, apply = { path, _ -> File(path).isFile },
    )
    assertEquals(DailyScheduleApplyOutcomeData.APPLIED, restored.outcome)
    assertEquals(previous, store.getSchedule())
  }

  @Test
  fun `successful daily deactivation preserves cache until rotation handoff is committed`() {
    val store = DailyScheduleStore(MemorySharedPreferences())
    val cache = temporary.newFolder("cache")
    assertTrue(store.save(schedule(cache), 0))
    var cancelled = false

    assertTrue(suspendDailyScheduleForRotation(store,
      cancelAlarm = { cancelled = true },
    ))

    assertTrue(cancelled)
    assertFalse(store.isRunning())
    assertTrue(cache.isDirectory)
  }

  @Test
  fun `stop waits for receiver apply then removes cache and no later apply rearms`() {
    val store = DailyScheduleStore(MemorySharedPreferences())
    val cache = temporary.newFolder("cache")
    assertTrue(store.save(schedule(cache), 1))
    val executor = Executors.newFixedThreadPool(2)
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    var armed = false
    try {
      val applying = executor.submit {
        WallpaperRotationCoordinator.withLock {
          applyDailyScheduleCurrent(store, noonMs, utc,
            arm = { _, _, _ -> armed = true }, cancelAlarm = { armed = false },
            apply = { _, _ -> entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); true },
          )
        }
      }
      assertTrue(entered.await(5, TimeUnit.SECONDS))
      val stopped = executor.submit<Boolean> {
        WallpaperRotationCoordinator.withLock {
          stopDailySchedule(store, { armed = false }, { cache.deleteRecursively() })
        }
      }
      release.countDown()
      applying.get(5, TimeUnit.SECONDS)
      assertTrue(stopped.get(5, TimeUnit.SECONDS))
      assertFalse(armed)
      assertFalse(cache.exists())
      val staleAlarm = WallpaperRotationCoordinator.withLock {
        applyDailyScheduleCurrent(store, noonMs, utc,
          arm = { _, _, _ -> armed = true }, cancelAlarm = { armed = false },
          apply = { _, _ -> error("Stopped receiver must not apply") },
        )
      }
      assertEquals(DailyScheduleApplyOutcomeData.NOT_RUNNING, staleAlarm.outcome)
      assertFalse(armed)
    } finally {
      release.countDown()
      executor.shutdownNow()
    }
  }

  private fun assertFailedHandoffPreservesDaily(stopRotation: () -> Boolean) {
    val preferences = MemorySharedPreferences()
    val store = DailyScheduleStore(preferences)
    val root = temporary.newFolder("cache")
    val oldDirectory = File(root, "generation-old").apply { mkdir() }
    val previous = schedule(oldDirectory)
    assertTrue(store.save(previous, 1))
    store.setNextSwitchEpochMs(456L)
    val newDirectory = File(root, "generation-new").apply { mkdir() }
    val replacement = schedule(newDirectory)
    val prepared = PreparedRotationGeneration(newDirectory, replacement.entries.map { it.path })

    val started = commitDailyScheduleGeneration(root, prepared, store, replacement, 0,
      stopRotation = stopRotation,
    )

    assertFalse(started)
    assertEquals(previous, store.getSchedule())
    assertEquals(1, store.getLastAppliedIndex())
    assertEquals(456L, store.getNextSwitchEpochMs())
    assertEquals(previous, DailyScheduleStore(preferences.reopen()).getSchedule())
    assertTrue(previous.entries.all { File(it.path).isFile })
  }

  private fun schedule(directory: File) = StoredDailySchedule(
    listOf(
      DailyScheduleEntry(7 * 60, File(directory, "day.jpg").apply { writeText("day") }.absolutePath),
      DailyScheduleEntry(19 * 60, File(directory, "night.jpg").apply { writeText("night") }.absolutePath),
    ),
    2,
  )
}
