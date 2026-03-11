package com.codenameakshay.async_wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import com.squareup.picasso.Picasso
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class WallpaperRotationEngine(
  context: Context,
  private val store: WallpaperRotationStore,
) {
  private val appContext = context.applicationContext
  private val wallpaperManager = WallpaperManager.getInstance(appContext)

  fun startRotation(config: WallpaperRotationConfigData): Boolean {
    val intervalMinutes = config.intervalMinutes?.toInt() ?: return false
    if (intervalMinutes < MIN_INTERVAL_MINUTES) {
      store.setLastError("Rotation interval must be at least 15 minutes.")
      return false
    }

    val preparedFiles = prepareLocalFiles(config.sources.orEmpty())
    if (preparedFiles.isEmpty()) {
      store.setLastError("No valid wallpapers available for rotation.")
      return false
    }

    val storedConfig = StoredWallpaperRotationConfig(
      localSources = preparedFiles,
      target = (config.target ?: TARGET_BOTH).toInt(),
      intervalMinutes = intervalMinutes,
      enableIntervalTrigger = config.enableIntervalTrigger == true,
      enableChargingTrigger = config.enableChargingTrigger == true,
      enableTimeOfDayTrigger = config.enableTimeOfDayTrigger == true,
      activeHoursStart = config.activeHoursStart?.toInt() ?: WallpaperRotationStore.DEFAULT_ACTIVE_HOURS_START,
      activeHoursEnd = config.activeHoursEnd?.toInt() ?: WallpaperRotationStore.DEFAULT_ACTIVE_HOURS_END,
      orderType = (config.orderType ?: WallpaperRotationStore.ORDER_TYPE_SEQUENTIAL.toLong()).toInt(),
    )
    store.saveConfig(storedConfig)

    val firstApplySuccess = applyNextWallpaper()
    if (!firstApplySuccess) {
      store.setLastError("Unable to apply initial wallpaper from playlist.")
    }
    return true
  }

  fun applyNextWallpaper(): Boolean {
    return rotationLock.withLock {
      val config = store.getConfig()
      if (config == null || config.localSources.isEmpty()) {
        store.setLastError("Rotation is not configured.")
        return@withLock false
      }

      val sourceCount = config.localSources.size
      val basePosition = positiveModulo(store.getCurrentIndex(), sourceCount)
      val order = resolveOrder(config, sourceCount)

      for (offset in 0 until sourceCount) {
        val position = (basePosition + offset) % sourceCount
        val sourceIndex = order[position]
        val path = config.localSources[sourceIndex]
        if (applyPathToWallpaper(path, config.target)) {
          val nextPosition = (position + 1) % sourceCount
          store.setCurrentIndex(nextPosition)
          if (config.orderType == WallpaperRotationStore.ORDER_TYPE_SHUFFLE && nextPosition == 0) {
            store.setShuffleOrder(generateShuffleOrder(sourceCount))
          }
          store.setLastAppliedEpochMs(System.currentTimeMillis())
          store.setLastError(null)
          return@withLock true
        }
      }

      store.setLastError("Failed to apply wallpaper from cached playlist.")
      false
    }
  }

  fun clearRotationCache() {
    getRotationCacheDirectory().deleteRecursively()
  }

  private fun resolveOrder(config: StoredWallpaperRotationConfig, sourceCount: Int): List<Int> {
    if (config.orderType != WallpaperRotationStore.ORDER_TYPE_SHUFFLE) {
      return List(sourceCount) { it }
    }
    val savedOrder = store.getShuffleOrder()
    if (savedOrder.size == sourceCount) {
      return savedOrder
    }
    val newOrder = generateShuffleOrder(sourceCount)
    store.setShuffleOrder(newOrder)
    return newOrder
  }

  private fun generateShuffleOrder(sourceCount: Int): List<Int> {
    val list = MutableList(sourceCount) { it }
    Collections.shuffle(list)
    return list
  }

  private fun prepareLocalFiles(sources: List<RotationSourceData?>): List<String> {
    val cacheDir = getRotationCacheDirectory()
    if (cacheDir.exists()) {
      cacheDir.deleteRecursively()
    }
    cacheDir.mkdirs()

    val prepared = mutableListOf<String>()
    sources.forEachIndexed { index, sourceData ->
      val source = sourceData?.source?.trim().orEmpty()
      val sourceType = sourceData?.sourceType?.toInt()
      if (source.isEmpty()) {
        return@forEachIndexed
      }
      val targetFile = File(cacheDir, "wallpaper_$index.jpg")
      val success = when (sourceType) {
        SOURCE_TYPE_URL -> cacheUrl(source, targetFile)
        SOURCE_TYPE_FILE -> copyLocalFile(source, targetFile)
        else -> false
      }
      if (success) {
        prepared.add(targetFile.absolutePath)
      }
    }
    return prepared
  }

  private fun cacheUrl(url: String, targetFile: File): Boolean {
    val targetSize = getTargetSize()
    return runCatching {
      val bitmap = Picasso.get()
        .load(url)
        .resize(targetSize.width, targetSize.height)
        .centerCrop()
        .onlyScaleDown()
        .get()
      FileOutputStream(targetFile).use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
      }
      true
    }.getOrElse {
      Log.e(TAG, "Failed to cache URL wallpaper: $url", it)
      false
    }
  }

  private fun copyLocalFile(sourcePath: String, targetFile: File): Boolean {
    val targetSize = getTargetSize()
    return runCatching {
      val sourceFile = File(sourcePath)
      if (!sourceFile.exists() || !sourceFile.canRead()) {
        false
      } else {
        val bitmap = Picasso.get()
          .load(sourceFile)
          .resize(targetSize.width, targetSize.height)
          .centerCrop()
          .onlyScaleDown()
          .get()
        FileOutputStream(targetFile).use { output ->
          bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        }
        true
      }
    }.getOrElse {
      Log.e(TAG, "Failed to copy local wallpaper: $sourcePath", it)
      false
    }
  }

  private fun applyPathToWallpaper(path: String, target: Int): Boolean {
    return runCatching {
      val flag = targetToFlag(target)
      FileInputStream(path).use { input ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          wallpaperManager.setStream(input, null, true, flag)
        } else {
          wallpaperManager.setStream(input)
        }
      }
      true
    }.getOrElse {
      Log.e(TAG, "Failed to apply cached wallpaper: $path", it)
      false
    }
  }

  private fun getTargetSize(): TargetSize {
    val width = wallpaperManager.desiredMinimumWidth.takeIf { it > 0 } ?: DEFAULT_WIDTH
    val height = wallpaperManager.desiredMinimumHeight.takeIf { it > 0 } ?: DEFAULT_HEIGHT
    return TargetSize(width = width, height = height)
  }

  private data class TargetSize(val width: Int, val height: Int)

  private fun targetToFlag(target: Int): Int {
    return when (target) {
      TARGET_HOME -> WallpaperManager.FLAG_SYSTEM
      TARGET_LOCK -> WallpaperManager.FLAG_LOCK
      else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
    }
  }

  private fun getRotationCacheDirectory(): File = File(appContext.filesDir, CACHE_DIR_NAME)

  private fun positiveModulo(value: Int, size: Int): Int {
    if (size == 0) {
      return 0
    }
    return ((value % size) + size) % size
  }

  companion object {
    private const val TAG = "WallpaperRotation"
    private const val CACHE_DIR_NAME = "wallpaper_rotation"
    private const val MIN_INTERVAL_MINUTES = 15
    private const val DEFAULT_WIDTH = 1080
    private const val DEFAULT_HEIGHT = 1920
    private const val SOURCE_TYPE_URL = 0
    private const val SOURCE_TYPE_FILE = 1
    private const val TARGET_HOME = 0
    private const val TARGET_LOCK = 1
    private const val TARGET_BOTH = 2L
    private val rotationLock = ReentrantLock()
  }
}
