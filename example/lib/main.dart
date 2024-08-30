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
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> with RestorationMixin {
  // Restorable state variables for various wallpaper operations
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

  // URLs for static and live wallpapers
  String url = 'https://images.unsplash.com/photo-1635593701810-3156162e184f';
  String liveUrl = 'https://github.com/codenameakshay/sample-data/raw/main/video3.mp4';

  late bool goToHome;
  String? _loadingOption;

  @override
  void initState() {
    super.initState();
    goToHome = false;
    initPlatformState();
  }

  // Initialize platform state
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

  // Generic method to set wallpaper and update UI
  Future<void> setWallpaper(Function wallpaperSetter, RestorableString status, String optionLabel) async {
    setState(() {
      _loadingOption = optionLabel;
      status.value = 'Loading';
    });

    String result;
    try {
      result = await wallpaperSetter() ? 'Wallpaper set' : 'Failed to set wallpaper.';
    } on PlatformException {
      result = 'Failed to set wallpaper.';
    }

    if (!mounted) return;

    setState(() {
      _loadingOption = null;
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
              return AsyncWallpaper.setWallpaperFromFileNative(
                filePath: file.path,
                goToHome: goToHome,
                toastDetails: ToastDetails.success(),
                errorToastDetails: ToastDetails.error(),
              );
            }, _wallpaperFileNative, 'File Native'),
        'status': _wallpaperFileNative
      },
      {
        'label': 'File Home',
        'subtitle': 'Set wallpaper using a file on home screen',
        'onTap': () => setWallpaper(() async {
              var file = await DefaultCacheManager().getSingleFile(url);
              return AsyncWallpaper.setWallpaperFromFile(
                filePath: file.path,
                wallpaperLocation: AsyncWallpaper.HOME_SCREEN,
                goToHome: goToHome,
                toastDetails: ToastDetails.success(),
                errorToastDetails: ToastDetails.error(),
              );
            }, _wallpaperFileHome, 'File Home'),
        'status': _wallpaperFileHome
      },
      {
        'label': 'File Lock',
        'subtitle': 'Set wallpaper using a file on lock screen',
        'onTap': () => setWallpaper(() async {
              var file = await DefaultCacheManager().getSingleFile(url);
              return AsyncWallpaper.setWallpaperFromFile(
                filePath: file.path,
                wallpaperLocation: AsyncWallpaper.LOCK_SCREEN,
                goToHome: goToHome,
                toastDetails: ToastDetails.success(),
                errorToastDetails: ToastDetails.error(),
              );
            }, _wallpaperFileLock, 'File Lock'),
        'status': _wallpaperFileLock
      },
      {
        'label': 'File Both',
        'subtitle': 'Set wallpaper using a file on both screens',
        'onTap': () => setWallpaper(() async {
              var file = await DefaultCacheManager().getSingleFile(url);
              return AsyncWallpaper.setWallpaperFromFile(
                filePath: file.path,
                wallpaperLocation: AsyncWallpaper.BOTH_SCREENS,
                goToHome: goToHome,
                toastDetails: ToastDetails.success(),
                errorToastDetails: ToastDetails.error(),
              );
            }, _wallpaperFileBoth, 'File Both'),
        'status': _wallpaperFileBoth
      },
      {
        'label': 'URL Native',
        'subtitle': 'Set wallpaper using URL on native screen',
        'onTap': () => setWallpaper(() {
              return AsyncWallpaper.setWallpaperNative(
                url: url,
                goToHome: goToHome,
                toastDetails: ToastDetails.success(),
                errorToastDetails: ToastDetails.error(),
              );
            }, _wallpaperUrlNative, 'URL Native'),
        'status': _wallpaperUrlNative
      },
      {
        'label': 'URL Home',
        'subtitle': 'Set wallpaper using URL on home screen',
        'onTap': () => setWallpaper(() {
              return AsyncWallpaper.setWallpaper(
                url: url,
                wallpaperLocation: AsyncWallpaper.HOME_SCREEN,
                goToHome: goToHome,
                toastDetails: ToastDetails.success(),
                errorToastDetails: ToastDetails.error(),
              );
            }, _wallpaperUrlHome, 'URL Home'),
        'status': _wallpaperUrlHome
      },
      {
        'label': 'URL Lock',
        'subtitle': 'Set wallpaper using URL on lock screen',
        'onTap': () => setWallpaper(() {
              return AsyncWallpaper.setWallpaper(
                url: url,
                wallpaperLocation: AsyncWallpaper.LOCK_SCREEN,
                goToHome: goToHome,
                toastDetails: ToastDetails.success(),
                errorToastDetails: ToastDetails.error(),
              );
            }, _wallpaperUrlLock, 'URL Lock'),
        'status': _wallpaperUrlLock
      },
      {
        'label': 'URL Both',
        'subtitle': 'Set wallpaper using URL on both screens',
        'onTap': () => setWallpaper(() {
              return AsyncWallpaper.setWallpaper(
                url: url,
                wallpaperLocation: AsyncWallpaper.BOTH_SCREENS,
                goToHome: goToHome,
                toastDetails: ToastDetails.success(),
                errorToastDetails: ToastDetails.error(),
              );
            }, _wallpaperUrlBoth, 'URL Both'),
        'status': _wallpaperUrlBoth
      },
      {
        'label': 'Live Wallpaper',
        'subtitle': 'Set live wallpaper',
        'onTap': () => setWallpaper(() async {
              var file = await DefaultCacheManager().getSingleFile(liveUrl);
              return AsyncWallpaper.setLiveWallpaper(
                filePath: file.path,
                goToHome: goToHome,
                toastDetails: ToastDetails.success(),
                errorToastDetails: ToastDetails.error(),
              );
            }, _liveWallpaper, 'Live Wallpaper'),
        'status': _liveWallpaper
      },
      {
        'label': 'Wallpaper Chooser',
        'subtitle': 'Open wallpaper chooser',
        'onTap': () => setWallpaper(() {
              return AsyncWallpaper.openWallpaperChooser(
                goToHome: goToHome,
                toastDetails: ToastDetails.wallpaperChooser(),
                errorToastDetails: ToastDetails.error(),
              );
            }, _wallpaperChooser, 'Wallpaper Chooser'),
        'status': _wallpaperChooser
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
                    onPressed: _loadingOption == null ? option['onTap'] : null,
                    style: ElevatedButton.styleFrom(
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12.0),
                      ),
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                    ),
                    child: _loadingOption == option['label']
                        ? const Center(child: CircularProgressIndicator())
                        : Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Text(option['label'], style: const TextStyle(fontWeight: FontWeight.bold)),
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
                                  color: Theme.of(context).colorScheme.secondary,
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
  String? get restorationId => '_HomePageState';

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
