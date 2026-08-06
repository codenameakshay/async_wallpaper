package com.codenameakshay.async_wallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.squareup.picasso.Picasso
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Android implementation of the Pigeon transport.
 *
 * All mutating work is submitted to [operationQueue] so an image write, video replacement, and
 * renderer configuration cannot race each other. A foreground Activity is retained weakly and is
 * consulted only immediately before launching system UI; direct work remains safe from a worker
 * or headless Flutter engine.
 */
class PigeonApiImpl(
  context: Context,
  private val operationQueue: OperationQueue = OperationQueue(),
  private val mainHandler: Handler = Handler(Looper.getMainLooper()),
  private val staticWallpaperEngine: StaticWallpaperEngine = StaticWallpaperEngine(context),
  private val videoRepository: VideoWallpaperRepository = VideoWallpaperRepository(context.filesDir),
  private val videoSourceOpener: BoundedSourceOpener = BoundedSourceOpener(
    context,
    MAX_VIDEO_SOURCE_BYTES,
  ),
  private val textureSourceOpener: BoundedSourceOpener = BoundedSourceOpener(
    context,
    ShaderProgramValidator.MAX_TEXTURE_SOURCE_BYTES,
  ),
) : WallpaperApi {
  private val appContext = context.applicationContext
  private val ioExecutor: ExecutorService = Executors.newCachedThreadPool()
  private val rotationStore = WallpaperRotationStore(appContext)
  private val rotationEngine = WallpaperRotationEngine(appContext, rotationStore)

  @Volatile
  private var activityReference: WeakReference<Activity>? = null

  /** Called by [AsyncWallpaperPlugin] only while Flutter has an attached Activity. */
  fun attachActivity(activity: Activity) {
    activityReference = WeakReference(activity)
  }

  /** Clears the weak reference on normal and configuration-change detach paths. */
  fun detachActivity(activity: Activity? = null) {
    val current = activityReference?.get()
    if (activity == null || current == null || current === activity) {
      activityReference = null
    }
  }

  /** Ends the plugin-owned queue and releases every Activity reference on engine detach. */
  fun shutdown() {
    activityReference = null
    operationQueue.shutdown()
  }

  override fun getPlatformVersion(callback: (Result<String>) -> Unit) {
    callback(Result.success("Android ${Build.VERSION.RELEASE}"))
  }

  override fun checkMaterialYouSupport(callback: (Result<MaterialYouSupportData>) -> Unit) {
    callback(
      Result.success(
        MaterialYouSupportData(
          isSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
          androidVersion = Build.VERSION.RELEASE,
          sdkInt = Build.VERSION.SDK_INT.toLong(),
        ),
      ),
    )
  }

  override fun getCapabilities(callback: (Result<WallpaperCapabilitiesData>) -> Unit) {
    callback(Result.success(AndroidCapabilities.snapshot(appContext)))
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
              WallpaperRotationScheduler.schedulePeriodic(appContext, intervalMinutes)
              rotationStore.setNextRunEpochMs(
                System.currentTimeMillis() + intervalMinutes.toLong() * 60_000L,
              )
            } else {
              WallpaperRotationScheduler.cancelPeriodic(appContext)
              rotationStore.setNextRunEpochMs(0L)
            }
            val needsMonitor = config.enableChargingTrigger == true || config.enableTimeOfDayTrigger == true
            if (needsMonitor) {
              Log.d(TAG, "Starting rotation monitor service")
              WallpaperRotationMonitorService.start(appContext)
            } else {
              WallpaperRotationMonitorService.stop(appContext)
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
        WallpaperRotationScheduler.cancelPeriodic(appContext)
        WallpaperRotationMonitorService.stop(appContext)
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

  override fun applyWallpaper(
    request: StaticWallpaperRequestData,
    callback: (Result<OperationResultData>) -> Unit,
  ) {
    enqueueOperation(request.target, callback) {
      when (request.strategy) {
        WallpaperApplyStrategyData.SYSTEM_CROPPER -> runOnMainBlocking {
          staticWallpaperEngine.openSystemCropper(request, currentActivity())
        }
        WallpaperApplyStrategyData.SYSTEM_PICKER -> runOnMainBlocking {
          staticWallpaperEngine.openSystemPicker(request, currentActivity())
        }
        WallpaperApplyStrategyData.DIRECT,
        WallpaperApplyStrategyData.AUTOMATIC,
        null,
        -> staticWallpaperEngine.applyDirect(request)
      }
    }
  }

  override fun prepareVideoWallpaper(
    request: VideoWallpaperRequestData,
    callback: (Result<OperationResultData>) -> Unit,
  ) {
    enqueueOperation(request.target, callback) {
      prepareVideoAsset(request)
    }
  }

  override fun openLiveWallpaperPreview(
    request: VideoWallpaperRequestData,
    callback: (Result<OperationResultData>) -> Unit,
  ) {
    enqueueOperation(request.target, callback) {
      val target = request.target ?: return@enqueueOperation OperationResultPolicy.failed(
        WallpaperTargetData.HOME,
        ERROR_INVALID_REQUEST,
        "A video wallpaper target is required.",
      )
      // Opening the preview is a UI operation. Do not replace the active asset in a headless
      // engine that has no way to show the user the system confirmation screen.
      if (!runOnMainBlocking { currentActivity() != null }) {
        return@enqueueOperation OperationResultPolicy.foregroundRequired(target)
      }
      val prepared = prepareVideoAsset(request)
      if (prepared.status != OperationStatusData.AWAITING_USER_CONFIRMATION) {
        prepared
      } else {
        runOnMainBlocking {
          openLiveWallpaperUi(target, VideoLiveWallpaper::class.java)
        }
      }
    }
  }

  override fun applyOpenGlWallpaper(
    request: OpenGlWallpaperRequestData,
    callback: (Result<OperationResultData>) -> Unit,
  ) {
    enqueueOperation(request.target, callback) {
      val target = request.target ?: return@enqueueOperation OperationResultPolicy.failed(
        WallpaperTargetData.HOME,
        ERROR_INVALID_REQUEST,
        "An OpenGL wallpaper target is required.",
      )
      // Persisting a renderer configuration is a mutation. Avoid changing it when the requested
      // preview cannot be shown by this headless engine.
      if (!runOnMainBlocking { currentActivity() != null }) {
        return@enqueueOperation OperationResultPolicy.foregroundRequired(target)
      }
      prepareAndOpenOpenGlWallpaper(request)
    }
  }

  override fun setHomeWallpaperFromUrl(
    url: String,
    goToHome: Boolean,
    callback: (Result<Boolean>) -> Unit,
  ) {
    setLegacyStaticWallpaper(
      WallpaperSourceData(kind = WallpaperSourceKindData.URL, url = url),
      WallpaperTargetData.HOME,
      callback,
    )
  }

  override fun setLockWallpaperFromUrl(
    url: String,
    goToHome: Boolean,
    callback: (Result<Boolean>) -> Unit,
  ) {
    setLegacyStaticWallpaper(
      WallpaperSourceData(kind = WallpaperSourceKindData.URL, url = url),
      WallpaperTargetData.LOCK,
      callback,
    )
  }

  override fun setBothWallpaperFromUrl(
    url: String,
    goToHome: Boolean,
    callback: (Result<Boolean>) -> Unit,
  ) {
    setLegacyStaticWallpaper(
      WallpaperSourceData(kind = WallpaperSourceKindData.URL, url = url),
      WallpaperTargetData.BOTH,
      callback,
    )
  }

  /**
   * Legacy chooser endpoint. Its URL was never passed to Android; retain that source-compatible
   * behavior, but require the current Activity rather than launching UI from the app context.
   */
  override fun setWallpaper(url: String, goToHome: Boolean, callback: (Result<Boolean>) -> Unit) {
    enqueueBoolean(callback) {
      runOnMainBlocking {
        openGenericWallpaperPicker()
      }
    }
  }

  override fun setHomeWallpaperFromFile(
    filePath: String,
    goToHome: Boolean,
    callback: (Result<Boolean>) -> Unit,
  ) {
    setLegacyStaticWallpaper(
      WallpaperSourceData(kind = WallpaperSourceKindData.FILE_PATH, filePath = filePath),
      WallpaperTargetData.HOME,
      callback,
    )
  }

  override fun setLockWallpaperFromFile(
    filePath: String,
    goToHome: Boolean,
    callback: (Result<Boolean>) -> Unit,
  ) {
    setLegacyStaticWallpaper(
      WallpaperSourceData(kind = WallpaperSourceKindData.FILE_PATH, filePath = filePath),
      WallpaperTargetData.LOCK,
      callback,
    )
  }

  override fun setBothWallpaperFromFile(
    filePath: String,
    goToHome: Boolean,
    callback: (Result<Boolean>) -> Unit,
  ) {
    setLegacyStaticWallpaper(
      WallpaperSourceData(kind = WallpaperSourceKindData.FILE_PATH, filePath = filePath),
      WallpaperTargetData.BOTH,
      callback,
    )
  }

  /**
   * The old implementation copied a file into public MediaStore solely to launch cropper UI.
   * Use the direct path instead: it preserves file-source support without leaking duplicate media
   * items or requiring a foreground Activity.
   */
  override fun setWallpaperFromFile(
    filePath: String,
    goToHome: Boolean,
    callback: (Result<Boolean>) -> Unit,
  ) {
    setLegacyStaticWallpaper(
      WallpaperSourceData(kind = WallpaperSourceKindData.FILE_PATH, filePath = filePath),
      WallpaperTargetData.HOME,
      callback,
    )
  }

  override fun setMaterialYouWallpaper(
    url: String,
    goToHome: Boolean,
    enableEffects: Boolean,
    callback: (Result<Boolean>) -> Unit,
  ) {
    // Material You effects remain a launcher/system concern; preserve historical behavior by
    // applying the supplied image to both targets when Android permits it.
    setBothWallpaperFromUrl(url, goToHome, callback)
  }

  override fun setLiveWallpaper(
    filePath: String,
    goToHome: Boolean,
    callback: (Result<Boolean>) -> Unit,
  ) {
    enqueueBoolean(callback) {
      if (!runOnMainBlocking { currentActivity() != null }) {
        return@enqueueBoolean false
      }
      val request = VideoWallpaperRequestData(
        source = WallpaperSourceData(kind = WallpaperSourceKindData.FILE_PATH, filePath = filePath),
        target = WallpaperTargetData.HOME,
        scaleMode = WallpaperScaleModeData.CENTER_CROP,
      )
      val prepared = prepareVideoAsset(request)
      if (prepared.status != OperationStatusData.AWAITING_USER_CONFIRMATION) {
        false
      } else {
        runOnMainBlocking {
          openLiveWallpaperUi(WallpaperTargetData.HOME, VideoLiveWallpaper::class.java).status ==
            OperationStatusData.PREVIEW_OPENED
        }
      }
    }
  }

  override fun openWallpaperChooser(callback: (Result<Boolean>) -> Unit) {
    enqueueBoolean(callback) {
      runOnMainBlocking {
        val activity = currentActivity() ?: return@runOnMainBlocking false
        val intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
        if (!AndroidCapabilities.resolves(activity.packageManager, intent)) {
          false
        } else {
          runCatching { activity.startActivity(intent) }.isSuccess
        }
      }
    }
  }

  override fun downloadWallpaper(url: String, callback: (Result<Boolean>) -> Unit) {
    enqueueBoolean(callback) {
      downloadToMediaStore(url)
    }
  }

  private fun setLegacyStaticWallpaper(
    source: WallpaperSourceData,
    target: WallpaperTargetData,
    callback: (Result<Boolean>) -> Unit,
  ) {
    enqueueBoolean(callback) {
      staticWallpaperEngine.applyDirect(
        StaticWallpaperRequestData(
          source = source,
          target = target,
          scaleMode = WallpaperScaleModeData.CENTER_CROP,
          strategy = WallpaperApplyStrategyData.DIRECT,
        ),
      ).status == OperationStatusData.APPLIED
    }
  }

  private fun prepareVideoAsset(request: VideoWallpaperRequestData): OperationResultData {
    val target = request.target
      ?: return OperationResultPolicy.failed(
        WallpaperTargetData.HOME,
        ERROR_INVALID_REQUEST,
        "A video wallpaper target is required.",
      )
    val source = request.source
      ?: return OperationResultPolicy.failed(
        target,
        ERROR_INVALID_REQUEST,
        "A video wallpaper source is required.",
      )
    val scaleMode = request.scaleMode
      ?: return OperationResultPolicy.failed(
        target,
        ERROR_INVALID_REQUEST,
        "A video wallpaper scale mode is required.",
      )
    if (scaleMode != WallpaperScaleModeData.CENTER_CROP &&
      scaleMode != WallpaperScaleModeData.FIT_CENTER
    ) {
      return OperationResultPolicy.unsupported(
        target,
        ERROR_VIDEO_SCALE_UNSUPPORTED,
        "Video live wallpapers support only centerCrop and fitCenter scaling.",
      )
    }
    if (!source.hasValueForKind()) {
      return OperationResultPolicy.failed(
        target,
        ERROR_INVALID_REQUEST,
        "The video wallpaper source is incomplete.",
      )
    }
    if (!AndroidCapabilities.hasVideoLiveWallpaper(appContext)) {
      return OperationResultPolicy.unsupported(
        target,
        ERROR_LIVE_WALLPAPER_UNSUPPORTED,
        "This device cannot open this app's live wallpaper flow.",
      )
    }

    return try {
      videoSourceOpener.open(source).use { input ->
        videoRepository.prepare(input)
      }
      VideoLiveWallpaper.configureScaleMode(scaleMode)
      // Preparing changes only an app-private candidate. The user has not selected it yet.
      OperationResultPolicy.awaitingUserConfirmation(target)
    } catch (error: BoundedSourceException) {
      OperationResultPolicy.failed(target, error.code, error.message ?: "Unable to read the video source.")
    } catch (error: VideoMetadataValidationException) {
      OperationResultPolicy.failed(
        target,
        videoValidationCode(error),
        error.message ?: "The video source is not playable.",
      )
    } catch (error: SecurityException) {
      OperationResultPolicy.failed(
        target,
        ERROR_PERMISSION_DENIED,
        "Permission to read or prepare the video source was denied.",
        error.message,
      )
    } catch (error: IOException) {
      OperationResultPolicy.failed(
        target,
        ERROR_VIDEO_PREPARATION_FAILED,
        "Unable to prepare the video wallpaper.",
        error.message,
      )
    } catch (error: Exception) {
      OperationResultPolicy.failed(
        target,
        ERROR_VIDEO_PREPARATION_FAILED,
        "Unable to prepare the video wallpaper.",
        error.message,
      )
    }
  }

  private fun openLiveWallpaperUi(
    target: WallpaperTargetData,
    serviceClass: Class<*>,
  ): OperationResultData {
    val activity = currentActivity() ?: return OperationResultPolicy.foregroundRequired(target)
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
      putExtra(
        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
        ComponentName(appContext, serviceClass),
      )
    }
    return try {
      if (!AndroidCapabilities.resolves(activity.packageManager, intent)) {
        OperationResultPolicy.failed(
          target,
          ERROR_SYSTEM_UI_UNAVAILABLE,
          "This device has no system live wallpaper preview UI.",
        )
      } else {
        activity.startActivity(intent)
        // Android owns target selection in this UI. Do not claim home or lock was applied.
        OperationResultPolicy.previewOpened(target)
      }
    } catch (_: ActivityNotFoundException) {
      OperationResultPolicy.failed(
        target,
        ERROR_SYSTEM_UI_UNAVAILABLE,
        "This device has no system live wallpaper preview UI.",
      )
    } catch (error: SecurityException) {
      OperationResultPolicy.failed(
        target,
        ERROR_PERMISSION_DENIED,
        "Permission to open live wallpaper preview was denied.",
        error.message,
      )
    } catch (error: Exception) {
      OperationResultPolicy.failed(
        target,
        ERROR_SYSTEM_UI_FAILED,
        "Unable to open live wallpaper preview.",
        error.message,
      )
    }
  }

  private fun prepareAndOpenOpenGlWallpaper(request: OpenGlWallpaperRequestData): OperationResultData {
    val target = request.target
      ?: return OperationResultPolicy.failed(
        WallpaperTargetData.HOME,
        ERROR_INVALID_REQUEST,
        "An OpenGL wallpaper target is required.",
      )
    val fragmentShader = request.fragmentShader
      ?: return OperationResultPolicy.failed(target, ERROR_INVALID_REQUEST, "A fragment shader is required.")
    val frameRate = request.frameRate
      ?: return OperationResultPolicy.failed(target, ERROR_INVALID_REQUEST, "A frame rate is required.")
    val textures = request.textures
      ?: return OperationResultPolicy.failed(target, ERROR_INVALID_REQUEST, "A texture list is required.")
    if (frameRate !in ShaderProgramValidator.MIN_FRAME_RATE..ShaderProgramValidator.MAX_FRAME_RATE) {
      return OperationResultPolicy.failed(
        target,
        ShaderProgramValidator.ErrorCode.FRAME_RATE_OUT_OF_RANGE.wireCode,
        "Frame rate must be between ${ShaderProgramValidator.MIN_FRAME_RATE} and " +
          "${ShaderProgramValidator.MAX_FRAME_RATE} FPS.",
      )
    }
    if (!AndroidCapabilities.hasOpenGlLiveWallpaper(appContext)) {
      return OperationResultPolicy.unsupported(
        target,
        ERROR_OPENGL_WALLPAPER_UNSUPPORTED,
        "This device cannot open this app's OpenGL live wallpaper flow.",
      )
    }

    val textureSources = try {
      textures.mapIndexed { index, source ->
        source ?: throw BoundedSourceException(
          BoundedSourceOpener.ERROR_INVALID_SOURCE,
          "Texture $index is missing.",
        )
        textureSourceFor(source)
      }
    } catch (error: BoundedSourceException) {
      return OperationResultPolicy.failed(
        target,
        error.code,
        error.message ?: "Unable to read an OpenGL texture source.",
      )
    } catch (error: Exception) {
      return OperationResultPolicy.failed(
        target,
        ERROR_OPENGL_CONFIGURATION_FAILED,
        "Unable to prepare OpenGL texture sources.",
        error.message,
      )
    }

    val configuration = OpenGlWallpaperConfiguration(
      fragmentShader = fragmentShader,
      textures = textureSources,
      frameRate = frameRate.toInt(),
    )
    when (val saved = OpenGlLiveWallpaper.saveConfiguration(appContext, configuration)) {
      OpenGlConfigurationResult.Saved -> Unit
      is OpenGlConfigurationResult.Rejected -> {
        return OperationResultPolicy.failed(
          target,
          saved.error.wireCode,
          saved.error.message,
        )
      }
    }

    return runOnMainBlocking {
      openLiveWallpaperUi(target, OpenGlLiveWallpaper::class.java)
    }
  }

  private fun textureSourceFor(source: WallpaperSourceData): GlTextureSource {
    return when (source.kind ?: throw BoundedSourceException(
      BoundedSourceOpener.ERROR_INVALID_SOURCE,
      "A texture source kind is required.",
    )) {
      WallpaperSourceKindData.URL -> GlTextureSource.Bytes(textureSourceOpener.readBytes(source))
      WallpaperSourceKindData.FILE_PATH -> {
        val path = source.filePath?.trim()?.takeIf { it.isNotEmpty() }
          ?: throw BoundedSourceException(BoundedSourceOpener.ERROR_INVALID_SOURCE, "A texture file path is required.")
        GlTextureSource.FilePath(path)
      }
      WallpaperSourceKindData.CONTENT_URI -> {
        val uri = source.contentUri?.trim()?.takeIf { it.isNotEmpty() }
          ?: throw BoundedSourceException(BoundedSourceOpener.ERROR_INVALID_SOURCE, "A texture content URI is required.")
        val parsed = Uri.parse(uri)
        if (!parsed.scheme.equals("content", ignoreCase = true) || parsed.authority.isNullOrBlank()) {
          throw BoundedSourceException(
            BoundedSourceOpener.ERROR_INVALID_SOURCE,
            "Texture content sources must use a content URI.",
          )
        }
        GlTextureSource.ContentUri(uri)
      }
      WallpaperSourceKindData.BYTES -> {
        val bytes = source.bytes
          ?: throw BoundedSourceException(BoundedSourceOpener.ERROR_INVALID_SOURCE, "Texture bytes are required.")
        if (bytes.isEmpty()) {
          throw BoundedSourceException(BoundedSourceOpener.ERROR_INVALID_SOURCE, "Texture bytes must not be empty.")
        }
        if (bytes.size.toLong() > ShaderProgramValidator.MAX_TEXTURE_SOURCE_BYTES) {
          throw BoundedSourceException(
            BoundedSourceOpener.ERROR_SOURCE_TOO_LARGE,
            "Texture bytes exceed the configured byte limit.",
          )
        }
        GlTextureSource.Bytes(bytes)
      }
    }
  }

  private fun openGenericWallpaperPicker(): Boolean {
    val activity = currentActivity() ?: return false
    val intent = Intent(Intent.ACTION_SET_WALLPAPER)
    return if (!AndroidCapabilities.resolves(activity.packageManager, intent)) {
      false
    } else {
      runCatching { activity.startActivity(intent) }.isSuccess
    }
  }

  private fun downloadToMediaStore(url: String): Boolean {
    var uri: Uri? = null
    var completed = false
    return try {
      val source = WallpaperSourceData(kind = WallpaperSourceKindData.URL, url = url)
      val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "wallpaper_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AsyncWallpaper")
          put(MediaStore.Images.Media.IS_PENDING, 1)
        }
      }
      val resolver = appContext.contentResolver
      uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
      val written = textureSourceOpener.open(source).use { input ->
        resolver.openOutputStream(uri!!)?.use { output ->
          input.copyTo(output)
          true
        } ?: false
      }
      if (!written) {
        return false
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        resolver.update(
          uri!!,
          ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
          null,
          null,
        )
      }
      completed = true
      true
    } catch (error: Exception) {
      Log.e(TAG, "downloadWallpaper failed", error)
      false
    } finally {
      // Do not leave an abandoned, user-visible pending record if writing failed midway.
      if (!completed && uri != null) {
        runCatching { appContext.contentResolver.delete(uri!!, null, null) }
      }
    }
  }

  private fun enqueueOperation(
    requestedTarget: WallpaperTargetData?,
    callback: (Result<OperationResultData>) -> Unit,
    operation: () -> OperationResultData,
  ) {
    operationQueue.submit(operation) { result ->
      val data = result.getOrElse { error -> operationFailure(requestedTarget, error) }
      postCallback(callback, Result.success(data))
    }
  }

  private fun enqueueBoolean(
    callback: (Result<Boolean>) -> Unit,
    operation: () -> Boolean,
  ) {
    operationQueue.submit(operation) { result ->
      postCallback(callback, Result.success(result.getOrDefault(false)))
    }
  }


  private fun <T> postCallback(callback: (Result<T>) -> Unit, result: Result<T>) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      callback(result)
      return
    }
    if (!mainHandler.post { callback(result) }) {
      // Handler rejection means there is no main queue to marshal onto; do not lose the exactly
      // once Pigeon reply. BasicMessageChannel accepts replies from a background thread.
      callback(result)
    }
  }

  private fun operationFailure(
    requestedTarget: WallpaperTargetData?,
    error: Throwable,
  ): OperationResultData {
    val target = requestedTarget ?: WallpaperTargetData.HOME
    return when (error) {
      is OperationQueueShutdownException -> OperationResultPolicy.failed(
        target,
        ERROR_QUEUE_SHUTDOWN,
        "The wallpaper operation queue is no longer available.",
      )
      is MainThreadUnavailableException -> OperationResultPolicy.foregroundRequired(target)
      is BoundedSourceException -> OperationResultPolicy.failed(target, error.code, error.message ?: "Unable to read the source.")
      is WallpaperSourceException -> OperationResultPolicy.failed(target, error.code, error.message ?: "Unable to read the source.")
      is SecurityException -> OperationResultPolicy.failed(target, ERROR_PERMISSION_DENIED, "Permission was denied.", error.message)
      is OutOfMemoryError -> OperationResultPolicy.failed(target, ERROR_OUT_OF_MEMORY, "The operation exceeded available memory.")
      else -> OperationResultPolicy.failed(target, ERROR_OPERATION_FAILED, "The wallpaper operation failed.", error.message)
    }
  }

  private fun <T> runOnMainBlocking(operation: () -> T): T {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      return operation()
    }
    val latch = CountDownLatch(1)
    val gate = MainThreadOperationGate()
    var result: Result<T>? = null
    val runnable = Runnable {
      if (!gate.begin()) {
        return@Runnable
      }
      try {
        result = runCatching(operation)
      } finally {
        gate.complete()
        latch.countDown()
      }
    }
    if (!mainHandler.post(runnable)) {
      gate.cancelBeforeStart()
      throw MainThreadUnavailableException()
    }
    var interruptedWhileWaiting = false
    val completedWithinTimeout = try {
      latch.await(MAIN_THREAD_WAIT_MILLIS, TimeUnit.MILLISECONDS)
    } catch (_: InterruptedException) {
      interruptedWhileWaiting = true
      false
    }
    if (!completedWithinTimeout) {
      mainHandler.removeCallbacks(runnable)
      if (gate.cancelBeforeStart()) {
        if (interruptedWhileWaiting) {
          Thread.currentThread().interrupt()
        }
        throw MainThreadUnavailableException()
      }
      // The main-thread action began before timeout. Wait for that action to finish rather than
      // replying first and allowing a delayed system UI launch or mutation afterward.
      awaitMainOperationCompletion(latch)
    }
    if (interruptedWhileWaiting) {
      Thread.currentThread().interrupt()
    }
    return result?.getOrThrow() ?: throw MainThreadUnavailableException()
  }

  private fun awaitMainOperationCompletion(latch: CountDownLatch) {
    var interrupted = false
    while (true) {
      try {
        latch.await()
        break
      } catch (_: InterruptedException) {
        interrupted = true
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt()
    }
  }

  private fun currentActivity(): Activity? {
    val reference = activityReference
    val activity = reference?.get()
    if (!isUsableActivity(activity)) {
      // Do not erase a newer Activity attached during a configuration change while a queued
      // operation was checking an older weak reference.
      if (activityReference === reference) {
        activityReference = null
      }
      return null
    }
    return activity
  }

  private fun isUsableActivity(activity: Activity?): Boolean {
    return activity != null && !activity.isFinishing &&
      (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed)
  }

  private fun WallpaperSourceData.hasValueForKind(): Boolean {
    return when (kind) {
      WallpaperSourceKindData.URL -> !url.isNullOrBlank()
      WallpaperSourceKindData.FILE_PATH -> !filePath.isNullOrBlank()
      WallpaperSourceKindData.CONTENT_URI -> !contentUri.isNullOrBlank()
      WallpaperSourceKindData.BYTES -> bytes?.isNotEmpty() == true
      null -> false
    }
  }

  private fun videoValidationCode(error: VideoMetadataValidationException): String {
    return when (error.failure) {
      VideoValidationFailure.MISSING_FILE -> BoundedSourceOpener.ERROR_SOURCE_UNAVAILABLE
      VideoValidationFailure.INVALID_MIME_TYPE -> "invalid-content-type"
      VideoValidationFailure.NO_VIDEO_TRACK,
      VideoValidationFailure.INVALID_DIMENSIONS,
      VideoValidationFailure.INVALID_ROTATION,
      VideoValidationFailure.INVALID_DURATION,
      -> ERROR_INVALID_VIDEO
      VideoValidationFailure.RETRIEVER_FAILURE -> ERROR_VIDEO_PREPARATION_FAILED
    }
  }

  private class MainThreadUnavailableException : IllegalStateException()

  companion object {
    private const val TAG = "AsyncWallpaper"
    private const val MAX_VIDEO_SOURCE_BYTES = 256L * 1024L * 1024L
    private const val MAIN_THREAD_WAIT_MILLIS = 10_000L

    private const val ERROR_INVALID_REQUEST = "invalid-request"
    private const val ERROR_PERMISSION_DENIED = "permission-denied"
    private const val ERROR_OUT_OF_MEMORY = "out-of-memory"
    private const val ERROR_OPERATION_FAILED = "operation-failed"
    private const val ERROR_QUEUE_SHUTDOWN = "queue-shutdown"
    private const val ERROR_LIVE_WALLPAPER_UNSUPPORTED = "live-wallpaper-unsupported"
    private const val ERROR_OPENGL_WALLPAPER_UNSUPPORTED = "opengl-live-wallpaper-unsupported"
    private const val ERROR_VIDEO_PREPARATION_FAILED = "video-preparation-failed"
    private const val ERROR_INVALID_VIDEO = "invalid-video"
    private const val ERROR_VIDEO_SCALE_UNSUPPORTED = "video-scale-unsupported"
    private const val ERROR_OPENGL_CONFIGURATION_FAILED = "opengl-configuration-failed"
    private const val ERROR_SYSTEM_UI_UNAVAILABLE = "system-ui-unavailable"
    private const val ERROR_SYSTEM_UI_FAILED = "system-ui-failed"
  }
}
