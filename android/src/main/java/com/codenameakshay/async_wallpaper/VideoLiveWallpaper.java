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
import android.os.BatteryManager;
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
        private static final int LOW_BATTERY_THRESHOLD = 15; // Battery percentage threshold
        private static final float LOW_BATTERY_FPS = 15.0f; // FPS for low battery mode
        private static final float NORMAL_FPS = 30.0f; // Normal FPS
        private boolean isLowBatteryMode = false;
        private BatteryManager batteryManager;

        @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            contextRef = new WeakReference<>(getApplicationContext());
            registerVolumeReceiver();
            initializeBatteryManager();
        }

        private void initializeBatteryManager() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
                updateBatteryMode();
            }
        }

        private void updateBatteryMode() {
            if (batteryManager != null) {
                int batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                boolean newLowBatteryMode = batteryLevel <= LOW_BATTERY_THRESHOLD;
                
                if (newLowBatteryMode != isLowBatteryMode) {
                    isLowBatteryMode = newLowBatteryMode;
                    updatePlaybackSettings();
                }
            }
        }

        private void updatePlaybackSettings() {
            if (mediaPlayer != null) {
                try {
                    if (isLowBatteryMode) {
                        // Reduce frame rate for low battery mode
                        mediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
                        // Set lower playback speed
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(0.5f));
                        }
                    } else {
                        // Normal playback settings
                        mediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(1.0f));
                        }
                    }
                } catch (Exception e) {
                    Log.e("VideoEngine", "Error updating playback settings", e);
                }
            }
        }

        private void registerVolumeReceiver() {
            if (!isReceiverRegistered) {
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(VIDEO_PARAMS_CONTROL_ACTION);
                intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
                
                broadcastReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (intent.getAction().equals(VIDEO_PARAMS_CONTROL_ACTION)) {
                            if (mediaPlayer != null) {
                                boolean action = intent.getBooleanExtra(KEY_ACTION, false);
                                mediaPlayer.setVolume(action ? 0 : 1.0f, action ? 0 : 1.0f);
                            }
                        } else if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
                            updateBatteryMode();
                        }
                    }
                };
                
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
                    
                    // Set initial playback settings based on battery level
                    updateBatteryMode();
                    
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