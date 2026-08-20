import Flutter
import Foundation
import Photos
import UIKit

public class AsyncWallpaperPlugin: NSObject, FlutterPlugin, WallpaperApi {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let instance = AsyncWallpaperPlugin()
    WallpaperApiSetup.setUp(binaryMessenger: registrar.messenger(), api: instance)
  }

  func getPlatformVersion(completion: @escaping (Result<String, Error>) -> Void) {
    completion(.success("iOS \(UIDevice.current.systemVersion)"))
  }

  func checkMaterialYouSupport(
    completion: @escaping (Result<MaterialYouSupportData, Error>) -> Void
  ) {
    completion(
      .success(
        MaterialYouSupportData(
          isSupported: false,
          androidVersion: "Unsupported on iOS",
          sdkInt: 0
        )
      )
    )
  }

  func getCapabilities(
    completion: @escaping (Result<WallpaperCapabilitiesData, Error>) -> Void
  ) {
    completion(
      .success(
        WallpaperCapabilitiesData(
          supportsStaticWallpaper: false,
          supportsLiveWallpaper: false,
          supportsOpenGlLiveWallpaper: false,
          supportsHomeWallpaper: false,
          supportsLockWallpaper: false,
          supportsBothWallpapers: false,
          canSetWallpaper: false,
          hasSystemWallpaperPicker: false,
          requiresForeground: false,
          manufacturer: "Apple",
          sdkInt: 0
        )
      )
    )
  }

  func applyWallpaper(
    request: StaticWallpaperRequestData,
    completion: @escaping (Result<OperationResultData, Error>) -> Void
  ) {
    completion(.success(unsupportedResult(target: request.target, operation: "static wallpaper")))
  }

  func prepareVideoWallpaper(
    request: VideoWallpaperRequestData,
    completion: @escaping (Result<OperationResultData, Error>) -> Void
  ) {
    completion(.success(unsupportedResult(target: request.target, operation: "video wallpaper")))
  }

  func openLiveWallpaperPreview(
    request: VideoWallpaperRequestData,
    completion: @escaping (Result<OperationResultData, Error>) -> Void
  ) {
    completion(.success(unsupportedResult(target: request.target, operation: "live wallpaper preview")))
  }

  func applyOpenGlWallpaper(
    request: OpenGlWallpaperRequestData,
    completion: @escaping (Result<OperationResultData, Error>) -> Void
  ) {
    completion(.success(unsupportedResult(target: request.target, operation: "OpenGL wallpaper")))
  }

  func setHomeWallpaperFromUrl(
    url: String,
    goToHome: Bool,
    completion: @escaping (Result<Bool, Error>) -> Void
  ) {
    completion(.success(false))
  }

  func setLockWallpaperFromUrl(
    url: String,
    goToHome: Bool,
    completion: @escaping (Result<Bool, Error>) -> Void
  ) {
    completion(.success(false))
  }

  func setBothWallpaperFromUrl(
    url: String,
    goToHome: Bool,
    completion: @escaping (Result<Bool, Error>) -> Void
  ) {
    completion(.success(false))
  }

  func setWallpaper(
    url: String,
    goToHome: Bool,
    completion: @escaping (Result<Bool, Error>) -> Void
  ) {
    completion(.success(false))
  }

  func setHomeWallpaperFromFile(
    filePath: String,
    goToHome: Bool,
    completion: @escaping (Result<Bool, Error>) -> Void
  ) {
    completion(.success(false))
  }

  func setLockWallpaperFromFile(
    filePath: String,
    goToHome: Bool,
    completion: @escaping (Result<Bool, Error>) -> Void
  ) {
    completion(.success(false))
  }

  func setBothWallpaperFromFile(
    filePath: String,
    goToHome: Bool,
    completion: @escaping (Result<Bool, Error>) -> Void
  ) {
    completion(.success(false))
  }

  func setWallpaperFromFile(
    filePath: String,
    goToHome: Bool,
    completion: @escaping (Result<Bool, Error>) -> Void
  ) {
    completion(.success(false))
  }

  func setMaterialYouWallpaper(
    url: String,
    goToHome: Bool,
    enableEffects: Bool,
    completion: @escaping (Result<Bool, Error>) -> Void
  ) {
    completion(.success(false))
  }

  func setLiveWallpaper(
    filePath: String,
    goToHome: Bool,
    completion: @escaping (Result<Bool, Error>) -> Void
  ) {
    completion(.success(false))
  }

  func openWallpaperChooser(completion: @escaping (Result<Bool, Error>) -> Void) {
    completion(.success(false))
  }

  func downloadWallpaper(url: String, completion: @escaping (Result<Bool, Error>) -> Void) {
    guard let remoteUrl = URL(string: url) else {
      completion(.success(false))
      return
    }

    requestPhotoLibraryPermission { granted in
      guard granted else {
        completion(.success(false))
        return
      }

      URLSession.shared.dataTask(with: remoteUrl) { data, _, error in
        guard error == nil, let data else {
          completion(.success(false))
          return
        }

        PHPhotoLibrary.shared().performChanges({
          let creationRequest = PHAssetCreationRequest.forAsset()
          creationRequest.addResource(with: .photo, data: data, options: nil)
        }) { saved, _ in
          completion(.success(saved))
        }
      }.resume()
    }
  }

  func startWallpaperRotation(
    config: WallpaperRotationConfigData,
    completion: @escaping (Result<Bool, Error>) -> Void
  ) {
    completion(.success(false))
  }

  func stopWallpaperRotation(completion: @escaping (Result<Bool, Error>) -> Void) {
    completion(.success(false))
  }

  func getWallpaperRotationStatus(
    completion: @escaping (Result<WallpaperRotationStatusData, Error>) -> Void
  ) {
    completion(
      .success(
        WallpaperRotationStatusData(
          isRunning: false,
          nextRunEpochMs: 0,
          currentIndex: 0,
          cachedCount: 0,
          totalCount: 0,
          lastError: nil,
          effectiveIntervalMinutes: 0
        )
      )
    )
  }

  func rotateWallpaperNow(completion: @escaping (Result<Bool, Error>) -> Void) {
    completion(.success(false))
  }

  private func requestPhotoLibraryPermission(_ completion: @escaping (Bool) -> Void) {
    if #available(iOS 14, *) {
      let status = PHPhotoLibrary.authorizationStatus(for: .addOnly)
      switch status {
      case .authorized, .limited:
        completion(true)
      case .notDetermined:
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { newStatus in
          completion(newStatus == .authorized || newStatus == .limited)
        }
      default:
        completion(false)
      }
    } else {
      let status = PHPhotoLibrary.authorizationStatus()
      switch status {
      case .authorized:
        completion(true)
      case .notDetermined:
        PHPhotoLibrary.requestAuthorization { newStatus in
          completion(newStatus == .authorized)
        }
      default:
        completion(false)
      }
    }
  }

  private func unsupportedResult(
    target: WallpaperTargetData?,
    operation: String
  ) -> OperationResultData {
    OperationResultData(
      status: .unsupported,
      requestedTarget: target ?? .home,
      errorCode: "not-implemented",
      errorMessage: "The structured \(operation) endpoint is not available on iOS."
    )
  }
}
