package expo.modules.eventcliprender.realmedia
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
class JournalRecoveryTest {
  private class ProcessDeath:Error()
  private fun root()=File(Files.createTempDirectory("birthday-transaction-test").toFile(),"eventclip-birthday")
  private fun seed(root:File):BirthdayProject {
    val s=BirthdayProjectStore(root);val p=s.load();val id=UUID.randomUUID().toString();s.file(id).writeText("synthetic-old")
    return p.copy(assets=listOf(ImportedAsset(id,"image",s.file(id).length(),"image/png"))).also{s.save(it)}
  }
  private fun add(s:BirthdayProjectStore,kind:String="image"):ImportedAsset {
    val id=s.allocateImportId();s.partial(id).writeText("synthetic-new");assertTrue(s.partial(id).renameTo(s.file(id)))
    val a=ImportedAsset(id,kind,s.file(id).length(),if(kind=="song")"audio/mpeg"else"image/png",durationUs=4000000)
    s.acceptImported(a,kind=="song");return a
  }
  @Test fun newProjectCrashAtEveryCheckpointPreservesExactlyOneCurrentAndAllOutputs(){
    for(point in listOf("AFTER_BEGIN","AFTER_ALLOCATE","AFTER_ACCEPT","AFTER_PREPARE","AFTER_COMMIT","AFTER_DELETE","BEFORE_JOURNAL_DELETE")){
      val root=root();try {
        val old=seed(root);val export=File(root,"last-good.mp4").apply{writeText("synthetic output")};val foreign=File(root,"unrelated").apply{writeText("do not touch")}
        val s=BirthdayProjectStore(root){if(it==point)throw ProcessDeath()}
        try{s.beginImport(true,"REPLACE_PROJECT");add(s);s.commitImport();fail("checkpoint $point not reached")}catch(_:ProcessDeath){}
        val recovered=BirthdayProjectStore(root);val now=recovered.load();val committed=point in listOf("AFTER_COMMIT","AFTER_DELETE","BEFORE_JOURNAL_DELETE")
        assertEquals(point,!committed,now.id==old.id);assertEquals(1,now.assets.size)
        assertEquals(!committed,recovered.file(old.assets.single().id).exists());assertTrue(recovered.file(now.assets.single().id).isFile)
        assertEquals(now,recovered.load());assertTrue(export.exists());assertTrue(foreign.exists());assertFalse(File(root,"transaction.properties").exists())
      }finally{root.parentFile!!.deleteRecursively()}
    }
  }
  @Test fun partialAndRenamedUnacceptedCopiesCleanedOnlyByAllocatedIdentity(){
    for(rename in listOf(false,true)){val root=root();try {
      val old=seed(root);val s=BirthdayProjectStore(root);s.beginImport(true,"REPLACE_PROJECT");val id=s.allocateImportId();s.partial(id).writeText("synthetic partial");if(rename)assertTrue(s.partial(id).renameTo(s.file(id)))
      val foreign=File(s.assetDir,"unregistered.part").apply{writeText("unrelated")}
      assertEquals(old,BirthdayProjectStore(root).load());assertFalse(s.partial(id).exists());assertFalse(s.file(id).exists());assertTrue(foreign.exists())
    }finally{root.parentFile!!.deleteRecursively()}}
  }
  @Test fun cancelAndExplicitFailureBeforeCommitKeepOldAndDiscardWholeNewBatch(){
    val root=root();try{val old=seed(root);val s=BirthdayProjectStore(root);s.beginImport(true,"REPLACE_PROJECT");val a=add(s)
      try{s.commitImport(AtomicBoolean(true));fail()}catch(e:FoundationFailure){assertEquals(ImportCode.IMPORT_CANCELLED,e.code)}
      s.abortImport();assertEquals(old,s.load());assertFalse(s.file(a.id).exists())
      s.beginImport(true,"REPLACE_PROJECT");val b=add(s);s.abortImport();assertEquals(old,s.load());assertFalse(s.file(b.id).exists())
    }finally{root.parentFile!!.deleteRecursively()}
  }
  @Test fun songSwapRecoveryBeforeAndAfterCommitRetainsOnlyCorrectSong(){
    for(point in listOf("AFTER_ACCEPT","AFTER_COMMIT")){val root=root();try{
      val base=BirthdayProjectStore(root);base.load();base.beginImport();val oldSong=add(base,"song");base.commitImport()
      val s=BirthdayProjectStore(root){if(it==point)throw ProcessDeath()}
      try{s.beginImport();add(s,"song");s.commitImport();fail()}catch(_:ProcessDeath){}
      val recovered=BirthdayProjectStore(root);val now=recovered.load();assertEquals(1,now.assets.size)
      assertEquals(point=="AFTER_ACCEPT",now.assets.single().id==oldSong.id);assertEquals(point=="AFTER_ACCEPT",recovered.file(oldSong.id).exists())
    }finally{root.parentFile!!.deleteRecursively()}}
  }
  @Test fun explicitRemovalAndDeleteProjectUseJournalAndNeverDeleteUnreferencedFiles(){
    for(point in listOf("AFTER_PREPARE","AFTER_COMMIT","AFTER_DELETE")){val root=root();try{
      val old=seed(root);val s=BirthdayProjectStore(root){if(it==point)throw ProcessDeath()};val foreign=File(s.assetDir,UUID.randomUUID().toString()+".asset").apply{writeText("not referenced")}
      try{s.remove(old.assets.single().id,"REMOVE_SOURCE");fail()}catch(_:ProcessDeath){}
      val now=BirthdayProjectStore(root).load();assertEquals(if(point=="AFTER_PREPARE")1 else 0,now.assets.size);assertTrue(foreign.exists())
      BirthdayProjectStore(root).deleteProject("DELETE_PROJECT");assertTrue(foreign.exists())
    }finally{root.parentFile!!.deleteRecursively()}}
  }
  @Test fun identityTamperAndSymlinkEscapeFailClosedWithoutDeletingOutside(){
    val root=root();try{
      val old=seed(root);val s=BirthdayProjectStore(root);s.beginImport(true,"REPLACE_PROJECT");add(s)
      val state=File(root,"project.properties");val original=state.readText();state.writeText(original.replace(old.id,UUID.randomUUID().toString()))
      try{BirthdayProjectStore(root).load();fail()}catch(_:IllegalStateException){}
      assertTrue(s.file(old.assets.single().id).exists())
      val outside=File(root.parentFile,"outside").apply{writeText("original untouched")};val id=UUID.randomUUID().toString()
      Files.createSymbolicLink(File(s.assetDir,"$id.asset").toPath(),outside.toPath())
      try{s.file(id);fail()}catch(_:IllegalArgumentException){}
      assertEquals("original untouched",outside.readText())
      try{s.file("../escape");fail()}catch(_:IllegalArgumentException){}
    }finally{root.parentFile!!.deleteRecursively()}
  }
  @Test fun deletionFailureAfterCommitIsNotFalseImportFailureAndRecoveryRetries(){
    val root=root();try{val old=seed(root);val s=BirthdayProjectStore(root){if(it=="AFTER_DELETE")throw java.io.IOException("synthetic deferred cleanup")}
      s.beginImport(true,"REPLACE_PROJECT");val a=add(s);val next=s.commitImport()
      assertTrue(s.pendingCleanup);assertNotEquals(old.id,next.id);assertTrue(File(root,"transaction.properties").exists())
      val fresh=BirthdayProjectStore(root);assertEquals(next,fresh.load());assertFalse(fresh.file(old.assets.single().id).exists());assertTrue(fresh.file(a.id).exists());assertFalse(File(root,"transaction.properties").exists())
    }finally{root.parentFile!!.deleteRecursively()}
  }
  @Test fun cancellationAfterCommitCannotMisreportRollback(){
    val root=root();try{val old=seed(root);val cancelled=AtomicBoolean(false)
      val s=BirthdayProjectStore(root){if(it=="AFTER_COMMIT")cancelled.set(true)}
      s.beginImport(true,"REPLACE_PROJECT");val a=add(s);val next=s.commitImport(cancelled)
      assertTrue(cancelled.get());assertNotEquals(old.id,next.id);assertEquals(next,BirthdayProjectStore(root).load());assertTrue(s.file(a.id).exists())
    }finally{root.parentFile!!.deleteRecursively()}
  }
  @Test fun retainedCapsAndCoexistencePreflightAreNotCumulativeOldPlusNewQuota(){
    val root=root();try{
      val old=seed(root);val s=BirthdayProjectStore(root)
      try{s.beginImport(true,"KEEP_CURRENT");fail()}catch(_:IllegalArgumentException){}
      try{s.beginImport(true,"REPLACE_PROJECT",Long.MAX_VALUE-FoundationPolicy.FREE_RESERVE_BYTES);fail()}catch(e:FoundationFailure){assertEquals(ImportCode.STORAGE_LOW,e.code)}
      assertEquals(old,s.load());s.beginImport(true,"REPLACE_PROJECT")
      val id=s.allocateImportId();s.file(id).writeText("x")
      try{s.acceptImported(ImportedAsset(id,"image",FoundationPolicy.MAX_SOURCE_BYTES+1,"image/png"),false);fail()}catch(e:FoundationFailure){assertEquals(ImportCode.SOURCE_SIZE_LIMIT,e.code)}
      s.abortImport();assertEquals(old,s.load());assertFalse(s.file(id).exists())
    }finally{root.parentFile!!.deleteRecursively()}
  }
}
