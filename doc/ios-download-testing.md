# iOS download transport tests

Run the Foundation-only transport tests on Linux or macOS with:

```sh
sh tool/ios_download_tests/run.sh
```

The tests cover HTTPS redirect policy, HTTP status handling, streaming size
limits, empty bodies, temporary-file cleanup, and exactly-once completion. The
Photos integration also checks that ImageIO can create a thumbnail capped at
2048 pixels before asking Photos to save the file. That UIKit/ImageIO/Photos
path needs an iOS build or device test; it is not exercised by the portable
transport harness.
