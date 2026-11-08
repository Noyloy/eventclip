package expo.modules.eventcliprender.realmedia
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
class FoundationTest {
  private fun failCode(code:ImportCode,block:()->Unit){try{block();fail("expected $code")}catch(e:FoundationFailure){assertEquals(code,e.code)}}
  @Test fun beatWindowUsesCumulativeStartAndDoesNotCutWholeClip(){
    val p=FoundationPolicy.plan(listOf(VisualInput("p1","image",0L),VisualInput("clip","video",7250000L),VisualInput("p2","image",0L)),60000000L,listOf(3800000L,10000000L,15000000L))
    assertEquals(3.8,p.items[0].durationSec,0.00001)
    assertEquals(7.25,p.items[1].durationSec,0.0)
    assertEquals(11.05,p.items[2].startSec,0.00001)
    assertEquals(3.95,p.items[2].durationSec,0.00001)
    assertEquals(15.0,p.durationSec,0.00001)
    assertTrue(p.musicEndsAtMovieEnd);assertEquals(14.0,p.musicFadeStartSec!!,0.00001)
  }
  @Test fun fallbackExcludeTieAndLimits(){
    val p=FoundationPolicy.plan(listOf(VisualInput("a","image",0L),VisualInput("excluded","video",1000000000L,false)),4000000L,emptyList())
    assertEquals(4.0,p.durationSec,0.0);assertNull(p.musicFadeStartSec)
    assertEquals(3.0,FoundationPolicy.plan(listOf(VisualInput("a","image",0L)),10000000L,listOf(3000000L,5000000L)).durationSec,0.0)
    failCode(ImportCode.SONG_TOO_SHORT){FoundationPolicy.plan(listOf(VisualInput("a","image",0L)),3000000L,emptyList())}
    failCode(ImportCode.DURATION_LIMIT){FoundationPolicy.plan(List(23){VisualInput("$it","image",0L)},120000000L,emptyList())}
    failCode(ImportCode.ITEM_LIMIT){FoundationPolicy.plan(List(31){VisualInput("$it","image",0L)},120000000L,emptyList())}
    assertEquals(90.0,FoundationPolicy.plan(listOf(VisualInput("clip","video",90000000L)),90000000L,listOf(3000000L)).durationSec,0.0)
  }
  @Test fun exactMicrosecondCapsAndReplacementBudget(){
    failCode(ImportCode.DURATION_LIMIT){FoundationPolicy.plan(listOf(VisualInput("x","video",90000001L)),100000000L,emptyList())}
    failCode(ImportCode.SONG_TOO_SHORT){FoundationPolicy.plan(listOf(VisualInput("x","video",89999999L)),89999998L,emptyList())}
    val p=FoundationPolicy.plan(listOf(VisualInput("x","video",89999999L)),89999999L,emptyList());assertEquals(90L,p.displaySecondsCeil)
    val assets=listOf(ImportedAsset("v","video",900,"video/avc",included=false),ImportedAsset("s","song",500,"audio/mpeg"))
    assertEquals(1400L,FoundationPolicy.finalBudgetBeforeCopy(assets,false));assertEquals(900L,FoundationPolicy.finalBudgetBeforeCopy(assets,true))
    failCode(ImportCode.ITEM_LIMIT){FoundationPolicy.plan(List(31){VisualInput("$it","image",0L,false)}+VisualInput("v","video",1),1,emptyList())}
  }
  @Test fun boundedCopyNoFalseSuccessAndPartialCleanup(){
    val dir=Files.createTempDirectory("birthday-copy").toFile();val f=File(dir,"synthetic.part")
    try {
      assertEquals(4L,FoundationPolicy.copyBounded(ByteArrayInputStream(byteArrayOf(1,2,3,4)),f,0,{1000000000L},AtomicBoolean(false)))
      f.delete()
      failCode(ImportCode.SOURCE_SIZE_LIMIT){FoundationPolicy.copyBounded(ByteArrayInputStream(byteArrayOf(1,2)),f,FoundationPolicy.MAX_SOURCE_BYTES-1,{1000000000L},AtomicBoolean(false))};assertFalse(f.exists())
      failCode(ImportCode.STORAGE_LOW){FoundationPolicy.copyBounded(ByteArrayInputStream(byteArrayOf(1)),f,0,{0L},AtomicBoolean(false))};assertFalse(f.exists())
      failCode(ImportCode.IMPORT_CANCELLED){FoundationPolicy.copyBounded(ByteArrayInputStream(byteArrayOf(1)),f,0,{1000000000L},AtomicBoolean(true))};assertFalse(f.exists())
      failCode(ImportCode.COPY_FAILED){FoundationPolicy.copyBounded(ByteArrayInputStream(byteArrayOf()),f,0,{1000000000L},AtomicBoolean(false))};assertFalse(f.exists())
    }finally{dir.deleteRecursively()}
  }
  @Test fun directRemoteProviderRejected(){
    FoundationPolicy.validateLocalProvider("content","media")
    failCode(ImportCode.ACCESS_UNREADABLE){FoundationPolicy.validateLocalProvider("https","example.com")}
    failCode(ImportCode.ACCESS_UNREADABLE){FoundationPolicy.validateLocalProvider("content","com.google.android.apps.docs.storage")}
  }
  @Test fun durableProjectReloadExcludeReincludeOrderAndNoSourceDependency(){
    val root=File(Files.createTempDirectory("birthday-store-parent").toFile(),"eventclip-birthday")
    try {
      val s=BirthdayProjectStore(root);val p=s.load();val a=UUID.randomUUID().toString();val b=UUID.randomUUID().toString()
      s.file(a).writeText("synthetic fixture only");s.file(b).writeText("another synthetic fixture")
      s.save(p.copy(assets=listOf(ImportedAsset(a,"image",22,"image/png",captureAtMs=20),ImportedAsset(b,"video",25,"video/avc",1000,captureAtMs=10))))
      assertEquals(b,s.ordered(s.load()).first().id)
      assertFalse(s.include(a,false).assets.first{it.id==a}.included)
      assertTrue(s.include(a,true).assets.first{it.id==a}.included)
      assertFalse(s.move(a,1).keepDateOrder)
      assertEquals(b,BirthdayProjectStore(root).load().assets.first().id)
      File(s.assetDir,"unfinished.part").writeText("partial")
      s.load();assertTrue(File(s.assetDir,"unfinished.part").exists());assertTrue(s.file(a).exists())
      assertFalse(File(root,"project.properties").readText().contains("content://"))
      try{s.file("../escape");fail()}catch(_:IllegalArgumentException){}
      s.remove(a,"REMOVE_SOURCE");assertFalse(s.file(a).exists());assertTrue(s.file(b).exists())
      try{s.remove(UUID.randomUUID().toString(),"REMOVE_SOURCE");fail()}catch(_:IllegalArgumentException){}
      s.deleteProject("DELETE_PROJECT");assertTrue(root.exists());assertFalse(s.file(b).exists())
    }finally{root.parentFile?.deleteRecursively()}
  }
}
