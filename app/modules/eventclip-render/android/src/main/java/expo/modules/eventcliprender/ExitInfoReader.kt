package expo.modules.eventcliprender

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build

/**
 * Historical process exits via ApplicationExitInfo (API 30+).
 * Incomplete by design — OEMs differ; does not replace crash reporting.
 */
object ExitInfoReader {
  fun recent(context: Context, max: Int = 5): List<Map<String, Any?>> {
    if (Build.VERSION.SDK_INT < 30) {
      return listOf(mapOf("supported" to false, "reason" to "API<30"))
    }
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val list = am.getHistoricalProcessExitReasons(null, 0, max)
    return list.map { info ->
      mapOf(
        "supported" to true,
        "timestamp" to info.timestamp,
        "reason" to info.reason,
        "reasonLabel" to reasonLabel(info.reason),
        "description" to info.description,
        "importance" to info.importance,
        "status" to info.status,
        "coverageNote" to
          "Partial historical sample only — not complete crash/ANR coverage; may omit some kills.",
      )
    }
  }

  private fun reasonLabel(reason: Int): String {
    if (Build.VERSION.SDK_INT < 30) return "unknown"
    return when (reason) {
      ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
      ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
      ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
      ApplicationExitInfo.REASON_CRASH -> "CRASH"
      ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
      ApplicationExitInfo.REASON_ANR -> "ANR"
      ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
      ApplicationExitInfo.REASON_OTHER -> "OTHER"
      else -> "CODE_$reason"
    }
  }
}
