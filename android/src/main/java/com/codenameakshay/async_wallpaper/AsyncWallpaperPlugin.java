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
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
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

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

public class AsyncWallpaperPlugin implements MethodCallHandler {
    private final Context context;
    private static Result res;

    public AsyncWallpaperPlugin(Context context) {
        this.context = context;
    }

    public static void registerWith(MethodChannel channel, Context context) {
        channel.setMethodCallHandler(new AsyncWallpaperPlugin(context));
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        res = result;
        switch (call.method) {
            case "getPlatformVersion":
                result.success("Android " + Build.VERSION.RELEASE);
                break;
            case "checkMaterialYouSupport":
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
                break;
            default:
                result.notImplemented();
                break;
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
            File file = new File(context.getFilesDir(), "wallpaper.jpg");
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();

            // Create an intent to set the wallpaper
            Intent intent = new Intent(Intent.ACTION_ATTACH_DATA);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.setDataAndType(Uri.fromFile(file), "image/jpeg");
            intent.putExtra("mimeType", "image/jpeg");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            // Start the activity
            context.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Class for setting wallpapers asynchronously
    public static class SetWallPaperTask extends AsyncTask<Pair<Bitmap, String>, Void, Boolean> {
        private final Context mContext;
        private static final int MAX_IMAGE_DIMENSION = 2048; // Maximum dimension for compressed images
        private static final int COMPRESSION_QUALITY = 85; // JPEG compression quality (0-100)

        public SetWallPaperTask(final Context context) {
            this.mContext = context;
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
                                File finalFile = new File(getRealPathFromURI(tempUri));
                                Uri contentURI = getImageContentUri(mContext, finalFile.getAbsolutePath());
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
        protected void onPostExecute(Boolean aBoolean) {
            myMethod(aBoolean);
        }

        private void myMethod(Boolean result) {
            AsyncWallpaperPlugin.res.success(result);
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

                // Create an intent to set the wallpaper
                Intent intent = new Intent(Intent.ACTION_ATTACH_DATA);
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                intent.setDataAndType(Uri.fromFile(file), "image/jpeg");
                intent.putExtra("mimeType", "image/jpeg");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // Start the activity
                mContext.startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
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

        public String getRealPathFromURI(Uri uri) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10+, we can't get the real path directly
                // Instead, we'll create a temporary file and copy the content
                try {
                    String[] projection = {MediaStore.Images.Media.DISPLAY_NAME};
                    Cursor cursor = mContext.getContentResolver().query(uri, projection, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        String displayName = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME));
                        cursor.close();
                        
                        File tempFile = new File(mContext.getCacheDir(), displayName);
                        try (FileOutputStream out = new FileOutputStream(tempFile)) {
                            try (FileInputStream in = (FileInputStream) mContext.getContentResolver().openInputStream(uri)) {
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
                    Log.e("SetWallPaperTask", "Error getting real path", e);
                }
                return null;
            } else {
                // Legacy approach for older Android versions
                Cursor cursor = mContext.getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
                    String path = cursor.getString(idx);
                    cursor.close();
                    return path;
                }
                return null;
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

        public SetMaterialYouWallpaperTask(final Context context, boolean enableEffects) {
            this.mContext = context;
            this.enableEffects = enableEffects;
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
            } else {
                Log.e("MaterialYou", "Failed to set Material You wallpaper");
            }
            AsyncWallpaperPlugin.res.success(result);
        }
    }
}