package com.codenameakshay.async_wallpaper

import android.app.ActivityManager
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** Android-only capability probes shared by the structured wallpaper endpoints. */
object AndroidCapabilities {
  data class StaticWallpaperSupport(
    val wallpaperSupported: Boolean,
    val settingAllowed: Boolean,
  ) {
    val canApplyDirectly: Boolean
      get() = wallpaperSupported && settingAllowed
  }

  fun snapshot(context: Context): WallpaperCapabilitiesData {
    val appContext = context.applicationContext
    val staticSupport = staticWallpaperSupport(appContext)
    val packageManager = appContext.packageManager
    val hasLiveWallpaperFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER)
    val canOpenLiveWallpaperFlow = resolves(
      packageManager,
      Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER),
    )
    val hasVideoService = hasWallpaperService(appContext, VideoLiveWallpaper::class.java)
    val hasOpenGlService = hasWallpaperService(appContext, OpenGlLiveWallpaper::class.java)
    val supportsLive = hasLiveWallpaperFeature && canOpenLiveWallpaperFlow && hasVideoService
    val supportsOpenGl = hasLiveWallpaperFeature && canOpenLiveWallpaperFlow && hasOpenGlService &&
      OpenGlLiveWallpaper.isOpenGlEs2Supported(appContext)
    val hasPicker = resolves(packageManager, Intent(Intent.ACTION_SET_WALLPAPER)) ||
      resolves(packageManager, Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
    val glVersion = openGlVersion(appContext)

    return WallpaperCapabilitiesData(
      supportsStaticWallpaper = staticSupport.canApplyDirectly,
      supportsLiveWallpaper = supportsLive,
      supportsOpenGlLiveWallpaper = supportsOpenGl,
      supportsHomeWallpaper = staticSupport.canApplyDirectly,
      supportsLockWallpaper = staticSupport.canApplyDirectly && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N,
      supportsBothWallpapers = staticSupport.canApplyDirectly && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N,
      canSetWallpaper = staticSupport.canApplyDirectly,
      hasSystemWallpaperPicker = hasPicker,
      // Direct bitmap and preparation work do not require an Activity. Any system UI path does.
      requiresForeground = hasPicker || canOpenLiveWallpaperFlow,
      manufacturer = Build.MANUFACTURER ?: "Unknown",
      sdkInt = Build.VERSION.SDK_INT.toLong(),
      openGlVersion = glVersion,
      // Android exposes the requested GLES version without creating an EGL context. Do not
      // manufacture a renderer string that would be misleading on devices with multiple GPUs.
      openGlRenderer = null,
    )
  }

  fun staticWallpaperSupport(context: Context): StaticWallpaperSupport {
    val manager = WallpaperManager.getInstance(context.applicationContext)
    val apiLevel = Build.VERSION.SDK_INT
    val supported = AndroidWallpaperApiPolicy.wallpaperSupported(
      apiLevel,
      if (apiLevel >= Build.VERSION_CODES.M) {
        runCatching { manager.isWallpaperSupported }.getOrNull()
      } else {
        null
      },
    )
    val allowed = AndroidWallpaperApiPolicy.settingAllowed(
      apiLevel,
      if (apiLevel >= Build.VERSION_CODES.N) {
        runCatching { manager.isSetWallpaperAllowed }.getOrNull()
      } else {
        null
      },
    )
    return StaticWallpaperSupport(supported, allowed)
  }

  fun hasVideoLiveWallpaper(context: Context): Boolean {
    val appContext = context.applicationContext
    return appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER) &&
      resolves(appContext.packageManager, Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)) &&
      hasWallpaperService(appContext, VideoLiveWallpaper::class.java)
  }

  fun hasOpenGlLiveWallpaper(context: Context): Boolean {
    val appContext = context.applicationContext
    return appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER) &&
      resolves(appContext.packageManager, Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)) &&
      hasWallpaperService(appContext, OpenGlLiveWallpaper::class.java) &&
      OpenGlLiveWallpaper.isOpenGlEs2Supported(appContext)
  }

  fun resolves(packageManager: PackageManager, intent: Intent): Boolean {
    return runCatching {
      packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
    }.getOrDefault(false)
  }

  private fun hasWallpaperService(context: Context, serviceClass: Class<*>): Boolean {
    return runCatching {
      context.packageManager.getServiceInfo(
        ComponentName(context, serviceClass),
        0,
      )
      true
    }.getOrDefault(false)
  }

  private fun openGlVersion(context: Context): String? {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val version = manager?.deviceConfigurationInfo?.reqGlEsVersion ?: return null
    if (version == 0) {
      return null
    }
    val major = version shr 16
    val minor = version and 0xffff
    return "$major.$minor"
  }
}

/**
 * Framework-independent compatibility policy for the wallpaper APIs added after Android L.
 *
 * Pre-M has no `isWallpaperSupported` probe, and pre-N has no `isSetWallpaperAllowed` probe;
 * both platforms still support the legacy home-wallpaper `setBitmap(Bitmap)` path. Lock and
 * combined target flags were introduced in N, so they are rejected before a pre-N home write can
 * accidentally occur.
 */
object AndroidWallpaperApiPolicy {
  private const val API_M = 23
  private const val API_N = 24

  fun wallpaperSupported(apiLevel: Int, frameworkValue: Boolean?): Boolean {
    return if (apiLevel < API_M) true else frameworkValue == true
  }

  fun settingAllowed(apiLevel: Int, frameworkValue: Boolean?): Boolean {
    return if (apiLevel < API_N) true else frameworkValue == true
  }

  /** Returns a truthful no-mutation result when the requested target needs Android N. */
  fun unsupportedTargetResult(
    apiLevel: Int,
    target: WallpaperTargetData,
  ): OperationResultData? {
    if (apiLevel >= API_N || target == WallpaperTargetData.HOME) {
      return null
    }
    return when (target) {
      WallpaperTargetData.LOCK -> OperationResultPolicy.unsupported(
        target,
        ERROR_LOCK_TARGET_UNSUPPORTED,
        "Lock-screen wallpaper is not supported before Android N.",
      )
      WallpaperTargetData.BOTH -> OperationResultData(
        status = OperationStatusData.UNSUPPORTED,
        requestedTarget = target,
        // Do not try the legacy home-only API once the lock half cannot be honored.
        home = OperationResultPolicy.notAttemptedTarget(),
        lock = OperationResultPolicy.unsupportedTarget(
          ERROR_LOCK_TARGET_UNSUPPORTED,
          "Lock-screen wallpaper is not supported before Android N.",
        ),
        errorCode = ERROR_LOCK_TARGET_UNSUPPORTED,
        errorMessage = "Home and lock wallpaper cannot both be applied before Android N.",
        fallbackUsed = false,
      )
      WallpaperTargetData.HOME -> null
    }
  }

  const val ERROR_LOCK_TARGET_UNSUPPORTED = "lock-wallpaper-unsupported"
}
