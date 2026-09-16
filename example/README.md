# async_wallpaper example

A small app that calls every public `async_wallpaper` API. It has no extra dependencies: each source is a text field you can replace with a value from your own app.

<img src="https://raw.githubusercontent.com/codenameakshay/async_wallpaper/main/screenshots/android-static.png" width="220"> <img src="https://raw.githubusercontent.com/codenameakshay/async_wallpaper/main/screenshots/android-capabilities.png" width="220"> <img src="https://raw.githubusercontent.com/codenameakshay/async_wallpaper/main/screenshots/android-more-apis.png" width="220">

## Run

```sh
cd example
flutter pub get
flutter run
```

## What to try

1. **Static wallpaper.** Pick a source (URL, file path, content URI, or embedded bytes), a target, a scale mode, and a strategy. Tap **Apply static wallpaper** and read the separate **Home** and **Lock** results.
2. **Capabilities.** Tap **Refresh capabilities** to see what the device supports.
3. **Video live wallpaper.** Tap **Prepare video**, then **Open live preview**. The preview result means the system UI opened, not that the user applied the wallpaper.
4. **OpenGL live wallpaper.** Opens a built-in color-pulse shader at 30 FPS.
5. **More APIs.** Download to the gallery, open the wallpaper chooser, the legacy setter, Material You, and the platform version.
6. **Rotation.** Start, rotate now, read the status, and stop. It rotates the URL and file path every 15 minutes.

The app runs one operation at a time and has no "go home" option. Your app decides what to do after a result.

## Notes

- Use the `direct` strategy from background work. The cropper, picker, and live previews need a foreground Activity.
- Android's live-wallpaper preview picks the final home/lock target. The plugin cannot force it.
- On Android 12+, a new wallpaper can relaunch the Activity. The example keeps one `FlutterEngine` so it can show the result. See [Activity relaunch after a wallpaper change](../doc/android-compatibility.md#activity-relaunch-after-a-wallpaper-change).
- iOS supports only **Download to gallery**. Other actions return `unsupported`.

## Tests

```sh
cd example
flutter test
```

The widget tests use a fake API, so they do not call the platform.
