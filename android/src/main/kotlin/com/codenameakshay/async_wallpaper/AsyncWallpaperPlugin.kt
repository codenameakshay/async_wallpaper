package com.codenameakshay.async_wallpaper

import android.util.Log
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel

/** AsyncWallpaperPlugin */
class AsyncWallpaperPlugin: FlutterPlugin, MethodCallHandler {
    private val TAG = "AsyncWallpaperPlugin"
    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var channel: MethodChannel
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        Log.d(TAG, "Attaching plugin to Flutter engine")
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "async_wallpaper")
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        Log.d(TAG, "Method called: ${call.method}")
        
        coroutineScope.launch {
            try {
                when (call.method) {
                    "getPlatformVersion" -> {
                        val version = getPlatformVersion()
                        Log.d(TAG, "Platform version: $version")
                        result.success(version)
                    }
                    else -> {
                        Log.w(TAG, "Method not implemented: ${call.method}")
                        result.notImplemented()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing method ${call.method}", e)
                result.error("EXECUTION_ERROR", e.message, e.stackTraceToString())
            }
        }
    }

    private suspend fun getPlatformVersion(): String = withContext(Dispatchers.IO) {
        try {
            "Android ${android.os.Build.VERSION.RELEASE}"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting platform version", e)
            throw e
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        Log.d(TAG, "Detaching plugin from Flutter engine")
        channel.setMethodCallHandler(null)
        coroutineScope.cancel()
    }
}
