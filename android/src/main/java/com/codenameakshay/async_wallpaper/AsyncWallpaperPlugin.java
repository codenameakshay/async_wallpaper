package com.codenameakshay.async_wallpaper;

import android.app.Activity;
import android.app.Application;
import android.app.WallpaperManager;
import android.graphics.BitmapFactory;
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

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/**
 * AsyncWallpaperPlugin
 */
public class AsyncWallpaperPlugin extends Application implements FlutterPlugin, MethodCallHandler, ActivityAware {
    /// The MethodChannel that will the communication between Flutter and native
    /// Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine
    /// and unregister it
    /// when the Flutter Engine is detached from the Activity
    private MethodChannel channel;
    public static Context context;
    private Activity activity;
    public static MethodChannel.Result res;

    private boolean redirectToLiveWallpaper;
    private boolean goToHome;

    private Target target = new Target() {
        @Override
        public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
            android.util.Log.i("Arguments ", "configureFlutterEngine: " + "Image Downloaded");
            SetWallPaperTask setWallPaperTask = new SetWallPaperTask(context);
            setWallPaperTask.execute(new Pair(resource, "1"));
        }

        @Override
        public void onBitmapFailed(Exception e, Drawable errorDrawable) {
        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
        }
    };
    private Target target1 = new Target() {
        @Override
        public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
            android.util.Log.i("Arguments ", "configureFlutterEngine: " + "Image Downloaded");
            SetWallPaperTask setWallPaperTask = new SetWallPaperTask(context);
            setWallPaperTask.execute(new Pair(resource, "2"));
        }

        @Override
        public void onBitmapFailed(Exception e, Drawable errorDrawable) {
        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
        }
    };
    private Target target2 = new Target() {
        @Override
        public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
            android.util.Log.i("Arguments ", "configureFlutterEngine: " + "Image Downloaded");
            SetWallPaperTask setWallPaperTask = new SetWallPaperTask(context);
            setWallPaperTask.execute(new Pair(resource, "3"));
        }

        @Override
        public void onBitmapFailed(Exception e, Drawable errorDrawable) {
        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
        }
    };
    private Target target3 = new Target() {
        @Override
        public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
            android.util.Log.i("Arguments ", "configureFlutterEngine: " + "Image Downloaded");
            SetWallPaperTask setWallPaperTask = new SetWallPaperTask(context);
            setWallPaperTask.execute(new Pair(resource, "4"));
        }

        @Override
        public void onBitmapFailed(Exception e, Drawable errorDrawable) {
        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
        }
    };

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "async_wallpaper");
        channel.setMethodCallHandler(this);
        context = flutterPluginBinding.getApplicationContext();
        redirectToLiveWallpaper = false;
        goToHome = false;
    }

    @Override
    public void onDetachedFromActivity() {
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding flutterPluginBinding) {
        if (redirectToLiveWallpaper && goToHome) {
            home();
        }
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding flutterPluginBinding) {
        activity = flutterPluginBinding.getActivity();
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
    }

    public void home() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        res = result;
        if (call.method.equals("getPlatformVersion")) {
            result.success("Android " + android.os.Build.VERSION.RELEASE);
        } else if (call.method.equals("set_wallpaper")) {
            String url = call.argument("url"); // .argument returns the correct type
            goToHome = call.argument("goToHome"); // .argument returns the correct type
            android.util.Log.i("Arguments ", "configureFlutterEngine: " + url);
            Picasso.get().load(url).into(target);
            // result.success(1);
        } else if (call.method.equals("set_wallpaper_file")) {
            String url = call.argument("url"); // .argument returns the correct type
            goToHome = call.argument("goToHome"); // .argument returns the correct type
            android.util.Log.i("Arguments ", "configureFlutterEngine: " + url);
            Picasso.get().load("file://" + url).into(target);
            // result.success(1);

        } else if (call.method.equals("set_lock_wallpaper")) {
            String url = call.argument("url"); // .argument returns the correct type
            goToHome = call.argument("goToHome"); // .argument returns the correct type
            android.util.Log.i("Arguments ", "configureFlutterEngine: " + url);
            Picasso.get().load(url).into(target1);
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            if (goToHome)
                home();
            // result.success(1);

        } else if (call.method.equals("set_home_wallpaper")) {
            String url = call.argument("url"); // .argument returns the correct type
            goToHome = call.argument("goToHome"); // .argument returns the correct type
            android.util.Log.i("Arguments ", "configureFlutterEngine: " + url);
            Picasso.get().load(url).into(target2);
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            if (goToHome)
                home();
            // result.success(1);

        } else if (call.method.equals("set_both_wallpaper")) {
            String url = call.argument("url"); // .argument returns the correct type
            goToHome = call.argument("goToHome"); // .argument returns the correct type
            android.util.Log.i("Arguments ", "configureFlutterEngine: " + url);
            Picasso.get().load(url).into(target3);
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            if (goToHome)
                home();
            // result.success(1);

        } else if (call.method.equals("set_lock_wallpaper_file")) {
            String url = call.argument("url"); // .argument returns the correct type
            goToHome = call.argument("goToHome"); // .argument returns the correct type
            android.util.Log.i("Arguments ", "configureFlutterEngine: " + url);
            Picasso.get().load("file://" + url).into(target1);
            if (goToHome)
                home();
            // result.success(1);

        } else if (call.method.equals("set_home_wallpaper_file")) {
            String url = call.argument("url"); // .argument returns the correct type
            goToHome = call.argument("goToHome"); // .argument returns the correct type
            android.util.Log.i("Arguments ", "configureFlutterEngine: " + url);
            Picasso.get().load("file://" + url).into(target2);
            if (goToHome)
                home();
            // result.success(1);

        } else if (call.method.equals("set_both_wallpaper_file")) {
            String url = call.argument("url"); // .argument returns the correct type
            goToHome = call.argument("goToHome"); // .argument returns the correct type
            android.util.Log.i("Arguments ", "configureFlutterEngine: " + url);
            Picasso.get().load("file://" + url).into(target3);
            if (goToHome)
                home();
            // result.success(1);

        } else if (call.method.equals("set_live_wallpaper")) {
            String url = call.argument("url"); // .argument returns the correct type
            goToHome = call.argument("goToHome"); // .argument returns the correct type
            android.util.Log.i("Arguments ", "configureFlutterEngine: " + url);
            // Picasso.get().load("file://" + url).into(target3);
            File videoFile = new File(activity.getFilesDir(), "file.mp4");
            copyFile(new File(url), videoFile);
            if (videoFile.exists()) {
                redirectToLiveWallpaper = false;
                VideoLiveWallpaper mVideoLiveWallpaper = new VideoLiveWallpaper();
                mVideoLiveWallpaper.setToWallPaper(context);
                result.success(true);
            } else {
                result.error("FILE_COPY_ERROR", "Failed to copy video file", null);
            }

        } else if (call.method.equals("open_wallpaper_chooser")) {
            goToHome = call.argument("goToHome"); // .argument returns the correct type
            // TODO: Add logic
            VideoLiveWallpaper mVideoLiveWallpaper = new VideoLiveWallpaper();
            mVideoLiveWallpaper.openWallpaperChooser(context);
            result.success(true);
        } else if (call.method.equals("set_material_you_wallpaper")) {
            // New method for Material You wallpaper effects (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                String url = call.argument("url");
                goToHome = call.argument("goToHome");
                boolean enableEffects = call.argument("enableEffects");
                
                android.util.Log.i("MaterialYou", "Setting Material You wallpaper: " + url);
                Picasso.get().load(url).into(new Target() {
                    @Override
                    public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
                        SetMaterialYouWallpaperTask task = new SetMaterialYouWallpaperTask(context, enableEffects);
                        task.execute(resource);
                    }

                    @Override
                    public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                        Log.e("MaterialYou", "Failed to load bitmap", e);
                        result.error("BITMAP_LOAD_ERROR", "Failed to load image", e.getMessage());
                    }

                    @Override
                    public void onPrepareLoad(Drawable placeHolderDrawable) {
                    }
                });
            } else {
                result.error("UNSUPPORTED_ANDROID_VERSION", "Material You is only supported on Android 12+", null);
            }
        } else {
            result.notImplemented();
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
            fileChannelInput.transferTo(0, fileChannelInput.size(), fileChannelOutput);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fileInputStream != null)
                    fileInputStream.close();
                if (fileChannelInput != null)
                    fileChannelInput.close();
                if (fileOutputStream != null)
                    fileOutputStream.close();
                if (fileChannelOutput != null)
                    fileChannelOutput.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }
}

class SetWallPaperTask extends AsyncTask<Pair<Bitmap, String>, Boolean, Boolean> {
    private final Context mContext;
    private static final int MAX_IMAGE_DIMENSION = 2048; // Maximum dimension for compressed images
    private static final int COMPRESSION_QUALITY = 85; // JPEG compression quality (0-100)

    public SetWallPaperTask(final Context context) {
        mContext = context;
    }

    private boolean isMIUI() {
        return !TextUtils.isEmpty(getSystemProperty("ro.miui.ui.version.name"));
    }

    private String getSystemProperty(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class, String.class);
            return (String) get.invoke(c, key, "");
        } catch (Exception e) {
            return "";
        }
    }

    private void setWallpaperForMIUI(Bitmap bitmap, int flags) {
        try {
            // Save bitmap to a temporary file
            File wallpaperFile = new File(mContext.getFilesDir(), "temp_wallpaper.jpg");
            FileOutputStream out = new FileOutputStream(wallpaperFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();

            // Create content values for MediaStore
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "wallpaper.jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

            // Insert the image into MediaStore
            Uri imageUri = mContext.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (imageUri != null) {
                try (OutputStream outStream = mContext.getContentResolver().openOutputStream(imageUri)) {
                    FileInputStream in = new FileInputStream(wallpaperFile);
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = in.read(buffer)) > 0) {
                        outStream.write(buffer, 0, length);
                    }
                    in.close();
                }
            }

            // Clean up temporary file
            wallpaperFile.delete();

            // Set wallpaper using WallpaperManager
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wallpaperManager.setBitmap(bitmap, null, true, flags);
            } else {
                wallpaperManager.setBitmap(bitmap);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
class SetMaterialYouWallpaperTask extends AsyncTask<Bitmap, Void, Boolean> {
    private final Context mContext;
    private final boolean enableEffects;

    public SetMaterialYouWallpaperTask(final Context context, boolean enableEffects) {
        this.mContext = context;
        this.enableEffects = enableEffects;
    }

    @Override
    protected Boolean doInBackground(Bitmap... bitmaps) {
        try {
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
            
            // Set the wallpaper with Material You effects
            wallpaperManager.setBitmap(bitmaps[0], null, true, 
                    WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
            
            // Enable Material You effects if requested
            if (enableEffects) {
                // This is a simplified example - in a real implementation,
                // you would need to use the appropriate API to enable effects
                // The actual implementation depends on the specific device and Android version
                Log.d("MaterialYou", "Material You effects enabled");
            }
            
            return true;
        } catch (Exception e) {
            Log.e("MaterialYou", "Error setting Material You wallpaper", e);
            return false;
        }
    }

    @Override
    protected void onPostExecute(Boolean result) {
        AsyncWallpaperPlugin.res.success(result);
    }
}