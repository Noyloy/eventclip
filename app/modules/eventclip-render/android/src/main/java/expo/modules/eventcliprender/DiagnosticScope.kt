package expo.modules.eventcliprender

/** Pure diagnostic expectations; does not configure or override the Media3 watchdog. */
object DiagnosticScope {
  const val MATCHED_SECONDS = 20.0
  const val MATCHED_FRAMES = 600
  const val FULL_SECONDS = 300.0
  fun matchedGate(exportOk: Boolean, frames: Int?, tracksValid: Boolean): Boolean =
    exportOk && frames == MATCHED_FRAMES && tracksValid
}
