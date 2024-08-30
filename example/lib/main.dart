import 'dart:async';

import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_cache_manager/flutter_cache_manager.dart';

void main() {
  runApp(const MaterialApp(
    restorationScopeId: 'async_wallpaper_app',
    home: MyApp(),
  ));
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> with RestorationMixin {
  final RestorableString _platformVersion = RestorableString('Unknown');
  final RestorableString _wallpaperFileNative = RestorableString('Unknown');
  final RestorableString _wallpaperFileHome = RestorableString('Unknown');
  final RestorableString _wallpaperFileLock = RestorableString('Unknown');
  final RestorableString _wallpaperFileBoth = RestorableString('Unknown');
  final RestorableString _wallpaperUrlNative = RestorableString('Unknown');
  final RestorableString _wallpaperUrlHome = RestorableString('Unknown');
  final RestorableString _wallpaperUrlLock = RestorableString('Unknown');
  final RestorableString _wallpaperUrlBoth = RestorableString('Unknown');
  final RestorableString _liveWallpaper = RestorableString('Unknown');
  final RestorableString _wallpaperChooser = RestorableString('Unknown');
  String url = 'https://images.unsplash.com/photo-1635593701810-3156162e184f';
  String liveUrl = 'https://github.com/codenameakshay/sample-data/raw/main/video3.mp4';

  late bool goToHome;

  @override
  void initState() {
    super.initState();
    goToHome = false;
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    // We also handle the message potentially returning null.
    try {
      platformVersion = await AsyncWallpaper.platformVersion ?? 'Unknown platform version';
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _platformVersion.value = platformVersion;
    });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> setWallpaperFromFileNative() async {
    setState(() {
      _wallpaperFileNative.value = 'Loading';
    });
    String result;
    var file = await DefaultCacheManager().getSingleFile(url);
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      result = await AsyncWallpaper.setWallpaperFromFileNative(
        filePath: file.path,
        goToHome: goToHome,
        toastDetails: ToastDetails.success(),
        errorToastDetails: ToastDetails.error(),
      )
          ? 'Wallpaper set'
          : 'Failed to get wallpaper.';
    } on PlatformException {
      result = 'Failed to get wallpaper.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _wallpaperFileNative.value = result;
    });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> setWallpaperFromFileHome() async {
    setState(() {
      _wallpaperFileHome.value = 'Loading';
    });
    String result;
    var file = await DefaultCacheManager().getSingleFile(url);
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      result = await AsyncWallpaper.setWallpaperFromFile(
        filePath: file.path,
        wallpaperLocation: AsyncWallpaper.HOME_SCREEN,
        goToHome: goToHome,
        toastDetails: ToastDetails.success(),
        errorToastDetails: ToastDetails.error(),
      )
          ? 'Wallpaper set'
          : 'Failed to get wallpaper.';
    } on PlatformException {
      result = 'Failed to get wallpaper.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _wallpaperFileHome.value = result;
    });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> setWallpaperFromFileLock() async {
    setState(() {
      _wallpaperFileLock.value = 'Loading';
    });
    String result;
    var file = await DefaultCacheManager().getSingleFile(url);
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      result = await AsyncWallpaper.setWallpaperFromFile(
        filePath: file.path,
        wallpaperLocation: AsyncWallpaper.LOCK_SCREEN,
        goToHome: goToHome,
        toastDetails: ToastDetails.success(),
        errorToastDetails: ToastDetails.error(),
      )
          ? 'Wallpaper set'
          : 'Failed to get wallpaper.';
    } on PlatformException {
      result = 'Failed to get wallpaper.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _wallpaperFileLock.value = result;
    });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> setWallpaperFromFileBoth() async {
    setState(() {
      _wallpaperFileBoth.value = 'Loading';
    });
    String result;
    var file = await DefaultCacheManager().getSingleFile(url);
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      result = await AsyncWallpaper.setWallpaperFromFile(
        filePath: file.path,
        wallpaperLocation: AsyncWallpaper.BOTH_SCREENS,
        goToHome: goToHome,
        toastDetails: ToastDetails.success(),
        errorToastDetails: ToastDetails.error(),
      )
          ? 'Wallpaper set'
          : 'Failed to get wallpaper.';
    } on PlatformException {
      result = 'Failed to get wallpaper.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _wallpaperFileBoth.value = result;
    });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> setWallpaperNative() async {
    setState(() {
      _wallpaperUrlNative.value = 'Loading';
    });
    String result;
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      result = await AsyncWallpaper.setWallpaperNative(
        url: url,
        goToHome: goToHome,
        toastDetails: ToastDetails.success(),
        errorToastDetails: ToastDetails.error(),
      )
          ? 'Wallpaper set'
          : 'Failed to get wallpaper.';
    } on PlatformException {
      result = 'Failed to get wallpaper.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _wallpaperUrlNative.value = result;
    });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> setWallpaperHome() async {
    setState(() {
      _wallpaperUrlHome.value = 'Loading';
    });
    String result;
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      result = await AsyncWallpaper.setWallpaper(
        url: url,
        wallpaperLocation: AsyncWallpaper.HOME_SCREEN,
        goToHome: goToHome,
        toastDetails: ToastDetails.success(),
        errorToastDetails: ToastDetails.error(),
      )
          ? 'Wallpaper set'
          : 'Failed to get wallpaper.';
    } on PlatformException {
      result = 'Failed to get wallpaper.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _wallpaperUrlHome.value = result;
    });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> setWallpaperLock() async {
    setState(() {
      _wallpaperUrlLock.value = 'Loading';
    });
    String result;
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      result = await AsyncWallpaper.setWallpaper(
        url: url,
        wallpaperLocation: AsyncWallpaper.LOCK_SCREEN,
        goToHome: goToHome,
        toastDetails: ToastDetails.success(),
        errorToastDetails: ToastDetails.error(),
      )
          ? 'Wallpaper set'
          : 'Failed to get wallpaper.';
    } on PlatformException {
      result = 'Failed to get wallpaper.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _wallpaperUrlLock.value = result;
    });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> setWallpaperBoth() async {
    setState(() {
      _wallpaperUrlBoth.value = 'Loading';
    });
    String result;
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      result = await AsyncWallpaper.setWallpaper(
        url: url,
        wallpaperLocation: AsyncWallpaper.BOTH_SCREENS,
        goToHome: goToHome,
        toastDetails: ToastDetails.success(),
        errorToastDetails: ToastDetails.error(),
      )
          ? 'Wallpaper set'
          : 'Failed to get wallpaper.';
    } on PlatformException {
      result = 'Failed to get wallpaper.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _wallpaperUrlBoth.value = result;
    });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> setLiveWallpaper() async {
    setState(() {
      _liveWallpaper.value = 'Loading';
    });
    String result;
    var file = await DefaultCacheManager().getSingleFile(liveUrl);
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      result = await AsyncWallpaper.setLiveWallpaper(
        filePath: file.path,
        goToHome: goToHome,
        toastDetails: ToastDetails.success(),
        errorToastDetails: ToastDetails.error(),
      )
          ? 'Wallpaper set'
          : 'Failed to get wallpaper.';
    } on PlatformException {
      result = 'Failed to get wallpaper.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _liveWallpaper.value = result;
    });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> openWallpaperChooser() async {
    setState(() {
      _wallpaperChooser.value = 'Loading';
    });
    String result;
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      result = await AsyncWallpaper.openWallpaperChooser(
        goToHome: goToHome,
        toastDetails: ToastDetails.wallpaperChooser(),
        errorToastDetails: ToastDetails.error(),
      )
          ? 'Wallpaper set'
          : 'Failed to get wallpaper.';
    } on PlatformException {
      result = 'Failed to get wallpaper.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _liveWallpaper.value = result;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Plugin example app'),
      ),
      body: SingleChildScrollView(
        child: Column(
          children: [
            Center(
              child: Text('Running on: ${_platformVersion.value}\n'),
            ),
            SwitchListTile(
                title: const Text('Go to home'),
                value: goToHome,
                onChanged: (value) {
                  setState(() {
                    goToHome = value;
                  });
                }),
            ElevatedButton(
              onPressed: setWallpaperFromFileNative,
              child: _wallpaperFileNative.value == 'Loading'
                  ? const CircularProgressIndicator()
                  : const Text('Set wallpaper from file native'),
            ),
            Center(
              child: Text('Wallpaper status: ${_wallpaperFileNative.value}\n'),
            ),
            ElevatedButton(
              onPressed: setWallpaperFromFileHome,
              child: _wallpaperFileHome.value == 'Loading'
                  ? const CircularProgressIndicator()
                  : const Text('Set wallpaper from file home'),
            ),
            Center(
              child: Text('Wallpaper status: ${_wallpaperFileHome.value}\n'),
            ),
            ElevatedButton(
              onPressed: setWallpaperFromFileLock,
              child: _wallpaperFileLock.value == 'Loading'
                  ? const CircularProgressIndicator()
                  : const Text('Set wallpaper from file lock'),
            ),
            Center(
              child: Text('Wallpaper status: ${_wallpaperFileLock.value}\n'),
            ),
            ElevatedButton(
              onPressed: setWallpaperFromFileBoth,
              child: _wallpaperFileBoth.value == 'Loading'
                  ? const CircularProgressIndicator()
                  : const Text('Set wallpaper from file both'),
            ),
            Center(
              child: Text('Wallpaper status: ${_wallpaperFileBoth.value}\n'),
            ),
            ElevatedButton(
              onPressed: setWallpaperNative,
              child: _wallpaperUrlNative.value == 'Loading'
                  ? const CircularProgressIndicator()
                  : const Text('Set wallpaper from Url native'),
            ),
            Center(
              child: Text('Wallpaper status: ${_wallpaperUrlNative.value}\n'),
            ),
            ElevatedButton(
              onPressed: setWallpaperHome,
              child: _wallpaperUrlHome.value == 'Loading'
                  ? const CircularProgressIndicator()
                  : const Text('Set wallpaper from Url home'),
            ),
            Center(
              child: Text('Wallpaper status: ${_wallpaperUrlHome.value}\n'),
            ),
            ElevatedButton(
              onPressed: setWallpaperLock,
              child: _wallpaperUrlLock.value == 'Loading'
                  ? const CircularProgressIndicator()
                  : const Text('Set wallpaper from Url lock'),
            ),
            Center(
              child: Text('Wallpaper status: ${_wallpaperUrlLock.value}\n'),
            ),
            ElevatedButton(
              onPressed: setWallpaperBoth,
              child: _wallpaperUrlBoth.value == 'Loading'
                  ? const CircularProgressIndicator()
                  : const Text('Set wallpaper from Url both'),
            ),
            Center(
              child: Text('Wallpaper status: ${_wallpaperUrlBoth.value}\n'),
            ),
            ElevatedButton(
              onPressed: setLiveWallpaper,
              child: _liveWallpaper.value == 'Loading'
                  ? const CircularProgressIndicator()
                  : const Text('Set live wallpaper'),
            ),
            Center(
              child: Text('Wallpaper status: ${_liveWallpaper.value}\n'),
            ),
            ElevatedButton(
              onPressed: openWallpaperChooser,
              child: _wallpaperChooser.value == 'Loading'
                  ? const CircularProgressIndicator()
                  : const Text('Open wallpaper chooser'),
            ),
            Center(
              child: Text('Wallpaper status: ${_wallpaperChooser.value}\n'),
            ),
          ],
        ),
      ),
    );
  }

  @override
  String? get restorationId => '_MyAppState';

  @override
  void restoreState(RestorationBucket? oldBucket, bool initialRestore) {
    registerForRestoration(_platformVersion, 'platform_version');
    registerForRestoration(_wallpaperFileNative, 'wallpaper_file_native');
    registerForRestoration(_wallpaperFileHome, 'wallpaper_file_home');
    registerForRestoration(_wallpaperFileLock, 'wallpaper_file_lock');
    registerForRestoration(_wallpaperFileBoth, 'wallpaper_file_both');
    registerForRestoration(_wallpaperUrlNative, 'wallpaper_url_native');
    registerForRestoration(_wallpaperUrlHome, 'wallpaper_url_home');
    registerForRestoration(_wallpaperUrlLock, 'wallpaper_url_lock');
    registerForRestoration(_wallpaperUrlBoth, 'wallpaper_url_both');
    registerForRestoration(_liveWallpaper, 'live_wallpaper');
    registerForRestoration(_wallpaperChooser, 'wallpaper_chooser');
  }
}
