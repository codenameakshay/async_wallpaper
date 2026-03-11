package com.codenameakshay.async_wallpaper

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.squareup.picasso.Picasso
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PigeonApiImpl(
  private val context: Context,
  private val ioExecutor: ExecutorService = Executors.newCachedThreadPool(),
  private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) : WallpaperApi {
  private val rotationStore = WallpaperRotationStore(context)
  private val rotationEngine = WallpaperRotationEngine(context, rotationStore)

  override fun getPlatformVersion(callback: (Result<String>) -> Unit) {
    callback(Result.success("Android ${Build.VERSION.RELEASE}"))
  }

  override fun checkMaterialYouSupport(callback: (Result<MaterialYouSupportData>) -> Unit) {
    val data = MaterialYouSupportData(
      isSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
      androidVersion = Build.VERSION.RELEASE,
      sdkInt = Build.VERSION.SDK_INT.toLong(),
    )
    callback(Result.success(data))
  }

  override fun setHomeWallpaperFromUrl(
    url: String,
    goToHome: Boolean,
    callback: (Result<Boolean>) -> Unit,
  ) {
    setWallpaperFromUrl(url, goToHome, WallpaperManager.FLAG_SYSTEM, callback)
  }

  override fun setLockWallpaperFromUrl(
    url: String,
    goToHome: Boolean,
    callback: (Result<Boolean>) -> Unit,
  ) {
    setWallpaperFromUrl(url, goToHome, WallpaperManager.FLAG_LOCK, callback)
  }

  override fun setBothWallpaperFromUrl(
    url: String,
    goToHome: Boolean,
    callback: (Result<Boolean>) -> Unit,
  ) {
    setWallpaperFromUrl(url, goToHome, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK, callback)
  }

  override fun setWallpaper(url: String, goToHome: Boolean, callback: (Result<Boolean>) -> Unit) {
    ioExecutor.execute {
      val success = runCatching {
        val intent = Intent(Intent.ACTION_SET_WALLPAPER).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        if (goToHome) {
          sendUserToHome()
        }
        true
      }.getOrElse {
        Log.e(TAG, "setWallpaper failed", it)
        false
      }
      postBoolean(callback, success)
    }
  }

  override fun setHomeWallpaperFromFile(
    filePath: String,
    goToHome: Boolean,
    callback: (Result<Boolean>) -> Unit,
  ) {
    setWallpaperFromFile(filePath, goToHome, WallpaperManager.FLAG_SYSTEM, callback)
  }

  override fun setLockWallpaperFromFile(
    filePath: String,
    goToHome: Boolean,
    callback: (Result<Boolean>) -> Unit,
  ) {
    setWallpaperFromFile(filePath, goToHome, WallpaperManager.FLAG_LOCK, callback)
  }

  override fun setBothWallpaperFromFile(
    filePath: String,
    goToHome: Boolean,
    callback: (Result<Boolean>) -> Unit,
  ) {
    setWallpaperFromFile(filePath, goToHome, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK, callback)
  }

  override fun setWallpaperFromFile(
    filePath: String,
    goToHome: Boolean,
    callback: (Result<Boolean>) -> Unit,
  ) {
    ioExecutor.execute {
      val success = runCatching {
        val uri = getImageContentUri(context, filePath)
        if (uri == null) {
          false
        } else {
          val intent = WallpaperManager.getInstance(context).getCropAndSetWallpaperIntent(uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          context.startActivity(intent)
          if (goToHome) {
            sendUserToHome()
          }
          true
        }
      }.getOrElse {
        Log.e(TAG, "setWallpaperFromFile failed", it)
        false
      }
      postBoolean(callback, success)
    }
  }

  override fun setMaterialYouWallpaper(
    url: String,
    goToHome: Boolean,
    enableEffects: Boolean,
    callback: (Result<Boolean>) -> Unit,
  ) {
    setBothWallpaperFromUrl(url, goToHome, callback)
  }

  override fun setLiveWallpaper(
    filePath: String,
    goToHome: Boolean,
    callback: (Result<Boolean>) -> Unit,
  ) {
    ioExecutor.execute {
      val success = runCatching {
        val sourceFile = File(filePath)
        if (!sourceFile.exists()) {
          false
        } else {
          val targetFile = File(context.filesDir, LIVE_WALLPAPER_FILE_NAME)
          copyFile(sourceFile, targetFile)
          VideoLiveWallpaper.setToWallpaper(context)
          if (goToHome) {
            sendUserToHome()
          }
          true
        }
      }.getOrElse {
        Log.e(TAG, "setLiveWallpaper failed", it)
        false
      }
      postBoolean(callback, success)
    }
  }

  override fun openWallpaperChooser(callback: (Result<Boolean>) -> Unit) {
    ioExecutor.execute {
      val success = runCatching {
        VideoLiveWallpaper.openWallpaperChooser(context)
        true
      }.getOrElse {
        Log.e(TAG, "openWallpaperChooser failed", it)
        false
      }
      postBoolean(callback, success)
    }
  }

  override fun startWallpaperRotation(
    config: WallpaperRotationConfigData,
    callback: (Result<Boolean>) -> Unit,
  ) {
    ioExecutor.execute {
      val success = runCatching {
        val intervalMinutes = config.intervalMinutes?.toInt() ?: 0
        if (intervalMinutes < MIN_ROTATION_INTERVAL_MINUTES) {
          false
        } else {
          val started = rotationEngine.startRotation(config)
          if (started) {
            if (config.enableIntervalTrigger == true) {
              WallpaperRotationScheduler.schedulePeriodic(context, intervalMinutes)
              rotationStore.setNextRunEpochMs(
                System.currentTimeMillis() + intervalMinutes.toLong() * 60_000L,
              )
            } else {
              WallpaperRotationScheduler.cancelPeriodic(context)
              rotationStore.setNextRunEpochMs(0L)
            }
            val needsMonitor = config.enableChargingTrigger == true || config.enableTimeOfDayTrigger == true
            if (needsMonitor) {
              Log.d(TAG, "Starting rotation monitor service")
              WallpaperRotationMonitorService.start(context)
            } else {
              WallpaperRotationMonitorService.stop(context)
            }
          }
          started
        }
      }.getOrElse {
        Log.e(TAG, "startWallpaperRotation failed", it)
        false
      }
      postBoolean(callback, success)
    }
  }

  override fun stopWallpaperRotation(callback: (Result<Boolean>) -> Unit) {
    ioExecutor.execute {
      val success = runCatching {
        WallpaperRotationScheduler.cancelPeriodic(context)
        WallpaperRotationMonitorService.stop(context)
        rotationStore.stopRotation()
        rotationEngine.clearRotationCache()
        true
      }.getOrElse {
        Log.e(TAG, "stopWallpaperRotation failed", it)
        false
      }
      postBoolean(callback, success)
    }
  }

  override fun getWallpaperRotationStatus(
    callback: (Result<WallpaperRotationStatusData>) -> Unit,
  ) {
    val status = runCatching {
      rotationStore.getStatusData()
    }.getOrElse {
      Log.e(TAG, "getWallpaperRotationStatus failed", it)
      WallpaperRotationStatusData(
        isRunning = false,
        nextRunEpochMs = 0L,
        currentIndex = 0L,
        cachedCount = 0L,
        totalCount = 0L,
        lastError = it.message,
        effectiveIntervalMinutes = 0L,
      )
    }
    callback(Result.success(status))
  }

  override fun rotateWallpaperNow(callback: (Result<Boolean>) -> Unit) {
    ioExecutor.execute {
      val success = runCatching {
        rotationEngine.applyNextWallpaper()
      }.getOrElse {
        Log.e(TAG, "rotateWallpaperNow failed", it)
        false
      }
      postBoolean(callback, success)
    }
  }

  private fun setWallpaperFromUrl(
    url: String,
    goToHome: Boolean,
    flag: Int,
    callback: (Result<Boolean>) -> Unit,
  ) {
    ioExecutor.execute {
      val success = runCatching {
        val bitmap = Picasso.get().load(url).get()
        setBitmap(bitmap, flag)
        if (goToHome) {
          sendUserToHome()
        }
        true
      }.getOrElse {
        Log.e(TAG, "setWallpaperFromUrl failed", it)
        false
      }
      postBoolean(callback, success)
    }
  }

  private fun setWallpaperFromFile(
    filePath: String,
    goToHome: Boolean,
    flag: Int,
    callback: (Result<Boolean>) -> Unit,
  ) {
    ioExecutor.execute {
      val success = runCatching {
        val bitmap = BitmapFactory.decodeFile(filePath)
        if (bitmap == null) {
          false
        } else {
          setBitmap(bitmap, flag)
          if (goToHome) {
            sendUserToHome()
          }
          true
        }
      }.getOrElse {
        Log.e(TAG, "setWallpaperFromFile failed", it)
        false
      }
      postBoolean(callback, success)
    }
  }

  private fun setBitmap(bitmap: Bitmap, flag: Int) {
    val wallpaperManager = WallpaperManager.getInstance(context)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      wallpaperManager.setBitmap(bitmap, null, true, flag)
    } else {
      wallpaperManager.setBitmap(bitmap)
    }
  }

  private fun sendUserToHome() {
    mainHandler.postDelayed({
      val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    }, GO_HOME_DELAY_MS)
  }

  private fun postBoolean(callback: (Result<Boolean>) -> Unit, value: Boolean) {
    mainHandler.post { callback(Result.success(value)) }
  }

  private fun copyFile(from: File, to: File) {
    FileInputStream(from).channel.use { input ->
      FileOutputStream(to).channel.use { output ->
        var transferred = 0L
        val size = input.size()
        while (transferred < size) {
          transferred += input.transferTo(transferred, size - transferred, output)
        }
      }
    }
  }

  private fun getImageContentUri(context: Context, absPath: String): Uri? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "wallpaper_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
      }
      val imageUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
      if (imageUri != null) {
        context.contentResolver.openOutputStream(imageUri)?.use { out ->
          FileInputStream(File(absPath)).use { input ->
            input.copyTo(out)
          }
        }
      }
      return imageUri
    }

    val values = ContentValues().apply {
      put(MediaStore.Images.Media.DATA, absPath)
    }
    return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
  }

  companion object {
    private const val TAG = "AsyncWallpaper"
    private const val GO_HOME_DELAY_MS = 1500L
    private const val LIVE_WALLPAPER_FILE_NAME = "file.mp4"
    private const val MIN_ROTATION_INTERVAL_MINUTES = 15
  }
}
