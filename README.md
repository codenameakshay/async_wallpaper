<!-- 
This README describes the package. If you publish this package to pub.dev,
this README's contents appear on the landing page for your package.

For information about how to write a good package README, see the guide for
[writing package pages](https://dart.dev/guides/libraries/writing-package-pages). 

For general information about developing packages, see the Dart guide for
[creating packages](https://dart.dev/guides/libraries/create-library-packages)
and the Flutter guide for
[developing packages and plugins](https://flutter.dev/developing-packages). 
-->

<h1 align="center">Async Wallpaper</h1>

<p align="center">A flutter package which contains a collection of some functions to set wallpaper on your Android device asynchronously. With this plugin you can also set video live wallpapers (.mp4) natively.</p><br>

<p align="center">
  <a href="https://flutter.dev">
    <img src="https://img.shields.io/badge/Platform-Flutter-02569B?logo=flutter"
      alt="Platform" />
  </a>
  <a href="https://pub.dartlang.org/packages/async_wallpaper">
    <img src="https://img.shields.io/pub/v/async_wallpaper.svg"
      alt="Pub Package" />
  </a>
  <a href="https://opensource.org/licenses/MIT">
    <img src="https://img.shields.io/github/license/aagarwal1012/animated-text-kit?color=red"
      alt="License: MIT" />
  </a>
  <a href="https://www.paypal.me/codenameakshay">
    <img src="https://img.shields.io/badge/Donate-PayPal-00457C?logo=paypal"
      alt="Donate" />
  </a>
</p><br>

| ![Example](https://raw.githubusercontent.com/codenameakshay/async_wallpaper/main/screenshots/demo.gif)  | ![Example App](https://raw.githubusercontent.com/codenameakshay/async_wallpaper/main/screenshots/image.jpg)  |
|---|---|
|  **Example in another app** |  **Example app screenshot** |

## Features

The package allows you to set wallpaper on your Android device asynchronously, in the following ways.

- Set wallpaper from a file path
- Set wallpaper from a URL
- Set wallpaper from a video file (file path)
- Can select locations (HOME, LOCK, BOTH)
- Open native wallpaper chooser
- Minimise your app and go to Android home screen

## Installing

### 1. Depend on it

Add this to your package's `pubspec.yaml` file:

```yaml
dependencies:
  async_wallpaper: ^2.1.0
```

### 2. Install it

You can install packages from the command line:

with `pub`:

```bash
pub get
```

with `Flutter`:

```bash
flutter pub get
```

### 3. Import it

Now in your `Dart` code, you can use:

```dart
import 'package:async_wallpaper/async_wallpaper.dart';
```

# Usage

`AsyncWallpaper` is a _Class_ that exposes some methods, variables which you can call to set wallpapers.
When you want to set wallpaper simply use it like:

```dart
await AsyncWallpaper.setWallpaperFromFile(
    filePath: file.path,
    wallpaperLocation: AsyncWallpaper.HOME_SCREEN,
    goToHome: goToHome,
    toastDetails: ToastDetails.success(),
    errorToastDetails: ToastDetails.error(),
);
```

It needs three arguments -

- `filePath` – the path of the file to set as wallpaper
- `wallpaperLocation` – the location where you want to set the wallpaper
- `goToHome` – a bool, which redirects your app to home screen when wallpaper is set (optional)
- `toastDetails:` - a `ToastDetails` object, which contains the details of the toast to be shown when wallpaper is set (optional)
- `errorToastDetails:` - a `ToastDetails` object, which contains the details of the toast to be shown when wallpaper is not set or error (optional)

AsyncWallpaper has three locations predefined -

- `HOME_SCREEN` – set wallpaper on home screen
- `LOCK_SCREEN` – set wallpaper on lock screen
- `BOTH_SCREENS` – set wallpaper on both home and lock screen

# Detailed Usage

Below is the detailed usage of the package, including all functions defined.

## platformVersion

```dart
await AsyncWallpaper.platformVersion ?? 'Unknown platform version';
```

It returns the platform version of the device. Useful for all the Android 11, 12 permission related stuff.

## setWallpaperFromFile

Setting wallpaper from a file path, on home screen.

```dart
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

```

Setting wallpaper from a file path, on lock screen.

```dart
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

```

Setting wallpaper from a file path, on both screens.

```dart
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

```

> Note - You can use the `flutter_cache_manager` plugin to download the file from the internet, and get the file path.

## setWallpaper

Setting wallpaper from a url, on home screen.

```dart
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

```

Setting wallpaper from a url, on lock screen.

```dart
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

```

Setting wallpaper from a url, on both screens.

```dart
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

```

## setLiveWallpaper

Setting live wallpaper requires `.mp4` file. Also currently local files are only supported, so download it before calling this function. The method call redirect to native Android live wallpaper setting intent, so no locations are currently supported.

```dart
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

```

## openWallpaperChooser

Opens Android native wallpaper chooser.

```dart
String result;
// Platform messages may fail, so we use a try/catch PlatformException.
try {
    result = await AsyncWallpaper.openWallpaperChooser(
    filePath: file.path,
    goToHome: goToHome,
    toastDetails: ToastDetails.success(),
    errorToastDetails: ToastDetails.error(),
    )
        ? 'Opened wallpaper chooser'
        : 'Failed to open wallpaper chooser.';
} on PlatformException {
    result = 'Failed to open wallpaper chooser.';
}

```

> Note - You can find more detailed examples in the `example` directory.

# Bugs or Requests

If you encounter any problems feel free to open an [issue](https://github.com/codenameakshay/async_wallpaper/issues/new?template=bug_report.md). If you feel the library is missing a feature, please raise a [ticket](https://github.com/codenameakshay/async_wallpaper/issues/new?template=feature_request.md) on GitHub and I'll look into it. Pull request are also welcome.

See [Contributing.md](https://github.com/codenameakshay/async_wallpaper/blob/master/CONTRIBUTING.md).
