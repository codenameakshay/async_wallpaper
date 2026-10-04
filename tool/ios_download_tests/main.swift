import Foundation
import XCTest

#if canImport(FoundationNetworking)
  import FoundationNetworking
#endif

final class DownloadTransportTests: XCTestCase {
  func testFileWriterRetriesPartialWrites() throws {
    let expected = Data([1, 2, 3, 4, 5])
    var actual = Data()
    var writeCalls = 0

    try WallpaperDownloadFileWriter.writeAll(expected) { buffer, length in
      writeCalls += 1
      let count = min(2, length)
      actual.append(contentsOf: UnsafeBufferPointer(start: buffer, count: count))
      return count
    }

    XCTAssertEqual(actual, expected)
    XCTAssertEqual(writeCalls, 3)
  }

  func testFileWriterRejectsNonpositiveWriteCount() {
    for count in [0, -1] {
      XCTAssertThrowsError(
        try WallpaperDownloadFileWriter.writeAll(Data([1])) { _, _ in count }
      ) { error in
        XCTAssertEqual(error as? WallpaperDownloadTransportError, .temporaryFileFailure)
      }
    }
  }

  func testFileWriterStopsAfterPartialWriteFailure() {
    let expected = Data([1, 2, 3, 4])
    var actual = Data()
    var writeCalls = 0

    XCTAssertThrowsError(
      try WallpaperDownloadFileWriter.writeAll(expected) { buffer, length in
        writeCalls += 1
        if writeCalls == 1 {
          actual.append(contentsOf: UnsafeBufferPointer(start: buffer, count: 2))
          return 2
        }
        return -1
      }
    ) { error in
      XCTAssertEqual(error as? WallpaperDownloadTransportError, .temporaryFileFailure)
    }

    XCTAssertEqual(actual, Data([1, 2]))
    XCTAssertEqual(writeCalls, 2)
  }

  func testFileWriterPropagatesWriteErrors() {
    let expected = NSError(domain: "test-output-stream", code: 7)
    var actual = Data()
    var writeCalls = 0

    XCTAssertThrowsError(
      try WallpaperDownloadFileWriter.writeAll(Data([1, 2, 3])) { buffer, length in
        writeCalls += 1
        if writeCalls == 1 {
          actual.append(contentsOf: UnsafeBufferPointer(start: buffer, count: min(2, length)))
          return min(2, length)
        }
        throw expected
      }
    ) { error in
      let actual = error as NSError
      XCTAssertEqual(actual.domain, expected.domain)
      XCTAssertEqual(actual.code, expected.code)
    }

    XCTAssertEqual(actual, Data([1, 2]))
    XCTAssertEqual(writeCalls, 2)
  }

  func testDefaultLimitMatchesAndroidDownloadLimit() {
    XCTAssertEqual(WallpaperDownloadTransport.maximumDownloadBytes, 64 * 1024 * 1024)
  }

  func testRejectsHttpErrorEvenWhenBodyIsAnImage() throws {
    StubURLProtocol.handler = { request in
      let response = HTTPURLResponse(
        url: request.url!, statusCode: 404, httpVersion: nil,
        headerFields: ["Content-Type": "image/png"]
      )!
      let png = Data(
        base64Encoded:
          "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lS8AAAAASUVORK5CYII="
      )!
      return (response, png)
    }

    let result = waitForDownload()

    XCTAssertThrowsError(try result.get()) { error in
      XCTAssertEqual(error as? WallpaperDownloadTransportError, .httpStatus(404))
    }
  }

  func testRedirectPolicyRejectsHttpAndAllowsHttps() throws {
    XCTAssertFalse(
      WallpaperDownloadTransport.isAllowedRedirect(
        to: URL(string: "http://insecure.example/image.png")
      )
    )
    XCTAssertTrue(
      WallpaperDownloadTransport.isAllowedRedirect(
        to: URL(string: "https://secure.example/image.png")
      )
    )
  }

  func testRejectsPayloadLargerThanConfiguredLimitAndRemovesTempFile() throws {
    StubURLProtocol.handler = { request in
      let response = HTTPURLResponse(
        url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil
      )!
      return (response, Data([1, 2, 3, 4, 5]))
    }

    let filesBefore = temporaryDownloadFiles()
    let result = waitForDownload(maximumBytes: 4)

    XCTAssertThrowsError(try result.get()) { error in
      XCTAssertEqual(error as? WallpaperDownloadTransportError, .tooLarge)
    }
    XCTAssertEqual(temporaryDownloadFiles(), filesBefore)
  }

  func testRejectsStreamThatCrossesLimitAcrossChunks() throws {
    StubURLProtocol.handler = { request in
      let response = HTTPURLResponse(
        url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil
      )!
      return (response, Data())
    }
    StubURLProtocol.dataChunks = [Data([1, 2, 3]), Data([4, 5])]

    let filesBefore = temporaryDownloadFiles()
    let result = waitForDownload(maximumBytes: 4)

    XCTAssertThrowsError(try result.get()) { error in
      XCTAssertEqual(error as? WallpaperDownloadTransportError, .tooLarge)
    }
    XCTAssertEqual(temporaryDownloadFiles(), filesBefore)
  }

  func testRejectsContentLengthOverLimitBeforeCreatingTemporaryFile() throws {
    StubURLProtocol.handler = { request in
      let response = HTTPURLResponse(
        url: request.url!, statusCode: 200, httpVersion: nil,
        headerFields: ["Content-Length": "5"]
      )!
      return (response, Data())
    }

    let filesBefore = temporaryDownloadFiles()
    let result = waitForDownload(maximumBytes: 4)

    XCTAssertThrowsError(try result.get()) { error in
      XCTAssertEqual(error as? WallpaperDownloadTransportError, .tooLarge)
    }
    XCTAssertEqual(temporaryDownloadFiles(), filesBefore)
  }

  func testAcceptsResponseAtExactConfiguredLimit() throws {
    let expected = Data([1, 2, 3, 4])
    StubURLProtocol.handler = { request in
      let response = HTTPURLResponse(
        url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil
      )!
      return (response, expected)
    }

    let fileUrl = try waitForDownload(maximumBytes: expected.count).get()
    defer { try? FileManager.default.removeItem(at: fileUrl) }
    XCTAssertEqual(try Data(contentsOf: fileUrl), expected)
  }

  func testRejectsAResponseWhoseFinalUrlIsNotHttps() throws {
    StubURLProtocol.handler = { request in
      let response = HTTPURLResponse(
        url: URL(string: "http://insecure.example/image.png")!,
        statusCode: 200,
        httpVersion: nil,
        headerFields: nil
      )!
      return (response, Data([1]))
    }

    let result = waitForDownload()

    XCTAssertThrowsError(try result.get()) { error in
      XCTAssertEqual(error as? WallpaperDownloadTransportError, .insecureRedirect)
    }
  }

  func testRemovesTemporaryFileAndCompletesOnlyOnceAfterTransportFailure() throws {
    StubURLProtocol.handler = { request in
      let response = HTTPURLResponse(
        url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil
      )!
      return (response, Data([1, 2, 3]))
    }
    StubURLProtocol.errorAfterBody = URLError(.networkConnectionLost)

    let filesBefore = temporaryDownloadFiles()
    var completionCount = 0
    let result = waitForDownload { _ in completionCount += 1 }

    XCTAssertThrowsError(try result.get()) { error in
      XCTAssertEqual(error as? WallpaperDownloadTransportError, .transportFailure)
    }
    XCTAssertEqual(completionCount, 1)
    XCTAssertEqual(temporaryDownloadFiles(), filesBefore)
    StubURLProtocol.errorAfterBody = nil
  }

  func testRejectsEmptySuccessfulResponse() throws {
    StubURLProtocol.handler = { request in
      let response = HTTPURLResponse(
        url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil
      )!
      return (response, Data())
    }

    let filesBefore = temporaryDownloadFiles()
    let result = waitForDownload()

    XCTAssertThrowsError(try result.get()) { error in
      XCTAssertEqual(error as? WallpaperDownloadTransportError, .emptyResponse)
    }
    XCTAssertEqual(temporaryDownloadFiles(), filesBefore)
  }

  func testStreamsSuccessfulHttpsBodyIntoTemporaryFile() throws {
    let expected = Data([1, 2, 3, 4])
    StubURLProtocol.handler = { request in
      let response = HTTPURLResponse(
        url: request.url!, statusCode: 200, httpVersion: nil,
        headerFields: ["Content-Length": "4"]
      )!
      return (response, expected)
    }

    let fileURL = try waitForDownload().get()
    defer { try? FileManager.default.removeItem(at: fileURL) }

    XCTAssertEqual(try Data(contentsOf: fileURL), expected)
  }

  private func waitForDownload(
    onCompletion: ((Result<URL, Error>) -> Void)? = nil,
    maximumBytes: Int = WallpaperDownloadTransport.maximumDownloadBytes
  ) -> Result<URL, Error> {
    let finished = expectation(description: "download completes")
    var result: Result<URL, Error>!
    let configuration = URLSessionConfiguration.ephemeral
    configuration.protocolClasses = [StubURLProtocol.self]
    WallpaperDownloadTransport(maximumBytes: maximumBytes, configuration: configuration)
      .download(
        from: URL(string: "https://example.test/image.png")!
      ) { downloadResult in
        onCompletion?(downloadResult)
        result = downloadResult
        finished.fulfill()
      }
    wait(for: [finished], timeout: 5)
    return result
  }

  private func temporaryDownloadFiles() -> Set<String> {
    let directory = FileManager.default.temporaryDirectory
    return Set(
      (try? FileManager.default.contentsOfDirectory(atPath: directory.path))?
        .filter { $0.hasPrefix("async-wallpaper-") && $0.hasSuffix(".download") } ?? []
    )
  }
}

final class StubURLProtocol: URLProtocol {
  static var handler: ((URLRequest) -> (HTTPURLResponse, Data))?
  static var dataChunks: [Data]?
  static var errorAfterBody: Error?

  override class func canInit(with request: URLRequest) -> Bool { true }
  override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

  override func startLoading() {
    guard let (response, data) = Self.handler?(request) else {
      client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
      return
    }
    client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
    let chunks = Self.dataChunks ?? [data]
    Self.dataChunks = nil
    for chunk in chunks where !chunk.isEmpty {
      client?.urlProtocol(self, didLoad: chunk)
    }
    if let error = Self.errorAfterBody {
      client?.urlProtocol(self, didFailWithError: error)
    } else {
      client?.urlProtocolDidFinishLoading(self)
    }
  }

  override func stopLoading() {}
}

XCTMain([
  testCase([
    (
      "testFileWriterRetriesPartialWrites",
      DownloadTransportTests.testFileWriterRetriesPartialWrites
    ),
    (
      "testFileWriterRejectsNonpositiveWriteCount",
      DownloadTransportTests.testFileWriterRejectsNonpositiveWriteCount
    ),
    (
      "testFileWriterStopsAfterPartialWriteFailure",
      DownloadTransportTests.testFileWriterStopsAfterPartialWriteFailure
    ),
    (
      "testFileWriterPropagatesWriteErrors",
      DownloadTransportTests.testFileWriterPropagatesWriteErrors
    ),
    (
      "testDefaultLimitMatchesAndroidDownloadLimit",
      DownloadTransportTests.testDefaultLimitMatchesAndroidDownloadLimit
    ),
    (
      "testRejectsHttpErrorEvenWhenBodyIsAnImage",
      DownloadTransportTests.testRejectsHttpErrorEvenWhenBodyIsAnImage
    ),
    (
      "testRedirectPolicyRejectsHttpAndAllowsHttps",
      DownloadTransportTests.testRedirectPolicyRejectsHttpAndAllowsHttps
    ),
    (
      "testRejectsPayloadLargerThanConfiguredLimitAndRemovesTempFile",
      DownloadTransportTests.testRejectsPayloadLargerThanConfiguredLimitAndRemovesTempFile
    ),
    (
      "testRejectsStreamThatCrossesLimitAcrossChunks",
      DownloadTransportTests.testRejectsStreamThatCrossesLimitAcrossChunks
    ),
    (
      "testRejectsContentLengthOverLimitBeforeCreatingTemporaryFile",
      DownloadTransportTests.testRejectsContentLengthOverLimitBeforeCreatingTemporaryFile
    ),
    (
      "testAcceptsResponseAtExactConfiguredLimit",
      DownloadTransportTests.testAcceptsResponseAtExactConfiguredLimit
    ),
    (
      "testRejectsAResponseWhoseFinalUrlIsNotHttps",
      DownloadTransportTests.testRejectsAResponseWhoseFinalUrlIsNotHttps
    ),
    (
      "testRemovesTemporaryFileAndCompletesOnlyOnceAfterTransportFailure",
      DownloadTransportTests.testRemovesTemporaryFileAndCompletesOnlyOnceAfterTransportFailure
    ),
    (
      "testRejectsEmptySuccessfulResponse",
      DownloadTransportTests.testRejectsEmptySuccessfulResponse
    ),
    (
      "testStreamsSuccessfulHttpsBodyIntoTemporaryFile",
      DownloadTransportTests.testStreamsSuccessfulHttpsBodyIntoTemporaryFile
    ),
  ])
])
