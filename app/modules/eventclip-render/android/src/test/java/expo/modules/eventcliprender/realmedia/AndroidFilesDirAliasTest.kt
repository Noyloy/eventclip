package expo.modules.eventcliprender.realmedia
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

/**
 * JVM reproduction of the Android /data/user/0 -> /data/data filesDir alias
 * that failed BirthdayProjectStore construction on device (BUG-002).
 * No Robolectric; local POSIX symlinks only.
 */
class AndroidFilesDirAliasTest {
  private fun packageTree(): Pair<File,File> {
    val tmp=Files.createTempDirectory("android-filesdir-alias").toFile()
    val realPkg=File(tmp,"data/data/com.eventclip.spike").apply{check(mkdirs())}
    File(realPkg,"files").mkdir(); File(realPkg,"cache").mkdir()
    val user=File(tmp,"data/user/0/com.eventclip.spike")
    check(requireNotNull(user.parentFile).mkdirs())
    Files.createSymbolicLink(user.toPath(), realPkg.toPath())
    val aliasFiles=File(user,"files")
    require(aliasFiles.absoluteFile.normalize()!=aliasFiles.canonicalFile)
    require(aliasFiles.canonicalFile==File(realPkg,"files").canonicalFile)
    return tmp to aliasFiles
  }

  @Test fun officialUser0FilesDirAlias_constructsStoreAndKeepsOwnership() {
    val (tmp,filesDir)=packageTree()
    try {
      val store=BirthdayProjectStore(File(filesDir,"eventclip-birthday"))
      assertEquals(store.assetDir.canonicalFile,store.assetDir.absoluteFile)
      val id=UUID.randomUUID().toString()
      val f=store.file(id)
      f.writeText("synthetic")
      val p=store.load().copy(assets=listOf(ImportedAsset(id,"image",f.length(),"image/png")))
      store.save(p)
      assertEquals(p.id,BirthdayProjectStore(File(filesDir,"eventclip-birthday")).load().id)
      assertEquals(f.length(),BirthdayProjectStore(File(filesDir,"eventclip-birthday")).file(id).length())
    } finally { tmp.deleteRecursively() }
  }

  @Test fun cacheDirAlias_settlesWorkspaceWithoutFollowingForeignChildLinks() {
    val (tmp,filesDir)=packageTree()
    try {
      val work=ProductWorkspaces(File(File(filesDir.parentFile,"cache"),"eventclip-birthday-render"))
      val dir=work.allocate()
      assertEquals(dir.canonicalFile,dir.absoluteFile)
      val foreignName=UUID.randomUUID().toString()
      Files.createSymbolicLink(File(dir.parentFile,foreignName).toPath(), File(tmp,"outside").apply{mkdirs()}.toPath())
      try { ProductPrivateFiles.child(dir.parentFile!!,foreignName); fail("foreign symlink must stay rejected") } catch (_:IllegalArgumentException) {}
      work.release(dir)
    } finally { tmp.deleteRecursively() }
  }

  @Test fun escapedDotDotAndForeignRootSymlink_stillRejected() {
    val tmp=Files.createTempDirectory("android-filesdir-escape").toFile()
    try {
      val outside=File(tmp,"outside").apply{check(mkdirs());File(this,"secret").writeText("no")}
      val files=File(tmp,"data/data/com.eventclip.spike/files").apply{check(mkdirs())}
      try { BirthdayProjectStore(File(files,"../outside")); fail("lexical escape must stay rejected") }
      catch (_:IllegalArgumentException) {}
      val trap=File(files,"eventclip-birthday")
      Files.createSymbolicLink(trap.toPath(), outside.toPath())
      try { BirthdayProjectStore(trap); fail("root symlink leaving the package tree must stay rejected") }
      catch (_:IllegalArgumentException) {}
    } finally { tmp.deleteRecursively() }
  }
}
