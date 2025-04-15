package com.codenameakshay.async_wallpaper;

import android.app.WallpaperManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

// Import PigeonApi class
import com.codenameakshay.async_wallpaper.PigeonApi;

public class PigeonApiImpl implements PigeonApi.WallpaperApi {
    private final Context context;
    
    public PigeonApiImpl(Context context) {
        this.context = context;
    }

    // Helper method to check if the device is MIUI
    private boolean isMIUI() {
        return !TextUtils.isEmpty(getSystemProperty("ro.miui.ui.version.name"));
    }

    // Helper method to get system properties
    private String getSystemProperty(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class, String.class);
            return (String) get.invoke(c, key, "");
        } catch (Exception e) {
            return "";
        }
    }

    // Helper method to get content URI for an image file
    public static Uri getImageContentUri(Context context, String absPath) {
        // For Android 10+ (API 29+), use MediaStore API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "wallpaper_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
            
            Uri imageUri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (imageUri != null) {
                try (OutputStream out = context.getContentResolver().openOutputStream(imageUri)) {
                    // Copy the file to the new location
                    File sourceFile = new File(absPath);
                    if (sourceFile.exists()) {
                        FileInputStream in = new FileInputStream(sourceFile);
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = in.read(buffer)) > 0) {
                            out.write(buffer, 0, length);
                        }
                        in.close();
                    }
                } catch (IOException e) {
                    Log.e("AsyncWallpaper", "Error copying file", e);
                }
            }
            return imageUri;
        } else {
            // Legacy approach for older Android versions
            Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[] { MediaStore.Images.Media._ID }, MediaStore.Images.Media.DATA + "=? ",
                    new String[] { absPath }, null);

            if (cursor != null && cursor.moveToFirst()) {
                int id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID));
                cursor.close();
                return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Integer.toString(id));
            } else if (!absPath.isEmpty()) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DATA, absPath);
                return context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            } else {
                return null;
            }
        }
    }

    // Helper method to get real path from URI
    public static String getRealPathFromURI(Context context, Uri uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10+, we can't get the real path directly
            // Instead, we'll create a temporary file and copy the content
            try {
                String[] projection = {MediaStore.Images.Media.DISPLAY_NAME};
                Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    String displayName = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME));
                    cursor.close();
                    
                    File tempFile = new File(context.getCacheDir(), displayName);
                    try (FileOutputStream out = new FileOutputStream(tempFile)) {
                        try (FileInputStream in = (FileInputStream) context.getContentResolver().openInputStream(uri)) {
                            byte[] buffer = new byte[1024];
                            int length;
                            while ((length = in.read(buffer)) > 0) {
                                out.write(buffer, 0, length);
                            }
                        }
                    }
                    return tempFile.getAbsolutePath();
                }
            } catch (Exception e) {
                Log.e("AsyncWallpaper", "Error getting real path", e);
            }
            return null;
        } else {
            // Legacy approach for older Android versions
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
                String path = cursor.getString(idx);
                cursor.close();
                return path;
            }
            return null;
        }
    }

    // Helper method to set wallpaper for MIUI devices
    private void setWallpaperForMIUI(Bitmap bitmap, int flag) {
        try {
            // Save the bitmap to a file
            File file = new File(context.getFilesDir(), "wallpaper.jpg");
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();

            // Get content URI for the file
            Uri contentUri = getImageContentUri(context, file.getAbsolutePath());
            if (contentUri == null) {
                Log.e("AsyncWallpaper", "Failed to get content URI for wallpaper file");
                return;
            }

            // Create an intent to set the wallpaper
            Intent intent = new Intent(Intent.ACTION_ATTACH_DATA);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.setDataAndType(contentUri, "image/jpeg");
            intent.putExtra("mimeType", "image/jpeg");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Start the activity
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e("AsyncWallpaper", "Error setting wallpaper for MIUI", e);
        }
    }

    // Class for setting wallpapers asynchronously
    public static class SetWallPaperTask extends AsyncTask<Pair<Bitmap, String>, Void, Boolean> {
        private final Context mContext;
        private static final int MAX_IMAGE_DIMENSION = 2048; // Maximum dimension for compressed images
        private static final int COMPRESSION_QUALITY = 85; // JPEG compression quality (0-100)
        private PigeonApi.Result<Boolean> resultCallback;
        private boolean goToHome;

        public SetWallPaperTask(final Context context) {
            this.mContext = context;
        }

        public SetWallPaperTask(final Context context, PigeonApi.Result<Boolean> resultCallback) {
            this.mContext = context;
            this.resultCallback = resultCallback;
        }

        public SetWallPaperTask(final Context context, PigeonApi.Result<Boolean> resultCallback, boolean goToHome) {
            this.mContext = context;
            this.resultCallback = resultCallback;
            this.goToHome = goToHome;
        }

        private Bitmap compressBitmap(Bitmap original) {
            if (original == null) return null;
            
            int width = original.getWidth();
            int height = original.getHeight();
            
            // Calculate new dimensions while maintaining aspect ratio
            float scale = 1.0f;
            if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
                scale = (float) MAX_IMAGE_DIMENSION / Math.max(width, height);
                width = Math.round(width * scale);
                height = Math.round(height * scale);
            }
            
            // Create a new compressed bitmap
            Bitmap compressed = Bitmap.createScaledBitmap(original, width, height, true);
            
            // Recycle the original bitmap if it's different from the compressed one
            if (compressed != original) {
                original.recycle();
            }
            
            return compressed;
        }

        private void recycleBitmap(Bitmap bitmap) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }

        @Override
        protected final Boolean doInBackground(Pair<Bitmap, String>... pairs) {
            Bitmap compressedBitmap = null;
            try {
                // Compress the bitmap before processing
                compressedBitmap = compressBitmap(pairs[0].first);
                
                switch (pairs[0].second) {
                    case "1": {
                        WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
                        try {
                            if (isMIUI()) {
                                setWallpaperForMIUI(compressedBitmap, WallpaperManager.FLAG_SYSTEM);
                            } else {
                                int WITH_OTHER_APP_CODE = 733;
                                Uri tempUri = getImageUri(mContext, compressedBitmap);
                                Log.i("Arguments ", "configureFlutterEngine: " + "Saved image to storage");
                                File finalFile = new File(PigeonApiImpl.getRealPathFromURI(mContext, tempUri));
                                Uri contentURI = PigeonApiImpl.getImageContentUri(mContext, finalFile.getAbsolutePath());
                                Log.i("Arguments ", "configureFlutterEngine: " + "Opening crop intent");
                                Intent setWall = new Intent(Intent.ACTION_ATTACH_DATA);
                                setWall.setDataAndType(contentURI, "image/*");
                                setWall.putExtra("mimeType", "image/*");
                                setWall.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                setWall.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                Intent chooser = Intent.createChooser(setWall, "Apply with external app");
                                chooser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                mContext.startActivity(chooser, new Bundle(WITH_OTHER_APP_CODE));
                            }
                        } catch (Exception ex) {
                            try {
                                wallpaperManager.setBitmap(compressedBitmap);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            ex.printStackTrace();
                            return false;
                        }
                    }
                    break;
                    case "2": {
                        if (isMIUI()) {
                            setWallpaperForMIUI(compressedBitmap, WallpaperManager.FLAG_LOCK);
                        } else {
                            WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    wallpaperManager.setBitmap(compressedBitmap, null, true, WallpaperManager.FLAG_LOCK);
                                }
                            } catch (IOException ex) {
                                ex.printStackTrace();
                                return false;
                            }
                        }
                        break;
                    }
                    case "3": {
                        if (isMIUI()) {
                            setWallpaperForMIUI(compressedBitmap, WallpaperManager.FLAG_SYSTEM);
                        } else {
                            WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    wallpaperManager.setBitmap(compressedBitmap, null, true, WallpaperManager.FLAG_SYSTEM);
                                }
                            } catch (IOException ex) {
                                ex.printStackTrace();
                                return false;
                            }
                        }
                        break;
                    }
                    case "4": {
                        if (isMIUI()) {
                            setWallpaperForMIUI(compressedBitmap, WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
                        } else {
                            WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    wallpaperManager.setBitmap(compressedBitmap, null, true,
                                            WallpaperManager.FLAG_LOCK | WallpaperManager.FLAG_SYSTEM);
                                }
                            } catch (IOException ex) {
                                ex.printStackTrace();
                                return false;
                            }
                        }
                        break;
                    }
                }
                return true;
            } finally {
                // Ensure bitmap is recycled after use
                recycleBitmap(compressedBitmap);
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                Log.d("AsyncWallpaper", "Wallpaper set successfully");
                if (resultCallback != null) {
                    resultCallback.success(true);
                }
                
                // Navigate to home screen only if wallpaper was set successfully and goToHome is true
                if (goToHome) {
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            goToHomeScreen(mContext);
                        }
                    }, 1000);
                }
            } else {
                Log.e("AsyncWallpaper", "Failed to set wallpaper");
                if (resultCallback != null) {
                    resultCallback.error(new Exception("Failed to set wallpaper"));
                }
            }
        }

        private static void goToHomeScreen(Context context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        }

        // Helper method to check if the device is MIUI
        private boolean isMIUI() {
            return !TextUtils.isEmpty(getSystemProperty("ro.miui.ui.version.name"));
        }

        // Helper method to get system properties
        private String getSystemProperty(String key) {
            try {
                Class<?> c = Class.forName("android.os.SystemProperties");
                Method get = c.getMethod("get", String.class, String.class);
                return (String) get.invoke(c, key, "");
            } catch (Exception e) {
                return "";
            }
        }

        // Helper method to set wallpaper for MIUI devices
        private void setWallpaperForMIUI(Bitmap bitmap, int flag) {
            try {
                // Save the bitmap to a file
                File file = new File(mContext.getFilesDir(), "wallpaper.jpg");
                FileOutputStream out = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                out.flush();
                out.close();

                // Get content URI for the file
                Uri contentUri = PigeonApiImpl.getImageContentUri(mContext, file.getAbsolutePath());
                if (contentUri == null) {
                    Log.e("AsyncWallpaper", "Failed to get content URI for wallpaper file");
                    return;
                }

                // Create an intent to set the wallpaper
                Intent intent = new Intent(Intent.ACTION_ATTACH_DATA);
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                intent.setDataAndType(contentUri, "image/jpeg");
                intent.putExtra("mimeType", "image/jpeg");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // Start the activity
                mContext.startActivity(intent);
            } catch (Exception e) {
                Log.e("AsyncWallpaper", "Error setting wallpaper for MIUI", e);
            }
        }

        public static Uri getImageContentUri(Context context, String absPath) {
            // For Android 10+ (API 29+), use MediaStore API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "wallpaper_" + System.currentTimeMillis() + ".jpg");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
                
                Uri imageUri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (imageUri != null) {
                    try (OutputStream out = context.getContentResolver().openOutputStream(imageUri)) {
                        // Copy the file to the new location
                        File sourceFile = new File(absPath);
                        if (sourceFile.exists()) {
                            FileInputStream in = new FileInputStream(sourceFile);
                            byte[] buffer = new byte[1024];
                            int length;
                            while ((length = in.read(buffer)) > 0) {
                                out.write(buffer, 0, length);
                            }
                            in.close();
                        }
                    } catch (IOException e) {
                        Log.e("SetWallPaperTask", "Error copying file", e);
                    }
                }
                return imageUri;
            } else {
                // Legacy approach for older Android versions
                Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        new String[] { MediaStore.Images.Media._ID }, MediaStore.Images.Media.DATA + "=? ",
                        new String[] { absPath }, null);

                if (cursor != null && cursor.moveToFirst()) {
                    int id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID));
                    cursor.close();
                    return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Integer.toString(id));
                } else if (!absPath.isEmpty()) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DATA, absPath);
                    return context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                } else {
                    return null;
                }
            }
        }

        public Uri getImageUri(Context inContext, Bitmap inImage) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
            
            // For Android 10+ (API 29+), use MediaStore API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "wallpaper_" + System.currentTimeMillis() + ".jpg");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
                
                Uri imageUri = inContext.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (imageUri != null) {
                    try (OutputStream out = inContext.getContentResolver().openOutputStream(imageUri)) {
                        out.write(bytes.toByteArray());
                    } catch (IOException e) {
                        Log.e("SetWallPaperTask", "Error saving image", e);
                    }
                }
                return imageUri;
            } else {
                // Legacy approach for older Android versions
                fixMediaDir();
                String path = MediaStore.Images.Media.insertImage(inContext.getContentResolver(), inImage, "Title", null);
                return Uri.parse(path);
            }
        }

        void fixMediaDir() {
            // For Android 10+, we don't need to create directories manually
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                File sdcard = Environment.getExternalStorageDirectory();
                if (sdcard != null) {
                    File mediaDir = new File(sdcard, "DCIM/Camera");
                    if (!mediaDir.exists()) {
                        mediaDir.mkdirs();
                    }
                }

                if (sdcard != null) {
                    File mediaDir = new File(sdcard, "Pictures");
                    if (!mediaDir.exists()) {
                        mediaDir.mkdirs();
                    }
                }
            }
        }
    }
    
    // New class for Material You wallpaper support (Android 12+)
    @RequiresApi(api = Build.VERSION_CODES.S)
    public static class SetMaterialYouWallpaperTask extends AsyncTask<Bitmap, Void, Boolean> {
        private final Context mContext;
        private final boolean enableEffects;
        private static final int MAX_IMAGE_DIMENSION = 2048; // Maximum dimension for compressed images
        private static final int COMPRESSION_QUALITY = 85; // JPEG compression quality (0-100)
        private PigeonApi.Result<Boolean> resultCallback;
        private boolean goToHome;

        public SetMaterialYouWallpaperTask(final Context context, boolean enableEffects) {
            this.mContext = context;
            this.enableEffects = enableEffects;
        }
        
        public SetMaterialYouWallpaperTask(final Context context, boolean enableEffects, PigeonApi.Result<Boolean> resultCallback) {
            this.mContext = context;
            this.enableEffects = enableEffects;
            this.resultCallback = resultCallback;
        }
        
        public SetMaterialYouWallpaperTask(final Context context, boolean enableEffects, PigeonApi.Result<Boolean> resultCallback, boolean goToHome) {
            this.mContext = context;
            this.enableEffects = enableEffects;
            this.resultCallback = resultCallback;
            this.goToHome = goToHome;
        }

        private Bitmap compressBitmap(Bitmap original) {
            if (original == null) return null;
            
            int width = original.getWidth();
            int height = original.getHeight();
            
            // Calculate new dimensions while maintaining aspect ratio
            float scale = 1.0f;
            if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
                scale = (float) MAX_IMAGE_DIMENSION / Math.max(width, height);
                width = Math.round(width * scale);
                height = Math.round(height * scale);
            }
            
            // Create a new compressed bitmap
            Bitmap compressed = Bitmap.createScaledBitmap(original, width, height, true);
            
            // Recycle the original bitmap if it's different from the compressed one
            if (compressed != original) {
                original.recycle();
            }
            
            return compressed;
        }

        private void recycleBitmap(Bitmap bitmap) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }

        @Override
        protected Boolean doInBackground(Bitmap... bitmaps) {
            Bitmap compressedBitmap = null;
            try {
                // Compress the bitmap before processing
                compressedBitmap = compressBitmap(bitmaps[0]);
                
                if (compressedBitmap == null) {
                    Log.e("MaterialYou", "Failed to compress bitmap");
                    return false;
                }
                
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
                
                // Set the wallpaper with Material You effects
                try {
                    // For Android 12+ (API 31+), we can use the setBitmap method with flags
                    wallpaperManager.setBitmap(compressedBitmap, null, true, 
                            WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
                    
                    // Enable Material You effects if requested
                    if (enableEffects) {
                        boolean effectsEnabled = false;
                        
                        // Try multiple approaches to enable Material You effects
                        try {
                            // Approach 1: Use reflection to access the setColorScheme method
                            Method setColorSchemeMethod = WallpaperManager.class.getMethod("setColorScheme", int.class);
                            if (setColorSchemeMethod != null) {
                                // 1 is typically the value for Material You color scheme
                                setColorSchemeMethod.invoke(wallpaperManager, 1);
                                Log.d("MaterialYou", "Material You effects enabled via reflection (setColorScheme)");
                                effectsEnabled = true;
                            }
                        } catch (Exception e) {
                            Log.d("MaterialYou", "Failed to enable Material You effects via setColorScheme", e);
                        }
                        
                        // If the first approach failed, try another approach
                        if (!effectsEnabled) {
                            try {
                                // Approach 2: Try to use the setColorSchemeEnabled method
                                Method setColorSchemeEnabledMethod = WallpaperManager.class.getMethod("setColorSchemeEnabled", boolean.class);
                                if (setColorSchemeEnabledMethod != null) {
                                    setColorSchemeEnabledMethod.invoke(wallpaperManager, true);
                                    Log.d("MaterialYou", "Material You effects enabled via reflection (setColorSchemeEnabled)");
                                    effectsEnabled = true;
                                }
                            } catch (Exception e) {
                                Log.d("MaterialYou", "Failed to enable Material You effects via setColorSchemeEnabled", e);
                            }
                        }
                        
                        // If all approaches failed, log a warning
                        if (!effectsEnabled) {
                            Log.w("MaterialYou", "Could not enable Material You effects - device may not support this feature");
                        }
                    }
                    
                    return true;
                } catch (Exception e) {
                    Log.e("MaterialYou", "Error setting Material You wallpaper", e);
                    return false;
                }
            } catch (Exception e) {
                Log.e("MaterialYou", "Error in doInBackground", e);
                return false;
            } finally {
                // Ensure bitmap is recycled after use
                recycleBitmap(compressedBitmap);
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                Log.d("MaterialYou", "Material You wallpaper set successfully");
                if (resultCallback != null) {
                    resultCallback.success(true);
                }
                
                // Navigate to home screen only if wallpaper was set successfully and goToHome is true
                if (goToHome) {
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            goToHomeScreen(mContext);
                        }
                    }, 1000);
                }
            } else {
                Log.e("MaterialYou", "Failed to set Material You wallpaper");
                if (resultCallback != null) {
                    resultCallback.error(new Exception("Failed to set Material You wallpaper"));
                }
            }
        }
        
        private static void goToHomeScreen(Context context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        }
    }

    @java.lang.Override
    public void getPlatformVersion(@NonNull PigeonApi.Result<String> result) {
        result.success("Android " + Build.VERSION.RELEASE);
    }

    @java.lang.Override
    public void checkMaterialYouSupport(@NonNull PigeonApi.Result<Map<String, Object>> result) {
        Map<String, Object> deviceInfo = new HashMap<>();
        deviceInfo.put("androidVersion", Build.VERSION.RELEASE);
        deviceInfo.put("sdkInt", Build.VERSION.SDK_INT);
        deviceInfo.put("manufacturer", Build.MANUFACTURER);
        deviceInfo.put("model", Build.MODEL);
        
        boolean supportsMaterialYou = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                // Check if the device supports Material You
                Class<?> wallpaperManagerClass = Class.forName("android.app.WallpaperManager");
                wallpaperManagerClass.getMethod("setColorScheme", int.class);
                supportsMaterialYou = true;
            } catch (Exception e) {
                Log.d("MaterialYou", "Device does not support Material You color scheme API");
            }
        }
        
        deviceInfo.put("supportsMaterialYou", supportsMaterialYou);
        result.success(deviceInfo);
    }

    @java.lang.Override
    public void setHomeWallpaperFromUrl(@NonNull String url, @NonNull Boolean goToHome, @NonNull PigeonApi.Result<Boolean> result) {
        // Create a Target to handle the bitmap loading
        Target target = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
                Log.i("AsyncWallpaper", "Image Downloaded");
                SetWallPaperTask setWallPaperTask = new SetWallPaperTask(context, result, goToHome);
                setWallPaperTask.execute(new Pair<>(resource, "3")); // "3" is for home wallpaper
            }

            @Override
            public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                Log.e("AsyncWallpaper", "Failed to load image", e);
                // Report failure to Flutter
                result.error(e);
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
                Log.d("AsyncWallpaper", "Preparing to load image");
            }
        };
        
        try {
            // Load the image using Picasso
            Picasso.get().load(url).into(target);
        } catch (Exception e) {
            Log.e("AsyncWallpaper", "Error loading image", e);
            result.error(e);
        }
    }

    private void goToHomeScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    @java.lang.Override
    public void setLockWallpaperFromUrl(@NonNull String url, @NonNull Boolean goToHome, @NonNull PigeonApi.Result<Boolean> result) {
        // Create a Target to handle the bitmap loading
        Target target = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
                Log.i("AsyncWallpaper", "Image Downloaded");
                SetWallPaperTask setWallPaperTask = new SetWallPaperTask(context, result, goToHome);
                setWallPaperTask.execute(new Pair<>(resource, "2")); // "2" is for lock wallpaper
            }

            @Override
            public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                Log.e("AsyncWallpaper", "Failed to load image", e);
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
                Log.d("AsyncWallpaper", "Preparing to load image");
            }
        };
        
        // Load the image using Picasso
        Picasso.get().load(url).into(target);
    }

    @java.lang.Override
    public void setBothWallpaperFromUrl(@NonNull String url, @NonNull Boolean goToHome, @NonNull PigeonApi.Result<Boolean> result) {
        // Create a Target to handle the bitmap loading
        Target target = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
                Log.i("AsyncWallpaper", "Image Downloaded");
                SetWallPaperTask setWallPaperTask = new SetWallPaperTask(context, result, goToHome);
                setWallPaperTask.execute(new Pair<>(resource, "4")); // "4" is for both home and lock wallpaper
            }

            @Override
            public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                Log.e("AsyncWallpaper", "Failed to load image", e);
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
                Log.d("AsyncWallpaper", "Preparing to load image");
            }
        };
        
        // Load the image using Picasso
        Picasso.get().load(url).into(target);
    }

    @java.lang.Override
    public void setWallpaper(@NonNull String url, @NonNull Boolean goToHome, @NonNull PigeonApi.Result<Boolean> result) {
        // Create a Target to handle the bitmap loading
        Target target = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
                Log.i("AsyncWallpaper", "Image Downloaded");
                SetWallPaperTask setWallPaperTask = new SetWallPaperTask(context, result, goToHome);
                setWallPaperTask.execute(new Pair<>(resource, "1")); // "1" is for default wallpaper
            }

            @Override
            public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                Log.e("AsyncWallpaper", "Failed to load image", e);
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
                Log.d("AsyncWallpaper", "Preparing to load image");
            }
        };
        
        // Load the image using Picasso
        Picasso.get().load(url).into(target);
    }

    @java.lang.Override
    public void setHomeWallpaperFromFile(@NonNull String filePath, @NonNull Boolean goToHome, @NonNull PigeonApi.Result<Boolean> result) {
        // Create a Target to handle the bitmap loading
        Target target = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
                Log.i("AsyncWallpaper", "Image Downloaded from file");
                SetWallPaperTask setWallPaperTask = new SetWallPaperTask(context, result, goToHome);
                setWallPaperTask.execute(new Pair<>(resource, "3")); // "3" is for home wallpaper
            }

            @Override
            public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                Log.e("AsyncWallpaper", "Failed to load image from file", e);
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
                Log.d("AsyncWallpaper", "Preparing to load image from file");
            }
        };
        
        // Load the image using Picasso with file:// prefix
        Picasso.get().load("file://" + filePath).into(target);
    }

    @java.lang.Override
    public void setLockWallpaperFromFile(@NonNull String filePath, @NonNull Boolean goToHome, @NonNull PigeonApi.Result<Boolean> result) {
        // Create a Target to handle the bitmap loading
        Target target = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
                Log.i("AsyncWallpaper", "Image Downloaded from file");
                SetWallPaperTask setWallPaperTask = new SetWallPaperTask(context, result, goToHome);
                setWallPaperTask.execute(new Pair<>(resource, "2")); // "2" is for lock wallpaper
            }

            @Override
            public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                Log.e("AsyncWallpaper", "Failed to load image from file", e);
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
                Log.d("AsyncWallpaper", "Preparing to load image from file");
            }
        };
        
        // Load the image using Picasso with file:// prefix
        Picasso.get().load("file://" + filePath).into(target);
    }

    @java.lang.Override
    public void setBothWallpaperFromFile(@NonNull String filePath, @NonNull Boolean goToHome, @NonNull PigeonApi.Result<Boolean> result) {
        // Create a Target to handle the bitmap loading
        Target target = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
                Log.i("AsyncWallpaper", "Image Downloaded from file");
                SetWallPaperTask setWallPaperTask = new SetWallPaperTask(context, result, goToHome);
                setWallPaperTask.execute(new Pair<>(resource, "4")); // "4" is for both home and lock wallpaper
            }

            @Override
            public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                Log.e("AsyncWallpaper", "Failed to load image from file", e);
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
                Log.d("AsyncWallpaper", "Preparing to load image from file");
            }
        };
        
        // Load the image using Picasso with file:// prefix
        Picasso.get().load("file://" + filePath).into(target);
    }

    @java.lang.Override
    public void setWallpaperFromFile(@NonNull String filePath, @NonNull Boolean goToHome, @NonNull PigeonApi.Result<Boolean> result) {
        // Create a Target to handle the bitmap loading
        Target target = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
                Log.i("AsyncWallpaper", "Image Downloaded from file");
                SetWallPaperTask setWallPaperTask = new SetWallPaperTask(context, result, goToHome);
                setWallPaperTask.execute(new Pair<>(resource, "1")); // "1" is for default wallpaper
            }

            @Override
            public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                Log.e("AsyncWallpaper", "Failed to load image from file", e);
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
                Log.d("AsyncWallpaper", "Preparing to load image from file");
            }
        };
        
        // Load the image using Picasso with file:// prefix
        Picasso.get().load("file://" + filePath).into(target);
    }

    @java.lang.Override
    public void setLiveWallpaper(@NonNull String filePath, @NonNull Boolean goToHome, @NonNull PigeonApi.Result<Boolean> result) {
        try {
            // Create a file in the app's files directory
            File videoFile = new File(context.getFilesDir(), "live_wallpaper.mp4");
            
            // Copy the file from the provided path to the app's files directory
            copyFile(new File(filePath), videoFile);
            
            if (videoFile.exists()) {
                // Set the live wallpaper
                VideoLiveWallpaper videoLiveWallpaper = new VideoLiveWallpaper();
                videoLiveWallpaper.setToWallPaper(context);
                
                // If goToHome is true, navigate to home screen after a short delay
                if (goToHome) {
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            goToHomeScreen();
                        }
                    }, 1000);
                }
                
                Log.i("AsyncWallpaper", "Live wallpaper set successfully");
                result.success(true);
            } else {
                Log.e("AsyncWallpaper", "Failed to copy video file");
                result.error(new Exception("Failed to copy video file"));
            }
        } catch (Exception e) {
            Log.e("AsyncWallpaper", "Error setting live wallpaper", e);
            result.error(e);
        }
    }

    private void copyFile(File sourceFile, File destFile) throws IOException {
        if (!destFile.exists()) {
            destFile.createNewFile();
        }
        
        FileInputStream source = null;
        FileOutputStream destination = null;
        
        try {
            source = new FileInputStream(sourceFile);
            destination = new FileOutputStream(destFile);
            
            byte[] buffer = new byte[1024];
            int length;
            
            while ((length = source.read(buffer)) > 0) {
                destination.write(buffer, 0, length);
            }
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
    }

    @java.lang.Override
    public void openWallpaperChooser(@NonNull PigeonApi.Result<Boolean> result) {
        try {
            // Create a VideoLiveWallpaper instance
            VideoLiveWallpaper videoLiveWallpaper = new VideoLiveWallpaper();
            
            // Open the system wallpaper chooser
            videoLiveWallpaper.openWallpaperChooser(context);
            
            Log.i("AsyncWallpaper", "Wallpaper chooser opened successfully");
            result.success(true);
        } catch (Exception e) {
            Log.e("AsyncWallpaper", "Error opening wallpaper chooser", e);
            result.error(e);
        }
    }

    @java.lang.Override
    public void setMaterialYouWallpaper(@NonNull String url, @NonNull Boolean goToHome, @NonNull Boolean enableEffects, @NonNull PigeonApi.Result<Boolean> result) {
        Picasso.get().load(url).into(new Target() {
            @Override
            public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
                Log.d("MaterialYou", "Bitmap loaded successfully, size: " + resource.getWidth() + "x" + resource.getHeight());
                SetMaterialYouWallpaperTask task = new SetMaterialYouWallpaperTask(context, enableEffects, result, goToHome);
                task.execute(resource);
            }

            @Override
            public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                Log.e("MaterialYou", "Failed to load bitmap", e);
                result.error(e);
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
                Log.d("MaterialYou", "Preparing to load image");
            }
        });
    }
}
