package expo.modules.eventcliprender.realmedia

/** Maps a create/export exception to the reasonCode and error text recorded on the job and in events. */
internal object ProductExportFailure {
 fun reasonCode(e:Throwable):String=when{
  e is FoundationFailure->e.code.name
  e.message=="AUDIO_GAPLESS_METADATA_UNSUPPORTED"->"UNSUPPORTED_MEDIA"
  e.message=="PROJECT_CHANGED"->"PROJECT_CHANGED"
  e.message=="SOURCE_UNREADABLE"->"ACCESS_UNREADABLE"
  else->"EXPORT_FAILED"
 }
 /** Always non-blank: class name plus message and cause, so a null Exception.message is still recorded. */
 fun text(e:Throwable):String {
  val parts=ArrayList<String>()
  val seen=HashSet<Throwable>()
  var t:Throwable?=e
  while(t!=null&&seen.add(t)){
   val label=listOfNotNull(t.javaClass.simpleName,t.message?.takeIf{it.isNotBlank()}).joinToString(":")
   if(label.isNotEmpty())parts.add(label)
   t=t.cause
  }
  return parts.joinToString(" <- ").ifEmpty{e.javaClass.name}
 }
}
