package com.codenameakshay.async_wallpaper;

import android.annotation.SuppressLint;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.service.wallpaper.WallpaperService;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.IOException;


public class VideoLiveWallpaper extends WallpaperService {

    public static final String VIDEO_PARAMS_CONTROL_ACTION = "com.codenameakshay.async_wallpaper";
    public static final String KEY_ACTION = "music";
    public static final boolean ACTION_MUSIC_UNMUTE = false;
    public static final boolean ACTION_MUSIC_MUTE = true;

    public static void setToWallPaper(Context context) {
        final Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new ComponentName(context, VideoLiveWallpaper.class));
        context.startActivity(intent);
        try {
            WallpaperManager.getInstance(context).clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void openWallpaperChooser(Context context) {
        Intent intent = new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        try {
            WallpaperManager.getInstance(context).clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Engine onCreateEngine() {
        return new VideoEngine();
    }

    class VideoEngine extends Engine {
        private MediaPlayer mediaPlayer;
        private BroadcastReceiver broadcastReceiver;

        @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            IntentFilter intentFilter = new IntentFilter(VideoLiveWallpaper.VIDEO_PARAMS_CONTROL_ACTION);
            registerReceiver(broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    boolean action = intent.getBooleanExtra(KEY_ACTION, false);
                    if (action) {
                        mediaPlayer.setVolume(0, 0);
                    } else {
                        mediaPlayer.setVolume(1.0f, 1.0f);
                    }
                }
            }, intentFilter, Context.RECEIVER_NOT_EXPORTED);
        }

        @SuppressLint("SdCardPath")
        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            initializeMediaPlayer(holder);
        }

        private void initializeMediaPlayer(SurfaceHolder holder) {
            try {
                if (mediaPlayer == null) {
                    mediaPlayer = new MediaPlayer();
                    mediaPlayer.setSurface(holder.getSurface());
                    mediaPlayer.setDataSource(getFilesDir() + "/file.mp4");
                    mediaPlayer.setLooping(true);
                    mediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                    mediaPlayer.setVolume(0, 0);
                    Log.d("VideoEngine", "MediaPlayer started successfully");
                }
            } catch (IOException e) {
                Log.e("VideoEngine", "Error initializing MediaPlayer", e);
                e.printStackTrace();
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (mediaPlayer != null) {
                if (visible) {
                    mediaPlayer.start();
                } else {
                    mediaPlayer.pause();
                }
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
            unregisterReceiver(broadcastReceiver);
        }
    }
}