package expo.modules.eventcliprender
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
class OutputPolicyTest {
  @Test fun selectedOutputOnlyAndTraversalRejected() {
    val root=Files.createTempDirectory("eventclip-output-policy").toFile()
    try {
      val a=File(root,"out_720_run.mp4").apply{writeText("synthetic")}
      val b=File(root,"out_1080_run.mp4").apply{writeText("synthetic1080")}
      assertEquals(a.canonicalFile,OutputPolicy.resolve(root,"720",a.name))
      assertEquals(b.canonicalFile,OutputPolicy.resolve(root,"1080",b.name))
      for(name in listOf("../outside.mp4","/etc/passwd","x/../../a.mp4","..mp4","notthere.mp4")) {
        try {OutputPolicy.resolve(root,"720",name);fail(name)}catch(_:IllegalArgumentException){}
      }
      try{OutputPolicy.resolve(root,"invalid",a.name);fail()}catch(_:IllegalArgumentException){}
      File(root,"empty.mp4").writeText("")
      try{OutputPolicy.resolve(root,"720","empty.mp4");fail()}catch(_:IllegalArgumentException){}
    } finally {root.deleteRecursively()}
  }
  @Test fun symlinkEscapeRejected() {
    val root=Files.createTempDirectory("eventclip-policy").toFile()
    val outside=File.createTempFile("eventclip-outside", ".mp4").apply{writeText("synthetic")}
    try {
      Files.createSymbolicLink(File(root,"escape.mp4").toPath(),outside.toPath())
      try{OutputPolicy.resolve(root,"720","escape.mp4");fail()}catch(_:IllegalArgumentException){}
    }finally{root.deleteRecursively();outside.delete()}
  }
  @Test fun full1080ConfigAndPrerequisites() {
    assertEquals(Triple(300.0,1080,1920),OutputPolicy.expected("1080"))
    assertEquals(Triple(300.0,720,1280),OutputPolicy.expected("720"))
    assertEquals(Triple(20.0,720,1280),OutputPolicy.expected("matched20"))
    assertEquals(9000,(OutputPolicy.expected("1080").first*30).toInt())
    assertFalse(OutputPolicy.prerequisites(false,true))
    assertFalse(OutputPolicy.prerequisites(true,false))
    assertTrue(OutputPolicy.prerequisites(true,true))
  }
}
