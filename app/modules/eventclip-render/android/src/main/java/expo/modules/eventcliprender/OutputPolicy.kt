package expo.modules.eventcliprender
import java.io.File
object OutputPolicy {
  val ids = setOf("matched20", "720", "1080")
  fun resolve(root: File, id: String, basename: String): File {
    require(id in ids) { "Unknown output selection" }
    require(basename.matches(Regex("[A-Za-z0-9_-]+\\.mp4"))) { "Unsafe output basename" }
    val f = File(root, basename).canonicalFile
    require(f.parentFile == root.canonicalFile) { "Output outside private directory" }
    require(f.isFile && f.length() > 0) { "Output missing or empty" }
    return f
  }
  fun expected(id: String): Triple<Double, Int, Int> = when(id) {
    "matched20" -> Triple(20.0,720,1280)
    "720" -> Triple(300.0,720,1280)
    "1080" -> Triple(300.0,1080,1920)
    else -> throw IllegalArgumentException("Unknown output selection")
  }
  fun prerequisites(matched: Boolean, full720: Boolean): Boolean = matched && full720
}
