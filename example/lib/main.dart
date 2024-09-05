import 'dart:async';

import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_cache_manager/flutter_cache_manager.dart';

const _brandBlue = Color(0xFF1E88E5);

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    // Use DynamicColorBuilder to support Material You theming
    return DynamicColorBuilder(
      builder: (ColorScheme? lightDynamic, ColorScheme? darkDynamic) {
        ColorScheme lightColorScheme;
        ColorScheme darkColorScheme;

        // Use dynamic color scheme if available, otherwise fall back to a generated scheme
        if (lightDynamic != null && darkDynamic != null) {
          lightColorScheme = lightDynamic.harmonized();
          darkColorScheme = darkDynamic.harmonized();
        } else {
          lightColorScheme = ColorScheme.fromSeed(seedColor: _brandBlue);
          darkColorScheme = ColorScheme.fromSeed(
            seedColor: _brandBlue,
            brightness: Brightness.dark,
          );
        }

        // Configure the MaterialApp with themes and home page
        return MaterialApp(
          restorationScopeId: 'async_wallpaper_app',
          theme: ThemeData(colorScheme: lightColorScheme),
          darkTheme: ThemeData(colorScheme: darkColorScheme),
          home: const HomePage(),
        );
      },
    );
  }
}

// Main page of the application
class HomePage extends StatefulWidget {
  const HomePage({Key? key}) : super(key: key);

  @override
  State<HomePage> createState() => HomePageState();
}

class HomePageState extends State<HomePage> with RestorationMixin {
  // Restorable state variables for various wallpaper operations
  final RestorableString platformVersion = RestorableString('Unknown');
  final RestorableString wallpaperFileNative = RestorableString('Unknown');
  final RestorableString wallpaperFileHome = RestorableString('Unknown');
  final RestorableString wallpaperFileLock = RestorableString('Unknown');
  final RestorableString wallpaperFileBoth = RestorableString('Unknown');
  final RestorableString wallpaperUrlNative = RestorableString('Unknown');
  final RestorableString wallpaperUrlHome = RestorableString('Unknown');
  final RestorableString wallpaperUrlLock = RestorableString('Unknown');
  final RestorableString wallpaperUrlBoth = RestorableString('Unknown');
  final RestorableString liveWallpaper = RestorableString('Unknown');
  final RestorableString wallpaperChooser = RestorableString('Unknown');

  // URLs for static and live wallpapers
  String url = 'https://images.unsplash.com/photo-1635593701810-3156162e184f';
  String liveUrl =
      'https://github.com/codenameakshay/sample-data/raw/main/video3.mp4';

  late bool goToHome;
  String? loadingOption;

  @override
  void initState() {
    super.initState();
    goToHome = false;
    initPlatformState();
  }

  // Initialize platform state
  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String _platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    // We also handle the message potentially returning null.
    try {
      _platformVersion = await AsyncWallpaper.instance.platformVersion ??
          'Unknown platform version';
    } on PlatformException {
      _platformVersion = 'Failed to get platform version.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      platformVersion.value = _platformVersion;
    });
  }

  // Generic method to set wallpaper and update UI
  Future<void> setWallpaper(Function wallpaperSetter, RestorableString status,
      String optionLabel) async {
    setState(() {
      loadingOption = optionLabel;
      status.value = 'Loading';
    });

    String result;
    try {
      result = await wallpaperSetter()
          ? 'Wallpaper set'
          : 'Failed to set wallpaper.';
    } on PlatformException {
      result = 'Failed to set wallpaper.';
    }

    if (!mounted) return;

    setState(() {
      loadingOption = null;
      status.value = result;
    });
  }

  @override
  Widget build(BuildContext context) {
    // Define wallpaper options with their respective functions
    final List<Map<String, dynamic>> wallpaperOptions = [
      {
        'label': 'File Native',
        'subtitle': 'Set wallpaper using a file on native screen',
        'onTap': () => setWallpaper(() async {
              var file = await DefaultCacheManager().getSingleFile(url);
              return AsyncWallpaper.instance.setWallpaperFromFileNative(
                filePath: file.path,
                goToHome: goToHome,
                toastDetails: ToastDetails.success(),
                errorToastDetails: ToastDetails.error(),
              );
            }, wallpaperFileNative, 'File Native'),
        'status': wallpaperFileNative
      },
      {
        'label': 'File Home',
        'subtitle': 'Set wallpaper using a file on home screen',
        'onTap': () => setWallpaper(() async {
              var file = await DefaultCacheManager().getSingleFile(url);
              return AsyncWallpaper.instance.setWallpaperFromFile(
                filePath: file.path,
                wallpaperLocation: AsyncWallpaper.HOME_SCREEN,
                goToHome: goToHome,
                toastDetails: ToastDetails.success(),
                errorToastDetails: ToastDetails.error(),
              );
            }, wallpaperFileHome, 'File Home'),
        'status': wallpaperFileHome
      },
      {
        'label': 'File Lock',
        'subtitle': 'Set wallpaper using a file on lock screen',
        'onTap': () => setWallpaper(() async {
              var file = await DefaultCacheManager().getSingleFile(url);
              return AsyncWallpaper.instance.setWallpaperFromFile(
                filePath: file.path,
                wallpaperLocation: AsyncWallpaper.LOCK_SCREEN,
                goToHome: goToHome,
                toastDetails: ToastDetails.success(),
                errorToastDetails: ToastDetails.error(),
              );
            }, wallpaperFileLock, 'File Lock'),
        'status': wallpaperFileLock
      },
      {
        'label': 'File Both',
        'subtitle': 'Set wallpaper using a file on both screens',
        'onTap': () => setWallpaper(() async {
              var file = await DefaultCacheManager().getSingleFile(url);
              return AsyncWallpaper.instance.setWallpaperFromFile(
                filePath: file.path,
                wallpaperLocation: AsyncWallpaper.BOTH_SCREENS,
                goToHome: goToHome,
                toastDetails: ToastDetails.success(),
                errorToastDetails: ToastDetails.error(),
              );
            }, wallpaperFileBoth, 'File Both'),
        'status': wallpaperFileBoth
      },
      {
        'label': 'URL Native',
        'subtitle': 'Set wallpaper using URL on native screen',
        'onTap': () => setWallpaper(() {
              return AsyncWallpaper.instance.setWallpaperNative(
                url: url,
                goToHome: goToHome,
                toastDetails: ToastDetails.success(),
                errorToastDetails: ToastDetails.error(),
              );
            }, wallpaperUrlNative, 'URL Native'),
        'status': wallpaperUrlNative
      },
      {
        'label': 'URL Home',
        'subtitle': 'Set wallpaper using URL on home screen',
        'onTap': () => setWallpaper(() {
              return AsyncWallpaper.instance.setWallpaper(
                url: url,
                wallpaperLocation: AsyncWallpaper.HOME_SCREEN,
                goToHome: goToHome,
                toastDetails: ToastDetails.success(),
                errorToastDetails: ToastDetails.error(),
              );
            }, wallpaperUrlHome, 'URL Home'),
        'status': wallpaperUrlHome
      },
      {
        'label': 'URL Lock',
        'subtitle': 'Set wallpaper using URL on lock screen',
        'onTap': () => setWallpaper(() {
              return AsyncWallpaper.instance.setWallpaper(
                url: url,
                wallpaperLocation: AsyncWallpaper.LOCK_SCREEN,
                goToHome: goToHome,
                toastDetails: ToastDetails.success(),
                errorToastDetails: ToastDetails.error(),
              );
            }, wallpaperUrlLock, 'URL Lock'),
        'status': wallpaperUrlLock
      },
      {
        'label': 'URL Both',
        'subtitle': 'Set wallpaper using URL on both screens',
        'onTap': () => setWallpaper(() {
              return AsyncWallpaper.instance.setWallpaper(
                url: url,
                wallpaperLocation: AsyncWallpaper.BOTH_SCREENS,
                goToHome: goToHome,
                toastDetails: ToastDetails.success(),
                errorToastDetails: ToastDetails.error(),
              );
            }, wallpaperUrlBoth, 'URL Both'),
        'status': wallpaperUrlBoth
      },
      {
        'label': 'Live Wallpaper',
        'subtitle': 'Set live wallpaper',
        'onTap': () => setWallpaper(() async {
              var file = await DefaultCacheManager().getSingleFile(liveUrl);
              return AsyncWallpaper.instance.setLiveWallpaper(
                filePath: file.path,
                goToHome: goToHome,
                toastDetails: ToastDetails.success(),
                errorToastDetails: ToastDetails.error(),
              );
            }, liveWallpaper, 'Live Wallpaper'),
        'status': liveWallpaper
      },
      {
        'label': 'Wallpaper Chooser',
        'subtitle': 'Open wallpaper chooser',
        'onTap': () => setWallpaper(() {
              return AsyncWallpaper.instance.openWallpaperChooser(
                goToHome: goToHome,
                toastDetails: ToastDetails.wallpaperChooser(),
                errorToastDetails: ToastDetails.error(),
              );
            }, wallpaperChooser, 'Wallpaper Chooser'),
        'status': wallpaperChooser
      },
    ];

    return Scaffold(
      appBar: AppBar(
        title: const Text('Dynamic Wallpaper App'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(8.0),
        child: Column(
          children: [
            // Switch to toggle 'Go to home' option
            SwitchListTile(
              title: const Text('Go to home'),
              value: goToHome,
              onChanged: (value) {
                setState(() {
                  goToHome = value;
                });
              },
            ),
            // Grid of wallpaper options
            Expanded(
              child: GridView.count(
                crossAxisCount: 2,
                mainAxisSpacing: 8.0,
                crossAxisSpacing: 8.0,
                childAspectRatio: 1.0, // Make buttons square
                children: wallpaperOptions.map((option) {
                  return ElevatedButton(
                    onPressed: loadingOption == null ? option['onTap'] : null,
                    style: ElevatedButton.styleFrom(
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12.0),
                      ),
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                    ),
                    child: loadingOption == option['label']
                        ? const Center(child: CircularProgressIndicator())
                        : Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Text(option['label'],
                                  style: const TextStyle(
                                      fontWeight: FontWeight.bold)),
                              const SizedBox(height: 4.0),
                              Text(
                                option['subtitle'],
                                textAlign: TextAlign.center,
                                style: const TextStyle(fontSize: 12),
                              ),
                              const SizedBox(height: 8.0),
                              Text(
                                option['status'].value,
                                textAlign: TextAlign.center,
                                style: TextStyle(
                                  fontSize: 12,
                                  color:
                                      Theme.of(context).colorScheme.secondary,
                                ),
                              ),
                            ],
                          ),
                  );
                }).toList(),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // Restoration
  @override
  String? get restorationId => 'HomePageState';

  @override
  void restoreState(RestorationBucket? oldBucket, bool initialRestore) {
    registerForRestoration(platformVersion, 'platform_version');
    registerForRestoration(wallpaperFileNative, 'wallpaper_file_native');
    registerForRestoration(wallpaperFileHome, 'wallpaper_file_home');
    registerForRestoration(wallpaperFileLock, 'wallpaper_file_lock');
    registerForRestoration(wallpaperFileBoth, 'wallpaper_file_both');
    registerForRestoration(wallpaperUrlNative, 'wallpaper_url_native');
    registerForRestoration(wallpaperUrlHome, 'wallpaper_url_home');
    registerForRestoration(wallpaperUrlLock, 'wallpaper_url_lock');
    registerForRestoration(wallpaperUrlBoth, 'wallpaper_url_both');
    registerForRestoration(liveWallpaper, 'live_wallpaper');
    registerForRestoration(wallpaperChooser, 'wallpaper_chooser');
  }
}
