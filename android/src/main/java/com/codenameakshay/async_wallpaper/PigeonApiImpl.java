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

// Import the task classes from AsyncWallpaperPlugin
import com.codenameakshay.async_wallpaper.AsyncWallpaperPlugin.SetWallPaperTask;
import com.codenameakshay.async_wallpaper.AsyncWallpaperPlugin.SetMaterialYouWallpaperTask;

// Import PigeonApi class
import com.codenameakshay.async_wallpaper.PigeonApi;

public class PigeonApiImpl implements PigeonApi.WallpaperApi {
    private final Context context;
    
    public PigeonApiImpl(Context context) {
        this.context = context;
    }

    @Override
    public String getPlatformVersion() {
        return "Android " + Build.VERSION.RELEASE;
    }
    
    @Override
    public Map<String, Object> checkMaterialYouSupport() {
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
        return deviceInfo;
    }

    @Override
    public Boolean setHomeWallpaperFromUrl(String url, Boolean goToHome) {
        // Create a Target to handle the bitmap loading
        Target target = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
                Log.i("AsyncWallpaper", "Image Downloaded");
                SetWallPaperTask setWallPaperTask = new SetWallPaperTask(context);
                setWallPaperTask.execute(new Pair<>(resource, "3")); // "3" is for home wallpaper
                
                // If goToHome is true, navigate to home screen after a short delay
                if (goToHome) {
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            goToHomeScreen();
                        }
                    }, 1000);
                }
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
        
        return true;
    }
    
    private void goToHomeScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    @Override
    public Boolean setLockWallpaperFromUrl(String url, Boolean goToHome) {
        // Create a Target to handle the bitmap loading
        Target target = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
                Log.i("AsyncWallpaper", "Image Downloaded");
                SetWallPaperTask setWallPaperTask = new SetWallPaperTask(context);
                setWallPaperTask.execute(new Pair<>(resource, "2")); // "2" is for lock wallpaper
                
                // If goToHome is true, navigate to home screen after a short delay
                if (goToHome) {
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            goToHomeScreen();
                        }
                    }, 1000);
                }
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
        
        return true;
    }

    @Override
    public Boolean setBothWallpaperFromUrl(String url, Boolean goToHome) {
        // Create a Target to handle the bitmap loading
        Target target = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
                Log.i("AsyncWallpaper", "Image Downloaded");
                SetWallPaperTask setWallPaperTask = new SetWallPaperTask(context);
                setWallPaperTask.execute(new Pair<>(resource, "4")); // "4" is for both home and lock wallpaper
                
                // If goToHome is true, navigate to home screen after a short delay
                if (goToHome) {
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            goToHomeScreen();
                        }
                    }, 1000);
                }
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
        
        return true;
    }

    @Override
    public Boolean setWallpaper(String url, Boolean goToHome) {
        // Create a Target to handle the bitmap loading
        Target target = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
                Log.i("AsyncWallpaper", "Image Downloaded");
                SetWallPaperTask setWallPaperTask = new SetWallPaperTask(context);
                setWallPaperTask.execute(new Pair<>(resource, "1")); // "1" is for default wallpaper
                
                // If goToHome is true, navigate to home screen after a short delay
                if (goToHome) {
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            goToHomeScreen();
                        }
                    }, 1000);
                }
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
        
        return true;
    }

    @Override
    public Boolean setHomeWallpaperFromFile(String filePath, Boolean goToHome) {
        // Create a Target to handle the bitmap loading
        Target target = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
                Log.i("AsyncWallpaper", "Image Downloaded from file");
                SetWallPaperTask setWallPaperTask = new SetWallPaperTask(context);
                setWallPaperTask.execute(new Pair<>(resource, "3")); // "3" is for home wallpaper
                
                // If goToHome is true, navigate to home screen after a short delay
                if (goToHome) {
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            goToHomeScreen();
                        }
                    }, 1000);
                }
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
        
        return true;
    }

    @Override
    public Boolean setLockWallpaperFromFile(String filePath, Boolean goToHome) {
        // Create a Target to handle the bitmap loading
        Target target = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
                Log.i("AsyncWallpaper", "Image Downloaded from file");
                SetWallPaperTask setWallPaperTask = new SetWallPaperTask(context);
                setWallPaperTask.execute(new Pair<>(resource, "2")); // "2" is for lock wallpaper
                
                // If goToHome is true, navigate to home screen after a short delay
                if (goToHome) {
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            goToHomeScreen();
                        }
                    }, 1000);
                }
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
        
        return true;
    }

    @Override
    public Boolean setBothWallpaperFromFile(String filePath, Boolean goToHome) {
        // Create a Target to handle the bitmap loading
        Target target = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
                Log.i("AsyncWallpaper", "Image Downloaded from file");
                SetWallPaperTask setWallPaperTask = new SetWallPaperTask(context);
                setWallPaperTask.execute(new Pair<>(resource, "4")); // "4" is for both home and lock wallpaper
                
                // If goToHome is true, navigate to home screen after a short delay
                if (goToHome) {
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            goToHomeScreen();
                        }
                    }, 1000);
                }
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
        
        return true;
    }

    @Override
    public Boolean setWallpaperFromFile(String filePath, Boolean goToHome) {
        // Create a Target to handle the bitmap loading
        Target target = new Target() {
            @Override
            public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
                Log.i("AsyncWallpaper", "Image Downloaded from file");
                SetWallPaperTask setWallPaperTask = new SetWallPaperTask(context);
                setWallPaperTask.execute(new Pair<>(resource, "1")); // "1" is for default wallpaper
                
                // If goToHome is true, navigate to home screen after a short delay
                if (goToHome) {
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            goToHomeScreen();
                        }
                    }, 1000);
                }
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
        
        return true;
    }

    @Override
    public Boolean setLiveWallpaper(String filePath, Boolean goToHome) {
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
                return true;
            } else {
                Log.e("AsyncWallpaper", "Failed to copy video file");
                return false;
            }
        } catch (Exception e) {
            Log.e("AsyncWallpaper", "Error setting live wallpaper", e);
            return false;
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

    @Override
    public Boolean openWallpaperChooser() {
        try {
            // Create a VideoLiveWallpaper instance
            VideoLiveWallpaper videoLiveWallpaper = new VideoLiveWallpaper();
            
            // Open the system wallpaper chooser
            videoLiveWallpaper.openWallpaperChooser(context);
            
            Log.i("AsyncWallpaper", "Wallpaper chooser opened successfully");
            return true;
        } catch (Exception e) {
            Log.e("AsyncWallpaper", "Error opening wallpaper chooser", e);
            return false;
        }
    }
    
    @Override
    public Boolean setMaterialYouWallpaper(String url, Boolean goToHome, Boolean enableEffects) {
        Picasso.get().load(url).into(new Target() {
            @Override
            public void onBitmapLoaded(Bitmap resource, Picasso.LoadedFrom from) {
                Log.d("MaterialYou", "Bitmap loaded successfully, size: " + resource.getWidth() + "x" + resource.getHeight());
                SetMaterialYouWallpaperTask task = new SetMaterialYouWallpaperTask(context, enableEffects);
                task.execute(resource);
                
                // If goToHome is true, navigate to home screen after a short delay
                if (goToHome) {
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            goToHomeScreen();
                        }
                    }, 1000);
                }
            }

            @Override
            public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                Log.e("MaterialYou", "Failed to load bitmap", e);
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
                Log.d("MaterialYou", "Preparing to load image");
            }
        });
        return true;
    }
}
