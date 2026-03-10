import Flutter
import Foundation
import Photos
import UIKit

public class AsyncWallpaperPlugin: NSObject, FlutterPlugin, WallpaperApi {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let instance = AsyncWallpaperPlugin()
    WallpaperApiSetup.setUp(binaryMessenger: registrar.messenger(), api: instance)
  }

  public func getPlatformVersion(completion: @escaping (Result<String, Error>) -> Void) {
    completion(.success("iOS \(UIDevice.current.systemVersion)"))
  }

  public func checkMaterialYouSupport(
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

  public func setHomeWallpaperFromUrl(
    url: String,
    goToHome: Bool,
    completion: @escaping (Result<Bool, Error>) -> Void
  ) {
    completion(.success(false))
  }

  public func setLockWallpaperFromUrl(
    url: String,
    goToHome: Bool,
    completion: @escaping (Result<Bool, Error>) -> Void
  ) {
    completion(.success(false))
  }

  public func setBothWallpaperFromUrl(
    url: String,
    goToHome: Bool,
    completion: @escaping (Result<Bool, Error>) -> Void
  ) {
    completion(.success(false))
  }

  public func setWallpaper(
    url: String,
    goToHome: Bool,
    completion: @escaping (Result<Bool, Error>) -> Void
  ) {
    completion(.success(false))
  }

  public func setHomeWallpaperFromFile(
    filePath: String,
    goToHome: Bool,
    completion: @escaping (Result<Bool, Error>) -> Void
  ) {
    completion(.success(false))
  }

  public func setLockWallpaperFromFile(
    filePath: String,
    goToHome: Bool,
    completion: @escaping (Result<Bool, Error>) -> Void
  ) {
    completion(.success(false))
  }

  public func setBothWallpaperFromFile(
    filePath: String,
    goToHome: Bool,
    completion: @escaping (Result<Bool, Error>) -> Void
  ) {
    completion(.success(false))
  }

  public func setWallpaperFromFile(
    filePath: String,
    goToHome: Bool,
    completion: @escaping (Result<Bool, Error>) -> Void
  ) {
    completion(.success(false))
  }

  public func setMaterialYouWallpaper(
    url: String,
    goToHome: Bool,
    enableEffects: Bool,
    completion: @escaping (Result<Bool, Error>) -> Void
  ) {
    completion(.success(false))
  }

  public func setLiveWallpaper(
    filePath: String,
    goToHome: Bool,
    completion: @escaping (Result<Bool, Error>) -> Void
  ) {
    completion(.success(false))
  }

  public func openWallpaperChooser(completion: @escaping (Result<Bool, Error>) -> Void) {
    completion(.success(false))
  }

  public func downloadWallpaper(url: String, completion: @escaping (Result<Bool, Error>) -> Void) {
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
}
