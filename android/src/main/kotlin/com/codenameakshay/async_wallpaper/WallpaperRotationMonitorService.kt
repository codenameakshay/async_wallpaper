package com.codenameakshay.async_wallpaper

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.concurrent.Executors

internal class WallpaperRotationMonitorService : Service() {
  private var isChargingReceiverRegistered = false

  private val chargingReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
      val action = intent?.action ?: return
      if (action != Intent.ACTION_POWER_CONNECTED) {
        return
      }
      Log.d(TAG, "Received ACTION_POWER_CONNECTED")
      ioExecutor.execute {
        val store = WallpaperRotationStore(applicationContext)
        if (!store.isRunning() || !store.isChargingTriggerEnabled()) {
          Log.d(TAG, "Skip charging rotation: not running or disabled")
          return@execute
        }
        val now = System.currentTimeMillis()
        if (now - store.getLastAppliedEpochMs() < CHARGING_DEBOUNCE_MS) {
          Log.d(TAG, "Skip charging rotation: debounce window")
          return@execute
        }
        val didRun = WallpaperRotationRunner.runNext(applicationContext)
        if (!didRun) {
          Log.w(TAG, "Direct charging rotation failed, queue immediate work")
          WallpaperRotationScheduler.enqueueImmediate(applicationContext)
        } else {
          Log.d(TAG, "Charging-triggered rotation applied")
        }
      }
    }
  }

  override fun onCreate() {
    super.onCreate()
    Log.d(TAG, "Rotation monitor service created")
    ensureNotificationChannel()
    startForeground(NOTIFICATION_ID, buildNotification())
    registerChargingReceiver()
    scheduleTimeOfDayAlarms()
  }

  override fun onDestroy() {
    Log.d(TAG, "Rotation monitor service destroyed")
    unregisterChargingReceiver()
    cancelTimeOfDayAlarms()
    super.onDestroy()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    return START_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun registerChargingReceiver() {
    if (isChargingReceiverRegistered) {
      return
    }
    val filter = IntentFilter(Intent.ACTION_POWER_CONNECTED)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      registerReceiver(chargingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      @Suppress("DEPRECATION")
      registerReceiver(chargingReceiver, filter)
    }
    isChargingReceiverRegistered = true
  }

  private fun unregisterChargingReceiver() {
    if (!isChargingReceiverRegistered) {
      return
    }
    runCatching { unregisterReceiver(chargingReceiver) }
    isChargingReceiverRegistered = false
  }

  private fun scheduleTimeOfDayAlarms() {
    val store = WallpaperRotationStore(this)
    if (!store.isTimeOfDayTriggerEnabled()) {
      return
    }

    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(this, WallpaperTimeOfDayReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
      this,
      TIME_OF_DAY_REQUEST_CODE,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val calendar = Calendar.getInstance().apply {
      val now = Calendar.getInstance()
      set(Calendar.HOUR_OF_DAY, store.getActiveHoursStart())
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      if (before(now)) {
        add(Calendar.DAY_OF_YEAR, 1)
      }
    }

    val intervalMs = (store.getActiveHoursEnd() - store.getActiveHoursStart()) * 60 * 60 * 1000L

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (alarmManager.canScheduleExactAlarms()) {
          alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            intervalMs,
            pendingIntent,
          )
          Log.d(TAG, "Scheduled exact time-of-day alarm")
        } else {
          alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            intervalMs,
            pendingIntent,
          )
          Log.d(TAG, "Scheduled inexact time-of-day alarm")
        }
      } else {
        alarmManager.setRepeating(
          AlarmManager.RTC_WAKEUP,
          calendar.timeInMillis,
          intervalMs,
          pendingIntent,
        )
        Log.d(TAG, "Scheduled time-of-day alarm")
      }
    } catch (e: SecurityException) {
      Log.w(TAG, "Cannot schedule exact alarms", e)
    }
  }

  private fun cancelTimeOfDayAlarms() {
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(this, WallpaperTimeOfDayReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
      this,
      TIME_OF_DAY_REQUEST_CODE,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    alarmManager.cancel(pendingIntent)
  }

  private fun ensureNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }
    val manager = getSystemService(NotificationManager::class.java)
    val channel = NotificationChannel(
      CHANNEL_ID,
      "Wallpaper Rotation",
      NotificationManager.IMPORTANCE_LOW,
    )
    manager.createNotificationChannel(channel)
  }

  private fun buildNotification(): Notification {
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("Wallpaper rotation active")
      .setContentText("Listening for charging and time triggers")
      .setSmallIcon(android.R.drawable.ic_menu_gallery)
      .setOngoing(true)
      .build()
  }

  companion object {
    private const val TAG = "RotationMonitorService"
    private const val CHANNEL_ID = "wallpaper_rotation_monitor"
    private const val NOTIFICATION_ID = 42042
    private const val TIME_OF_DAY_REQUEST_CODE = 42043
    private const val CHARGING_DEBOUNCE_MS = 30_000L
    private val ioExecutor = Executors.newSingleThreadExecutor()

    fun start(context: Context) {
      Log.d(TAG, "Starting rotation monitor service")
      val intent = Intent(context, WallpaperRotationMonitorService::class.java)
      ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
      Log.d(TAG, "Stopping rotation monitor service")
      val intent = Intent(context, WallpaperRotationMonitorService::class.java)
      context.stopService(intent)
    }
  }
}
