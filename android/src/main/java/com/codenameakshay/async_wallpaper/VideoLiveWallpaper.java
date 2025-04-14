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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.channels.FileChannel;


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
            Log.e("VideoLiveWallpaper", "Error clearing wallpaper", e);
        }
    }

    public static void openWallpaperChooser(Context context) {
        Intent intent = new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        try {
            WallpaperManager.getInstance(context).clear();
        } catch (IOException e) {
            Log.e("VideoLiveWallpaper", "Error clearing wallpaper", e);
        }
    }

    @Override
    public Engine onCreateEngine() {
        return new VideoEngine();
    }

    class VideoEngine extends Engine {
        private MediaPlayer mediaPlayer;
        private BroadcastReceiver broadcastReceiver;
        private boolean isReceiverRegistered = false;
        private WeakReference<Context> contextRef;

        @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            contextRef = new WeakReference<>(getApplicationContext());
            registerVolumeReceiver();
        }

        private void registerVolumeReceiver() {
            if (!isReceiverRegistered) {
                IntentFilter intentFilter = new IntentFilter(VIDEO_PARAMS_CONTROL_ACTION);
                broadcastReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (mediaPlayer != null) {
                            boolean action = intent.getBooleanExtra(KEY_ACTION, false);
                            mediaPlayer.setVolume(action ? 0 : 1.0f, action ? 0 : 1.0f);
                        }
                    }
                };
                
                // Use the appropriate registration method based on Android version
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    registerReceiver(broadcastReceiver, intentFilter);
                }
                
                isReceiverRegistered = true;
            }
        }

        private void releaseMediaPlayer() {
            if (mediaPlayer != null) {
                try {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                    mediaPlayer.reset();
                    mediaPlayer.release();
                } catch (Exception e) {
                    Log.e("VideoEngine", "Error releasing MediaPlayer", e);
                } finally {
                    mediaPlayer = null;
                }
            }
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
                    
                    // Use the appropriate file path based on Android version
                    File videoFile = new File(getFilesDir(), "file.mp4");
                    if (videoFile.exists()) {
                        mediaPlayer.setDataSource(videoFile.getAbsolutePath());
                    } else {
                        Log.e("VideoEngine", "Video file not found: " + videoFile.getAbsolutePath());
                        return;
                    }
                    
                    mediaPlayer.setLooping(true);
                    mediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                    mediaPlayer.setVolume(0, 0);
                    Log.d("VideoEngine", "MediaPlayer started successfully");
                }
            } catch (IOException e) {
                Log.e("VideoEngine", "Error initializing MediaPlayer", e);
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
            releaseMediaPlayer();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            releaseMediaPlayer();
            if (isReceiverRegistered && broadcastReceiver != null) {
                try {
                    unregisterReceiver(broadcastReceiver);
                    isReceiverRegistered = false;
                } catch (Exception e) {
                    Log.e("VideoEngine", "Error unregistering receiver", e);
                }
            }
        }
    }

    public void copyFile(File fromFile, File toFile) {
        FileInputStream fileInputStream = null;
        FileOutputStream fileOutputStream = null;
        FileChannel fileChannelInput = null;
        FileChannel fileChannelOutput = null;
        
        try {
            fileInputStream = new FileInputStream(fromFile);
            fileOutputStream = new FileOutputStream(toFile);
            fileChannelInput = fileInputStream.getChannel();
            fileChannelOutput = fileOutputStream.getChannel();
            
            long size = fileChannelInput.size();
            long transferred = 0;
            while (transferred < size) {
                transferred += fileChannelInput.transferTo(transferred, size - transferred, fileChannelOutput);
            }
        } catch (IOException e) {
            Log.e("AsyncWallpaperPlugin", "Error copying file", e);
            throw new RuntimeException("Failed to copy file", e);
        } finally {
            closeQuietly(fileChannelInput);
            closeQuietly(fileChannelOutput);
            closeQuietly(fileInputStream);
            closeQuietly(fileOutputStream);
        }
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                Log.e("AsyncWallpaperPlugin", "Error closing resource", e);
            }
        }
    }
}