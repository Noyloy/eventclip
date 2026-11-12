package expo.modules.eventcliprender.realmedia

import org.junit.Assert.*
import org.junit.Test

/** Start-of-export failures must always record a reasonCode and non-blank error text, even when message is null. */
class ProductExportFailureTest {
 @Test fun startRequireMessagesMapToExportFailedWithText() {
  val missing=IllegalArgumentException("OUTPUT_ALREADY_EXISTS")
  assertEquals("EXPORT_FAILED",ProductExportFailure.reasonCode(missing))
  assertEquals("IllegalArgumentException:OUTPUT_ALREADY_EXISTS",ProductExportFailure.text(missing))
  val pending=IllegalArgumentException("PENDING_VOICE_START")
  assertEquals("EXPORT_FAILED",ProductExportFailure.reasonCode(pending))
  assertTrue(ProductExportFailure.text(pending).contains("PENDING_VOICE_START"))
  val notMain=IllegalStateException("EXPORT_START_NOT_ON_MAIN")
  assertEquals("EXPORT_FAILED",ProductExportFailure.reasonCode(notMain))
  assertTrue(ProductExportFailure.text(notMain).contains("EXPORT_START_NOT_ON_MAIN"))
 }

 @Test fun nullMessageStillRecordsClassName() {
  val e=RuntimeException()
  assertNull(e.message)
  assertEquals("EXPORT_FAILED",ProductExportFailure.reasonCode(e))
  assertEquals("RuntimeException",ProductExportFailure.text(e))
 }

 @Test fun causeIsIncludedAndFoundationCodeIsPreserved() {
  val e=IllegalStateException("outer",IllegalArgumentException("OUTPUT_ALREADY_EXISTS"))
  assertEquals("IllegalStateException:outer <- IllegalArgumentException:OUTPUT_ALREADY_EXISTS",ProductExportFailure.text(e))
  assertEquals("UNSUPPORTED_MEDIA",ProductExportFailure.reasonCode(FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)))
  assertEquals("FoundationFailure:UNSUPPORTED_MEDIA",ProductExportFailure.text(FoundationFailure(ImportCode.UNSUPPORTED_MEDIA)))
  assertEquals("PROJECT_CHANGED",ProductExportFailure.reasonCode(IllegalArgumentException("PROJECT_CHANGED")))
  assertEquals("ACCESS_UNREADABLE",ProductExportFailure.reasonCode(IllegalArgumentException("SOURCE_UNREADABLE")))
  assertEquals("UNSUPPORTED_MEDIA",ProductExportFailure.reasonCode(IllegalStateException("AUDIO_GAPLESS_METADATA_UNSUPPORTED")))
 }
}
