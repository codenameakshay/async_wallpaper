package com.codenameakshay.async_wallpaper

/**
 * What one screen's OpenGL engine draws. On Android 16 it travels inside that screen's
 * `WallpaperDescription`, so the Home and Lock engines each read their own copy. Texture bytes
 * stay in an app-private generation directory; the description names that directory.
 */
internal data class LiveScreenContent(
  val fragmentShader: String,
  val frameRate: Int,
  val textureDirectory: String?,
  val textureCount: Int,
  val contentTag: String?,
) {
  fun toEntries(): Map<String, Any> = buildMap {
    put(KEY_VERSION, VERSION)
    put(KEY_SHADER, fragmentShader)
    put(KEY_FRAME_RATE, frameRate)
    put(KEY_TEXTURE_COUNT, textureCount)
    textureDirectory?.let { put(KEY_TEXTURE_DIRECTORY, it) }
    contentTag?.let { put(KEY_CONTENT_TAG, it) }
  }

  companion object {
    private const val VERSION = 1
    private const val KEY_VERSION = "async_wallpaper.version"
    private const val KEY_SHADER = "async_wallpaper.fragment_shader"
    private const val KEY_FRAME_RATE = "async_wallpaper.frame_rate"
    private const val KEY_TEXTURE_COUNT = "async_wallpaper.texture_count"
    private const val KEY_TEXTURE_DIRECTORY = "async_wallpaper.texture_directory"
    private const val KEY_CONTENT_TAG = "async_wallpaper.content_tag"
    const val MAX_CONTENT_TAG_LENGTH = 256

    val keys: List<String> = listOf(
      KEY_VERSION,
      KEY_SHADER,
      KEY_FRAME_RATE,
      KEY_TEXTURE_COUNT,
      KEY_TEXTURE_DIRECTORY,
      KEY_CONTENT_TAG,
    )

    /** Returns null for a description this plugin did not write, or one it cannot trust. */
    fun fromEntries(entries: Map<String, Any?>): LiveScreenContent? {
      if (entries[KEY_VERSION] != VERSION) {
        return null
      }
      val shader = (entries[KEY_SHADER] as? String)?.takeIf { it.isNotBlank() } ?: return null
      val frameRate = entries[KEY_FRAME_RATE] as? Int ?: return null
      if (frameRate !in ShaderProgramValidator.MIN_FRAME_RATE..ShaderProgramValidator.MAX_FRAME_RATE) {
        return null
      }
      val textureCount = entries[KEY_TEXTURE_COUNT] as? Int ?: return null
      if (textureCount !in 0..ShaderProgramValidator.MAX_TEXTURE_COUNT) {
        return null
      }
      val directory = entries[KEY_TEXTURE_DIRECTORY] as? String
      if ((textureCount == 0) != (directory == null)) {
        return null
      }
      if (directory != null && !isGenerationDirectoryName(directory)) {
        return null
      }
      val tag = entries[KEY_CONTENT_TAG] as? String
      if (tag != null && tag.length > MAX_CONTENT_TAG_LENGTH) {
        return null
      }
      return LiveScreenContent(shader, frameRate, directory, textureCount, tag)
    }

    fun isGenerationDirectoryName(name: String): Boolean {
      return name.startsWith(OPEN_GL_CONFIGURATION_DIRECTORY_PREFIX) &&
        name.length > OPEN_GL_CONFIGURATION_DIRECTORY_PREFIX.length &&
        name.none { it == '/' || it == '\\' } &&
        name != "." && name != ".."
    }
  }
}

/** Android's two wallpaper screens, with the `WallpaperManager` flag bit for each. */
internal enum class LiveScreen(val flag: Int) {
  // WallpaperManager.FLAG_SYSTEM and FLAG_LOCK, inlined so JVM tests need no android.jar.
  HOME(1),
  LOCK(2);

  companion object {
    fun fromFlags(which: Int): Set<LiveScreen> = entries.filterTo(LinkedHashSet()) { which and it.flag != 0 }
  }
}

/** Whether one live wallpaper service can show different content on Home and Lock. */
internal object PerScreenLiveSupport {
  const val MIN_SDK = 36

  fun isSupported(sdkInt: Int, contentHandlingEnabled: Boolean): Boolean =
    sdkInt >= MIN_SDK && contentHandlingEnabled

  /** The screens a request's content can reach. Without per-screen support both share it. */
  fun effectiveTarget(requested: WallpaperTargetData, perScreen: Boolean): WallpaperTargetData =
    if (perScreen) requested else WallpaperTargetData.BOTH
}

/** What a screen's engine drew, keyed to the system's id for that screen's wallpaper. */
internal data class LiveScreenRecord(val wallpaperId: Int, val content: LiveScreenContent)

/**
 * Picks what a running (not preview) engine draws once it knows its screen.
 *
 * Android gives a screen a new wallpaper id each time the user sets it. A record with the
 * current id means this engine restarted, so it keeps that screen's content. Any other id means
 * the user just set this screen, so it takes the content the app staged for the preview. A
 * description this plugin wrote wins over both.
 */
internal fun chooseScreenContent(
  described: LiveScreenContent?,
  record: LiveScreenRecord?,
  currentWallpaperId: Int,
  staged: LiveScreenContent,
): LiveScreenContent = described
  ?: record?.takeIf { it.wallpaperId == currentWallpaperId }?.content
  ?: staged

/** What a screen shows, from this app's point of view. */
internal data class LiveScreenState(val showsThisWallpaper: Boolean, val contentTag: String?)

/**
 * Combines the system's answer (whose wallpaper each screen shows, and its id) with the records
 * this plugin's engines wrote. A record for an older id is stale: the screen was set again since.
 */
internal fun resolveLiveScreens(
  homeIsThisWallpaper: Boolean,
  lockMirrorsHome: Boolean,
  lockIsThisWallpaper: Boolean,
  homeWallpaperId: Int,
  lockWallpaperId: Int,
  homeRecord: LiveScreenRecord?,
  lockRecord: LiveScreenRecord?,
): Map<LiveScreen, LiveScreenState> {
  fun state(isThisWallpaper: Boolean, wallpaperId: Int, record: LiveScreenRecord?) = LiveScreenState(
    isThisWallpaper,
    record?.takeIf { isThisWallpaper && it.wallpaperId == wallpaperId }?.content?.contentTag,
  )
  val home = state(homeIsThisWallpaper, homeWallpaperId, homeRecord)
  val lock = if (lockMirrorsHome) home else state(lockIsThisWallpaper, lockWallpaperId, lockRecord)
  return mapOf(LiveScreen.HOME to home, LiveScreen.LOCK to lock)
}
