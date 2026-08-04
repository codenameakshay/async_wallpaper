package com.codenameakshay.async_wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultContractTest {
  @Test
  fun `pre M and pre N retain compatible home-wallpaper defaults when probes do not exist`() {
    assertTrue(AndroidWallpaperApiPolicy.wallpaperSupported(apiLevel = 22, frameworkValue = null))
    assertTrue(AndroidWallpaperApiPolicy.settingAllowed(apiLevel = 23, frameworkValue = null))
    assertFalse(AndroidWallpaperApiPolicy.wallpaperSupported(apiLevel = 23, frameworkValue = null))
    assertFalse(AndroidWallpaperApiPolicy.settingAllowed(apiLevel = 24, frameworkValue = null))
  }

  @Test
  fun `pre N lock and both targets are rejected before a home-only write`() {
    val lock = requireNotNull(
      AndroidWallpaperApiPolicy.unsupportedTargetResult(
        apiLevel = 23,
        target = WallpaperTargetData.LOCK,
      ),
    )
    val both = requireNotNull(
      AndroidWallpaperApiPolicy.unsupportedTargetResult(
        apiLevel = 23,
        target = WallpaperTargetData.BOTH,
      ),
    )

    assertEquals(OperationStatusData.UNSUPPORTED, lock.status)
    assertEquals(TargetStatusData.UNSUPPORTED, lock.lock?.status)
    assertEquals(OperationStatusData.UNSUPPORTED, both.status)
    assertEquals(TargetStatusData.NOT_ATTEMPTED, both.home?.status)
    assertEquals(TargetStatusData.UNSUPPORTED, both.lock?.status)
    assertEquals(AndroidWallpaperApiPolicy.ERROR_LOCK_TARGET_UNSUPPORTED, both.errorCode)
    assertEquals(null, AndroidWallpaperApiPolicy.unsupportedTargetResult(23, WallpaperTargetData.HOME))
    assertEquals(null, AndroidWallpaperApiPolicy.unsupportedTargetResult(24, WallpaperTargetData.BOTH))
  }

  @Test
  fun `partial both fallback retains the successful target but never reports applied`() {
    val result = OperationResultPolicy.fromTargetResults(
      requestedTarget = WallpaperTargetData.BOTH,
      home = OperationResultPolicy.appliedTarget(),
      lock = OperationResultPolicy.failedTarget("wallpaper-apply-failed", "lock failed"),
      fallbackUsed = true,
      fallbackStrategy = WallpaperApplyStrategyData.DIRECT,
    )

    assertEquals(OperationStatusData.FAILED, result.status)
    assertNotEquals(OperationStatusData.APPLIED, result.status)
    assertEquals(TargetStatusData.APPLIED, result.home?.status)
    assertEquals(TargetStatusData.FAILED, result.lock?.status)
    assertEquals(OperationResultPolicy.ERROR_PARTIAL_APPLY, result.errorCode)
    assertEquals(true, result.fallbackUsed)
    assertEquals(WallpaperApplyStrategyData.DIRECT, result.fallbackStrategy)
  }

  @Test
  fun `combined fallback can report applied only after every requested target succeeds`() {
    val result = OperationResultPolicy.fromTargetResults(
      requestedTarget = WallpaperTargetData.BOTH,
      home = OperationResultPolicy.appliedTarget(),
      lock = OperationResultPolicy.appliedTarget(),
      fallbackUsed = true,
      fallbackStrategy = WallpaperApplyStrategyData.DIRECT,
    )

    assertEquals(OperationStatusData.APPLIED, result.status)
    assertEquals(TargetStatusData.APPLIED, result.home?.status)
    assertEquals(TargetStatusData.APPLIED, result.lock?.status)
    assertTrue(result.fallbackUsed == true)
  }

  @Test
  fun `unsupported or restricted target never claims an applied wallpaper`() {
    val result = OperationResultPolicy.unsupported(
      WallpaperTargetData.BOTH,
      code = StaticWallpaperEngine.ERROR_WALLPAPER_NOT_ALLOWED,
      message = "Wallpaper changes are restricted.",
    )

    assertEquals(OperationStatusData.UNSUPPORTED, result.status)
    assertEquals(TargetStatusData.UNSUPPORTED, result.home?.status)
    assertEquals(TargetStatusData.UNSUPPORTED, result.lock?.status)
    assertFalse(result.home?.status == TargetStatusData.APPLIED)
    assertFalse(result.lock?.status == TargetStatusData.APPLIED)
  }

  @Test
  fun `system UI outcomes leave every requested target not attempted`() {
    val preview = OperationResultPolicy.previewOpened(WallpaperTargetData.BOTH)
    val awaiting = OperationResultPolicy.awaitingUserConfirmation(WallpaperTargetData.HOME)

    assertEquals(OperationStatusData.PREVIEW_OPENED, preview.status)
    assertEquals(TargetStatusData.NOT_ATTEMPTED, preview.home?.status)
    assertEquals(TargetStatusData.NOT_ATTEMPTED, preview.lock?.status)
    assertEquals(OperationStatusData.AWAITING_USER_CONFIRMATION, awaiting.status)
    assertEquals(TargetStatusData.NOT_ATTEMPTED, awaiting.home?.status)
  }

  @Test
  fun `validation failures use stable failed target detail`() {
    val result = OperationResultPolicy.failed(
      WallpaperTargetData.LOCK,
      StaticWallpaperEngine.ERROR_INVALID_REQUEST,
      "A complete request is required.",
    )

    assertEquals(OperationStatusData.FAILED, result.status)
    assertEquals(TargetStatusData.FAILED, result.lock?.status)
    assertEquals(StaticWallpaperEngine.ERROR_INVALID_REQUEST, result.lock?.errorCode)
    assertEquals(StaticWallpaperEngine.ERROR_INVALID_REQUEST, result.errorCode)
  }
}
