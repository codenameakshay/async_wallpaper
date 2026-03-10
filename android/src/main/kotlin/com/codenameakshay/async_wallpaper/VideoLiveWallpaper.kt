package com.codenameakshay.async_wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import java.io.File

class VideoLiveWallpaper : WallpaperService() {
  override fun onCreateEngine(): Engine = VideoEngine()

  inner class VideoEngine : Engine() {
    private var mediaPlayer: MediaPlayer? = null

    override fun onSurfaceCreated(holder: SurfaceHolder) {
      super.onSurfaceCreated(holder)
      initializePlayer(holder)
    }

    override fun onVisibilityChanged(visible: Boolean) {
      val player = mediaPlayer ?: return
      if (visible) {
        player.start()
      } else {
        player.pause()
      }
    }

    override fun onSurfaceDestroyed(holder: SurfaceHolder) {
      super.onSurfaceDestroyed(holder)
      releasePlayer()
    }

    override fun onDestroy() {
      super.onDestroy()
      releasePlayer()
    }

    private fun initializePlayer(holder: SurfaceHolder) {
      try {
        val player = MediaPlayer()
        val file = File(filesDir, LIVE_WALLPAPER_FILE_NAME)
        if (!file.exists()) {
          player.release()
          return
        }
        player.setSurface(holder.surface)
        player.setDataSource(file.absolutePath)
        player.isLooping = true
        player.setVolume(0f, 0f)
        player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
        player.prepare()
        player.start()
        mediaPlayer = player
      } catch (error: Exception) {
        Log.e(TAG, "Failed to initialize live wallpaper player", error)
      }
    }

    private fun releasePlayer() {
      try {
        mediaPlayer?.stop()
      } catch (_: Exception) {
      }
      try {
        mediaPlayer?.release()
      } catch (_: Exception) {
      }
      mediaPlayer = null
    }
  }

  companion object {
    private const val TAG = "VideoLiveWallpaper"
    private const val LIVE_WALLPAPER_FILE_NAME = "file.mp4"

    fun setToWallpaper(context: Context) {
      val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(
          WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
          ComponentName(context, VideoLiveWallpaper::class.java),
        )
      }
      context.startActivity(intent)
    }

    fun openWallpaperChooser(context: Context) {
      val intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    }
  }
}
