import 'package:async_wallpaper/async_wallpaper.dart';
import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/material.dart';
import 'package:flutter_cache_manager/flutter_cache_manager.dart';

const List<String> imageUrls = <String>[
  'https://images.unsplash.com/photo-1635593701810-3156162e184f',
  'https://images.unsplash.com/photo-1744132116976-0a68511b70f6',
  'https://images.unsplash.com/photo-1741018605802-e394cf20a435',
  'https://images.unsplash.com/photo-1743953273017-6a5e0c14ce40',
  'https://images.unsplash.com/photo-1695067439143-81a61a8c904a',
];

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return DynamicColorBuilder(
      builder: (ColorScheme? lightDynamic, ColorScheme? darkDynamic) {
        final ColorScheme scheme =
            lightDynamic ?? ColorScheme.fromSeed(seedColor: Colors.blue);
        return MaterialApp(
          title: 'Async Wallpaper Example',
          theme: ThemeData(colorScheme: scheme),
          home: const HomePage(),
        );
      },
    );
  }
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  int _selectedIndex = 0;
  bool _goHome = false;
  bool _rotationShuffle = false;
  bool _rotationIntervalTrigger = true;
  bool _rotationChargingTrigger = false;
  bool _rotationTimeOfDayTrigger = false;
  int _rotationIntervalMinutes = 60;
  int _activeHoursStart = 6;
  int _activeHoursEnd = 23;
  String _status = 'Idle';
  String? _activeAction;
  WallpaperRotationStatus? _rotationStatus;

  bool _isActionLoading(String action) => _activeAction == action;

  Future<void> _runAction(
    String action,
    String operation,
    Future<WallpaperResult> Function() body,
  ) async {
    if (_activeAction != null) {
      return;
    }

    setState(() {
      _activeAction = action;
      _status = '$operation in progress...';
    });

    try {
      final WallpaperResult result = await body();
      _setStatus(result, operation);
    } catch (error) {
      setState(() {
        _status = '$operation failed: $error';
      });
    } finally {
      if (mounted) {
        setState(() {
          _activeAction = null;
        });
      }
    }
  }

  Future<void> _setFromUrl() async {
    await _runAction('url', 'URL wallpaper', () {
      return AsyncWallpaper.setWallpaper(
        WallpaperRequest(
          target: WallpaperTarget.both,
          sourceType: WallpaperSourceType.url,
          source: imageUrls[_selectedIndex],
          goToHome: _goHome,
        ),
      );
    });
  }

  Future<void> _setFromFile() async {
    await _runAction('file', 'File wallpaper', () async {
      final file = await DefaultCacheManager().getSingleFile(
        imageUrls[_selectedIndex],
      );
      return AsyncWallpaper.setWallpaper(
        WallpaperRequest(
          target: WallpaperTarget.both,
          sourceType: WallpaperSourceType.file,
          source: file.path,
          goToHome: _goHome,
        ),
      );
    });
  }

  Future<void> _setLiveWallpaper() async {
    await _runAction('live', 'Live wallpaper', () async {
      const String liveUrl =
          'https://github.com/codenameakshay/sample-data/raw/main/video3.mp4';
      final file = await DefaultCacheManager().getSingleFile(liveUrl);
      return AsyncWallpaper.setLiveWallpaper(
        LiveWallpaperRequest(filePath: file.path, goToHome: _goHome),
      );
    });
  }

  Future<void> _openChooser() async {
    await _runAction('chooser', 'Wallpaper chooser', () {
      return AsyncWallpaper.openWallpaperChooser();
    });
  }

  Future<void> _startRotation() async {
    await _runAction('rotation-start', 'Start rotation', () {
      final Set<WallpaperRotationTrigger> triggers = <WallpaperRotationTrigger>{
        if (_rotationIntervalTrigger) WallpaperRotationTrigger.interval,
        if (_rotationChargingTrigger) WallpaperRotationTrigger.charging,
        if (_rotationTimeOfDayTrigger) WallpaperRotationTrigger.timeOfDay,
      };
      return AsyncWallpaper.startWallpaperRotation(
        WallpaperRotationRequest(
          sources: imageUrls
              .map(
                (String url) => WallpaperRotationSource(
                  sourceType: WallpaperSourceType.url,
                  source: url,
                ),
              )
              .toList(),
          target: WallpaperTarget.both,
          intervalMinutes: _rotationIntervalMinutes,
          order: _rotationShuffle
              ? WallpaperRotationOrder.shuffle
              : WallpaperRotationOrder.sequential,
          triggers: triggers,
          activeHoursStart: _activeHoursStart,
          activeHoursEnd: _activeHoursEnd,
        ),
      );
    });
    await _refreshRotationStatus();
  }

  Future<void> _stopRotation() async {
    await _runAction('rotation-stop', 'Stop rotation', () {
      return AsyncWallpaper.stopWallpaperRotation();
    });
    await _refreshRotationStatus();
  }

  Future<void> _rotateNow() async {
    await _runAction('rotation-now', 'Rotate now', () {
      return AsyncWallpaper.rotateWallpaperNow();
    });
    await _refreshRotationStatus();
  }

  Future<void> _refreshRotationStatus() async {
    try {
      final WallpaperRotationStatus status =
          await AsyncWallpaper.getWallpaperRotationStatus();
      if (!mounted) {
        return;
      }
      setState(() {
        _rotationStatus = status;
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _status = 'Status fetch failed: $error';
      });
    }
  }

  void _setStatus(WallpaperResult result, String operation) {
    setState(() {
      _status = result.isSuccess
          ? '$operation succeeded'
          : '$operation failed: ${result.error?.message ?? 'unknown'}';
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Async Wallpaper v3 Example')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: <Widget>[
          DropdownButton<int>(
            value: _selectedIndex,
            items: List<DropdownMenuItem<int>>.generate(
              imageUrls.length,
              (int i) => DropdownMenuItem<int>(
                value: i,
                child: Text('Sample image ${i + 1}'),
              ),
            ),
            onChanged: (int? value) {
              if (value != null) {
                setState(() => _selectedIndex = value);
              }
            },
          ),
          const SizedBox(height: 12),
          ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: AspectRatio(
              aspectRatio: 16 / 9,
              child: Image.network(
                imageUrls[_selectedIndex],
                fit: BoxFit.cover,
                loadingBuilder:
                    (
                      BuildContext context,
                      Widget child,
                      ImageChunkEvent? progress,
                    ) {
                      if (progress == null) {
                        return child;
                      }
                      return const Center(child: CircularProgressIndicator());
                    },
                errorBuilder:
                    (BuildContext context, Object error, StackTrace? trace) {
                      return const Center(child: Text('Preview unavailable'));
                    },
              ),
            ),
          ),
          const SizedBox(height: 8),
          SizedBox(
            height: 72,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: imageUrls.length,
              separatorBuilder: (BuildContext context, int index) =>
                  const SizedBox(width: 8),
              itemBuilder: (BuildContext context, int index) {
                final bool isSelected = index == _selectedIndex;
                return GestureDetector(
                  onTap: () => setState(() => _selectedIndex = index),
                  child: Container(
                    width: 96,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(
                        color: isSelected
                            ? Theme.of(context).colorScheme.primary
                            : Colors.transparent,
                        width: 2,
                      ),
                    ),
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(6),
                      child: Image.network(
                        imageUrls[index],
                        fit: BoxFit.cover,
                        loadingBuilder:
                            (
                              BuildContext context,
                              Widget child,
                              ImageChunkEvent? progress,
                            ) {
                              if (progress == null) {
                                return child;
                              }
                              return const Center(
                                child: SizedBox.square(
                                  dimension: 16,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                  ),
                                ),
                              );
                            },
                        errorBuilder:
                            (
                              BuildContext context,
                              Object error,
                              StackTrace? trace,
                            ) {
                              return const ColoredBox(
                                color: Color(0x22000000),
                                child: Center(
                                  child: Icon(Icons.broken_image_outlined),
                                ),
                              );
                            },
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
          SwitchListTile(
            title: const Text('Go to home after apply'),
            value: _goHome,
            onChanged: (bool value) => setState(() => _goHome = value),
          ),
          FilledButton(
            onPressed: _activeAction == null ? _setFromUrl : null,
            child: _isActionLoading('url')
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Set From URL'),
          ),
          const SizedBox(height: 8),
          FilledButton(
            onPressed: _activeAction == null ? _setFromFile : null,
            child: _isActionLoading('file')
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Set From File'),
          ),
          const SizedBox(height: 8),
          FilledButton(
            onPressed: _activeAction == null ? _setLiveWallpaper : null,
            child: _isActionLoading('live')
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Set Live Wallpaper'),
          ),
          const SizedBox(height: 8),
          OutlinedButton(
            onPressed: _activeAction == null ? _openChooser : null,
            child: _isActionLoading('chooser')
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Open Wallpaper Chooser'),
          ),
          const Divider(height: 24),
          Text('Rotation', style: Theme.of(context).textTheme.titleMedium),
          Row(
            children: <Widget>[
              const Text('Interval minutes:'),
              const SizedBox(width: 8),
              DropdownButton<int>(
                value: _rotationIntervalMinutes,
                items: const <int>[15, 30, 60, 120]
                    .map(
                      (int value) => DropdownMenuItem<int>(
                        value: value,
                        child: Text('$value'),
                      ),
                    )
                    .toList(),
                onChanged: (int? value) {
                  if (value == null) {
                    return;
                  }
                  setState(() => _rotationIntervalMinutes = value);
                },
              ),
            ],
          ),
          SwitchListTile(
            title: const Text('Interval trigger'),
            value: _rotationIntervalTrigger,
            onChanged: (bool value) {
              setState(() => _rotationIntervalTrigger = value);
            },
          ),
          SwitchListTile(
            title: const Text('Charging trigger'),
            value: _rotationChargingTrigger,
            onChanged: (bool value) {
              setState(() => _rotationChargingTrigger = value);
            },
          ),
          SwitchListTile(
            title: const Text('Time-of-day trigger'),
            value: _rotationTimeOfDayTrigger,
            onChanged: (bool value) {
              setState(() => _rotationTimeOfDayTrigger = value);
            },
          ),
          Row(
            children: <Widget>[
              const Text('Active hours:'),
              const SizedBox(width: 8),
              DropdownButton<int>(
                value: _activeHoursStart,
                items: List<int>.generate(24, (int i) => i)
                    .map(
                      (int value) => DropdownMenuItem<int>(
                        value: value,
                        child: Text('$value:00'),
                      ),
                    )
                    .toList(),
                onChanged: (int? value) {
                  if (value == null) return;
                  setState(() => _activeHoursStart = value);
                },
              ),
              const Text(' - '),
              DropdownButton<int>(
                value: _activeHoursEnd,
                items: List<int>.generate(24, (int i) => i)
                    .map(
                      (int value) => DropdownMenuItem<int>(
                        value: value,
                        child: Text('$value:00'),
                      ),
                    )
                    .toList(),
                onChanged: (int? value) {
                  if (value == null) return;
                  setState(() => _activeHoursEnd = value);
                },
              ),
            ],
          ),
          SwitchListTile(
            title: const Text('Shuffle order'),
            value: _rotationShuffle,
            onChanged: (bool value) => setState(() => _rotationShuffle = value),
          ),
          FilledButton(
            onPressed: _activeAction == null ? _startRotation : null,
            child: _isActionLoading('rotation-start')
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Start Rotation'),
          ),
          const SizedBox(height: 8),
          FilledButton(
            onPressed: _activeAction == null ? _rotateNow : null,
            child: _isActionLoading('rotation-now')
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Rotate Now'),
          ),
          const SizedBox(height: 8),
          OutlinedButton(
            onPressed: _activeAction == null ? _stopRotation : null,
            child: _isActionLoading('rotation-stop')
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text('Stop Rotation'),
          ),
          const SizedBox(height: 8),
          OutlinedButton(
            onPressed: _activeAction == null ? _refreshRotationStatus : null,
            child: const Text('Refresh Rotation Status'),
          ),
          const SizedBox(height: 8),
          Text(
            _rotationStatus == null
                ? 'Rotation status: unavailable'
                : 'Rotation status: running=${_rotationStatus!.isRunning}, '
                      'index=${_rotationStatus!.currentIndex}, '
                      'cached=${_rotationStatus!.cachedCount}/${_rotationStatus!.totalCount}, '
                      'next=${_rotationStatus!.nextRunEpochMs}, '
                      'error=${_rotationStatus!.lastError ?? '-'}',
          ),
          const SizedBox(height: 16),
          Text('Status: $_status'),
        ],
      ),
    );
  }
}
