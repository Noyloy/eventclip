package expo.modules.eventcliprender.realmedia
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.nio.file.Files
import java.util.UUID
class VoicePersistenceTest {
 private fun exercise(block:(BirthdayProjectStore,String)->Unit){val root=File(Files.createTempDirectory("voice-state-test").toFile(),"eventclip-birthday");try{val s=BirthdayProjectStore(root);val p=s.load();val id=UUID.randomUUID().toString();s.file(id).writeText("synthetic");s.save(p.copy(assets=listOf(ImportedAsset(id,"video",s.file(id).length(),"video/avc",durationUs=4000000))));block(s,id)}finally{root.parentFile!!.deleteRecursively()}}
 @Test fun pendingStartAutosavesAndDiscardNeverClearsSavedRanges(){exercise{s,id->s.startVoiceMark(id,100000);s.finishVoiceMark(1000000);val saved=s.load().voiceRanges;s.startVoiceMark(id,2000000);assertEquals(2000000L,s.load().pendingVoiceStart!!.startUs);s.discardPendingVoiceStart();assertEquals(saved,s.load().voiceRanges);assertNull(s.load().pendingVoiceStart)}}
 @Test fun excludeReincludeReorderAndSongStyleEditsKeepMarks(){exercise{s,id->s.startVoiceMark(id,0);s.finishVoiceMark(500000);val saved=s.load().voiceRanges;s.include(id,false);s.dateOrder(false);s.move(id,0);s.include(id,true);assertEquals(saved,s.load().voiceRanges)}}
 @Test fun invalidEndDoesNotDestroyPendingOrSavedAndOnlyExplicitRemoveClears(){exercise{s,id->s.startVoiceMark(id,1000000);try{s.finishVoiceMark(900000);fail()}catch(_:IllegalArgumentException){};assertNotNull(s.load().pendingVoiceStart);s.finishVoiceMark(2000000);val m=s.load().voiceRanges.single();s.removeVoiceRange(m.id);assertTrue(s.load().voiceRanges.isEmpty())}}
 @Test fun sourceRemovalRemovesOnlyThatSourcesMarksAsExplicitProjectEdit(){exercise{s,id->s.startVoiceMark(id,0);s.finishVoiceMark(1000000);s.remove(id,"REMOVE_SOURCE");assertTrue(s.load().voiceRanges.isEmpty());assertTrue(s.load().assets.isEmpty())}}
}
