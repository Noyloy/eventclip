package expo.modules.eventcliprender

import android.content.Context
import java.io.File

/** App-private directories. Never use /data/local/tmp (adb-only; not app-readable). */
object SpikePaths {
  fun root(context: Context): File = File(context.filesDir, "eventclip").also { it.mkdirs() }
  fun fixtures(context: Context): File = File(root(context), "fixtures").also { it.mkdirs() }
  fun out(context: Context): File = File(root(context), "out").also { it.mkdirs() }
  fun logs(context: Context): File = File(root(context), "logs").also { it.mkdirs() }
  fun state(context: Context): File = File(root(context), "state").also { it.mkdirs() }
  fun progressFile(context: Context): File = File(state(context), "progress.json")
  fun latestResultsFile(context: Context): File = File(logs(context), "latest-results.json")
}
