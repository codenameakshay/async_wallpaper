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

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodChannel;

import com.codenameakshay.async_wallpaper.PigeonApi;

public class AsyncWallpaperPlugin implements FlutterPlugin {
    private MethodChannel channel;
    private PigeonApiImpl pigeonApiImpl;

    @Override
    public void onAttachedToEngine(FlutterPlugin.FlutterPluginBinding binding) {
        pigeonApiImpl = new PigeonApiImpl(binding.getApplicationContext());
        PigeonApi.WallpaperApi.setUp(binding.getBinaryMessenger(), pigeonApiImpl);
    }

    @Override
    public void onDetachedFromEngine(FlutterPlugin.FlutterPluginBinding binding) {
        PigeonApi.WallpaperApi.setUp(binding.getBinaryMessenger(), null);
    }
}