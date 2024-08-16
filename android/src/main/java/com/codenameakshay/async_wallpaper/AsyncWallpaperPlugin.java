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
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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

        } else if (call.method.equals("set_video_wallpaper")) {
            String url = call.argument("url"); // .argument returns the correct type
            goToHome = call.argument("goToHome"); // .argument returns the correct type
            android.util.Log.i("Arguments ", "configureFlutterEngine: " + url);
            // Picasso.get().load("file://" + url).into(target3);
            copyFile(new File(url), new File(activity.getFilesDir().toPath() + "/file.mp4"));
            redirectToLiveWallpaper = false;
            VideoLiveWallpaper mVideoLiveWallpaper = new VideoLiveWallpaper();
            mVideoLiveWallpaper.setToWallPaper(context);
            result.success(true);

        }
        else if (call.method.equals("open_wallpaper_chooser")) {
            goToHome = call.argument("goToHome"); // .argument returns the correct type
            // TODO: Add logic
            VideoLiveWallpaper mVideoLiveWallpaper = new VideoLiveWallpaper();
            mVideoLiveWallpaper.openWallpaperChooser(context);
            result.success(true);
        }
        else {
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

    public SetWallPaperTask(final Context context) {
        mContext = context;
    }

    @Override
    protected final Boolean doInBackground(Pair<Bitmap, String>... pairs) {
        switch (pairs[0].second) {
            case "1": {
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
                try {
                    int WITH_OTHER_APP_CODE = 733;
                    Uri tempUri = getImageUri(mContext, pairs[0].first);
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
                    mContext.startActivity(
                            chooser,
                            new Bundle(WITH_OTHER_APP_CODE));
                } catch (Exception ex) {
                    try {
                        wallpaperManager.setBitmap(pairs[0].first);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    ex.printStackTrace();
                    return false;
                }
            }
            case "2": {
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        wallpaperManager.setBitmap(pairs[0].first, null, true, WallpaperManager.FLAG_LOCK);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                    return false;
                }
                break;
            }
            case "3": {
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        wallpaperManager.setBitmap(pairs[0].first, null, true, WallpaperManager.FLAG_SYSTEM);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                    return false;
                }
                break;
            }
            case "4": {
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        wallpaperManager.setBitmap(pairs[0].first, null, true,
                                WallpaperManager.FLAG_LOCK | WallpaperManager.FLAG_SYSTEM);
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                    return false;
                }
                break;
            }
        }
        return true;
    }

    @Override
    protected void onPostExecute(Boolean aBoolean) {
        myMethod(aBoolean);
    }

    private void myMethod(Boolean result) {
        AsyncWallpaperPlugin.res.success(result);
    }

    public static Uri getImageContentUri(Context context, String absPath) {

        Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] { MediaStore.Images.Media._ID }, MediaStore.Images.Media.DATA + "=? ",
                new String[] { absPath }, null);

        if (cursor != null && cursor.moveToFirst()) {
            int id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID));
            return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Integer.toString(id));

        } else if (!absPath.isEmpty()) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DATA, absPath);
            return context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        } else {
            return null;
        }
    }

    public Uri getImageUri(Context inContext, Bitmap inImage) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        fixMediaDir();
        String path = MediaStore.Images.Media.insertImage(inContext.getContentResolver(), inImage, "Title", null);
        return Uri.parse(path);
    }

    public String getRealPathFromURI(Uri uri) {
        Cursor cursor = mContext.getContentResolver().query(uri, null, null, null, null);
        cursor.moveToFirst();
        int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
        return cursor.getString(idx);
    }

    void fixMediaDir() {
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