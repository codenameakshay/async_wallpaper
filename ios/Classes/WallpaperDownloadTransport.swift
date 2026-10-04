import Foundation

#if canImport(FoundationNetworking)
  import FoundationNetworking
#endif

enum WallpaperDownloadTransportError: Error, Equatable {
  case invalidUrl
  case insecureRedirect
  case missingHttpResponse
  case httpStatus(Int)
  case tooLarge
  case emptyResponse
  case temporaryFileFailure
  case transportFailure
}

enum WallpaperDownloadFileWriter {
  static func writeAll(
    _ data: Data,
    write: (UnsafePointer<UInt8>, Int) throws -> Int
  ) throws {
    try data.withUnsafeBytes { buffer in
      guard let baseAddress = buffer.bindMemory(to: UInt8.self).baseAddress else { return }
      var written = 0
      while written < buffer.count {
        let remaining = buffer.count - written
        let count = try write(baseAddress.advanced(by: written), remaining)
        guard count > 0, count <= remaining else {
          throw WallpaperDownloadTransportError.temporaryFileFailure
        }
        written += count
      }
    }
  }
}

private func isSecureHttpsUrl(_ url: URL?) -> Bool {
  guard let url,
    let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
  else {
    return false
  }
  return components.scheme?.lowercased() == "https"
    && components.host?.isEmpty == false
    && components.user == nil
    && components.password == nil
}

/// Streams an HTTPS response to a bounded temporary file for image validation and Photos.
final class WallpaperDownloadTransport {
  static let maximumDownloadBytes = 64 * 1024 * 1024

  static func isAllowedRedirect(to url: URL?) -> Bool {
    isSecureHttpsUrl(url)
  }

  private let maximumBytes: Int
  private let configuration: URLSessionConfiguration

  init(
    maximumBytes: Int = WallpaperDownloadTransport.maximumDownloadBytes,
    configuration: URLSessionConfiguration = .ephemeral
  ) {
    self.maximumBytes = max(0, maximumBytes)
    self.configuration = configuration
  }

  func download(from url: URL, completion: @escaping (Result<URL, Error>) -> Void) {
    guard isSecureHttpsUrl(url) else {
      completion(.failure(WallpaperDownloadTransportError.invalidUrl))
      return
    }

    WallpaperDownloadWorker(
      url: url,
      maximumBytes: maximumBytes,
      configuration: configuration,
      completion: completion
    ).start()
  }
}

// URLSession invokes delegate methods on one serial operation queue.
private final class WallpaperDownloadWorker: NSObject, URLSessionDataDelegate, @unchecked Sendable {
  private let requestUrl: URL
  private let maximumBytes: Int
  private let completion: (Result<URL, Error>) -> Void
  private var task: URLSessionDataTask?
  private var temporaryFile: URL?
  private var fileOutputStream: OutputStream?
  private var receivedBytes = 0
  private var receivedHttpResponse = false
  private var completed = false

  init(
    url: URL,
    maximumBytes: Int,
    configuration: URLSessionConfiguration,
    completion: @escaping (Result<URL, Error>) -> Void
  ) {
    requestUrl = url
    self.maximumBytes = maximumBytes
    self.completion = completion
    let delegateQueue = OperationQueue()
    delegateQueue.maxConcurrentOperationCount = 1
    super.init()
    // The active session must retain this delegate for the task lifetime.
    activeSession = URLSession(
      configuration: configuration, delegate: self, delegateQueue: delegateQueue)
  }

  private var activeSession: URLSession?

  func start() {
    guard let activeSession else {
      finish(.failure(WallpaperDownloadTransportError.transportFailure))
      return
    }
    let task = activeSession.dataTask(with: requestUrl)
    self.task = task
    task.resume()
  }

  func urlSession(
    _ session: URLSession,
    dataTask: URLSessionDataTask,
    didReceive response: URLResponse,
    completionHandler: @escaping (URLSession.ResponseDisposition) -> Void
  ) {
    guard let httpResponse = response as? HTTPURLResponse else {
      finish(.failure(WallpaperDownloadTransportError.missingHttpResponse))
      completionHandler(.cancel)
      return
    }
    guard isSecureHttpsUrl(response.url) else {
      finish(.failure(WallpaperDownloadTransportError.insecureRedirect))
      completionHandler(.cancel)
      return
    }
    if (300..<400).contains(httpResponse.statusCode),
      let location = httpResponse.value(forHTTPHeaderField: "Location"),
      let redirectUrl = URL(string: location, relativeTo: response.url)?.absoluteURL,
      !isSecureHttpsUrl(redirectUrl)
    {
      finish(.failure(WallpaperDownloadTransportError.insecureRedirect))
      completionHandler(.cancel)
      return
    }
    guard (200..<300).contains(httpResponse.statusCode) else {
      finish(.failure(WallpaperDownloadTransportError.httpStatus(httpResponse.statusCode)))
      completionHandler(.cancel)
      return
    }
    if httpResponse.expectedContentLength > Int64(maximumBytes) {
      finish(.failure(WallpaperDownloadTransportError.tooLarge))
      completionHandler(.cancel)
      return
    }
    do {
      let url = FileManager.default.temporaryDirectory
        .appendingPathComponent("async-wallpaper-\(UUID().uuidString).download")
      guard FileManager.default.createFile(atPath: url.path, contents: nil) else {
        throw WallpaperDownloadTransportError.temporaryFileFailure
      }
      temporaryFile = url
      guard let outputStream = OutputStream(toFileAtPath: url.path, append: false) else {
        throw WallpaperDownloadTransportError.temporaryFileFailure
      }
      fileOutputStream = outputStream
      outputStream.open()
      guard outputStream.streamStatus == .open else {
        throw outputStream.streamError ?? WallpaperDownloadTransportError.temporaryFileFailure
      }
      receivedHttpResponse = true
      completionHandler(.allow)
    } catch {
      finish(.failure(error))
      completionHandler(.cancel)
    }
  }

  func urlSession(
    _ session: URLSession,
    task: URLSessionTask,
    willPerformHTTPRedirection response: HTTPURLResponse,
    newRequest request: URLRequest,
    completionHandler: @escaping (URLRequest?) -> Void
  ) {
    guard WallpaperDownloadTransport.isAllowedRedirect(to: request.url) else {
      finish(.failure(WallpaperDownloadTransportError.insecureRedirect))
      completionHandler(nil)
      return
    }
    completionHandler(request)
  }

  func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
    guard !completed else { return }
    guard receivedHttpResponse else {
      finish(.failure(WallpaperDownloadTransportError.missingHttpResponse))
      task?.cancel()
      return
    }
    guard data.count <= maximumBytes - receivedBytes else {
      finish(.failure(WallpaperDownloadTransportError.tooLarge))
      task?.cancel()
      return
    }
    guard let outputStream = fileOutputStream else {
      finish(.failure(WallpaperDownloadTransportError.temporaryFileFailure))
      task?.cancel()
      return
    }
    do {
      try WallpaperDownloadFileWriter.writeAll(data) { buffer, length in
        let count = outputStream.write(buffer, maxLength: length)
        guard count > 0 else {
          throw outputStream.streamError ?? WallpaperDownloadTransportError.temporaryFileFailure
        }
        return count
      }
      receivedBytes += data.count
    } catch {
      finish(.failure(error))
      task?.cancel()
    }
  }

  func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
    guard !completed else { return }
    if error != nil {
      finish(.failure(WallpaperDownloadTransportError.transportFailure))
    } else if !receivedHttpResponse {
      finish(.failure(WallpaperDownloadTransportError.missingHttpResponse))
    } else if receivedBytes == 0 {
      finish(.failure(WallpaperDownloadTransportError.emptyResponse))
    } else if let temporaryFile {
      finish(.success(temporaryFile))
    } else {
      finish(.failure(WallpaperDownloadTransportError.temporaryFileFailure))
    }
  }

  private func finish(_ result: Result<URL, Error>) {
    guard !completed else { return }
    completed = true
    fileOutputStream?.close()
    fileOutputStream = nil

    if case .failure = result, let temporaryFile {
      try? FileManager.default.removeItem(at: temporaryFile)
      self.temporaryFile = nil
    }

    let activeSession = self.activeSession
    self.activeSession = nil
    if case .failure = result {
      activeSession?.invalidateAndCancel()
    } else {
      activeSession?.finishTasksAndInvalidate()
    }
    completion(result)
  }
}
