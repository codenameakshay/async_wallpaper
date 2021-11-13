import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:flutter_cache_manager/flutter_cache_manager.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';
  String _wallpaperFileHome = 'Unknown';
  String _wallpaperFileLock = 'Unknown';
  String _wallpaperFileBoth = 'Unknown';
  String _wallpaperUrlHome = 'Unknown';
  String _wallpaperUrlLock = 'Unknown';
  String _wallpaperUrlBoth = 'Unknown';
  String _liveWallpaper = 'Unknown';
  String url = 'https://images.unsplash.com/photo-1635593701810-3156162e184f';
  String liveUrl =
      'https://github.com/codenameakshay/sample-data/raw/main/video3.mp4';

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    // We also handle the message potentially returning null.
    try {
      platformVersion =
          await AsyncWallpaper.platformVersion ?? 'Unknown platform version';
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
    });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> setWallpaperFromFileHome() async {
    setState(() {
      _wallpaperFileHome = 'Loading';
    });
    String result;
    var file = await DefaultCacheManager().getSingleFile(url);
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      result = await AsyncWallpaper.setWallpaperFromFile(
          file.path, AsyncWallpaper.HOME_SCREEN);
    } on PlatformException {
      result = 'Failed to get wallpaper.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _wallpaperFileHome = result;
    });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> setWallpaperFromFileLock() async {
    setState(() {
      _wallpaperFileLock = 'Loading';
    });
    String result;
    var file = await DefaultCacheManager().getSingleFile(url);
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      result = await AsyncWallpaper.setWallpaperFromFile(
          file.path, AsyncWallpaper.LOCK_SCREEN);
    } on PlatformException {
      result = 'Failed to get wallpaper.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _wallpaperFileLock = result;
    });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> setWallpaperFromFileBoth() async {
    setState(() {
      _wallpaperFileBoth = 'Loading';
    });
    String result;
    var file = await DefaultCacheManager().getSingleFile(url);
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      result = await AsyncWallpaper.setWallpaperFromFile(
          file.path, AsyncWallpaper.BOTH_SCREENS);
    } on PlatformException {
      result = 'Failed to get wallpaper.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _wallpaperFileBoth = result;
    });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> setWallpaperHome() async {
    setState(() {
      _wallpaperUrlHome = 'Loading';
    });
    String result;
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      result =
          await AsyncWallpaper.setWallpaper(url, AsyncWallpaper.HOME_SCREEN);
    } on PlatformException {
      result = 'Failed to get wallpaper.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _wallpaperUrlHome = result;
    });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> setWallpaperLock() async {
    setState(() {
      _wallpaperUrlLock = 'Loading';
    });
    String result;
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      result =
          await AsyncWallpaper.setWallpaper(url, AsyncWallpaper.LOCK_SCREEN);
    } on PlatformException {
      result = 'Failed to get wallpaper.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _wallpaperUrlLock = result;
    });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> setWallpaperBoth() async {
    setState(() {
      _wallpaperUrlBoth = 'Loading';
    });
    String result;
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      result =
          await AsyncWallpaper.setWallpaper(url, AsyncWallpaper.BOTH_SCREENS);
    } on PlatformException {
      result = 'Failed to get wallpaper.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _wallpaperUrlBoth = result;
    });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> setLiveWallpaper() async {
    setState(() {
      _liveWallpaper = 'Loading';
    });
    String result;
    var file = await DefaultCacheManager().getSingleFile(liveUrl);
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      result = await AsyncWallpaper.setLiveWallpaper(
        file.path,
      );
    } on PlatformException {
      result = 'Failed to get wallpaper.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _liveWallpaper = result;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Column(
          children: [
            Center(
              child: Text('Running on: $_platformVersion\n'),
            ),
            ElevatedButton(
              onPressed: setWallpaperFromFileHome,
              child: _wallpaperFileHome == 'Loading'
                  ? const CircularProgressIndicator()
                  : const Text('Set wallpaper from file home'),
            ),
            Center(
              child: Text('Wallpaper status: $_wallpaperFileHome\n'),
            ),
            ElevatedButton(
              onPressed: setWallpaperFromFileLock,
              child: _wallpaperFileLock == 'Loading'
                  ? const CircularProgressIndicator()
                  : const Text('Set wallpaper from file lock'),
            ),
            Center(
              child: Text('Wallpaper status: $_wallpaperFileLock\n'),
            ),
            ElevatedButton(
              onPressed: setWallpaperFromFileBoth,
              child: _wallpaperFileBoth == 'Loading'
                  ? const CircularProgressIndicator()
                  : const Text('Set wallpaper from file both'),
            ),
            Center(
              child: Text('Wallpaper status: $_wallpaperFileBoth\n'),
            ),
            ElevatedButton(
              onPressed: setWallpaperHome,
              child: _wallpaperUrlHome == 'Loading'
                  ? const CircularProgressIndicator()
                  : const Text('Set wallpaper from Url home'),
            ),
            Center(
              child: Text('Wallpaper status: $_wallpaperUrlHome\n'),
            ),
            ElevatedButton(
              onPressed: setWallpaperLock,
              child: _wallpaperUrlLock == 'Loading'
                  ? const CircularProgressIndicator()
                  : const Text('Set wallpaper from Url lock'),
            ),
            Center(
              child: Text('Wallpaper status: $_wallpaperUrlLock\n'),
            ),
            ElevatedButton(
              onPressed: setWallpaperBoth,
              child: _wallpaperUrlBoth == 'Loading'
                  ? const CircularProgressIndicator()
                  : const Text('Set wallpaper from Url both'),
            ),
            Center(
              child: Text('Wallpaper status: $_wallpaperUrlBoth\n'),
            ),
            ElevatedButton(
              onPressed: setLiveWallpaper,
              child: _liveWallpaper == 'Loading'
                  ? const CircularProgressIndicator()
                  : const Text('Set live wallpaper'),
            ),
            Center(
              child: Text('Wallpaper status: $_liveWallpaper\n'),
            ),
          ],
        ),
      ),
    );
  }
}
