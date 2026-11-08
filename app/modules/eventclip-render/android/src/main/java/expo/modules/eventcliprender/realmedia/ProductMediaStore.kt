package expo.modules.eventcliprender.realmedia
import android.content.ContentUris
import android.content.ContentValues
import android.content.ContentResolver
import android.os.Bundle
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.provider.MediaStore

/** API29+ own MediaStore rows only. Never queries Gallery/user media or requests storage permissions. */
class ProductMediaStore(private val context:Context):SaveDestination {
 init{require(Build.VERSION.SDK_INT>=29)}
 private val resolver=context.contentResolver
 private val collection=MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
 private val path=Environment.DIRECTORY_MOVIES+"/EventClip/"
 private fun name(id:String):String{ProductPrivateFiles.id(id);return "EventClip-$id.mp4"}
 private fun uri(token:String):Uri=Uri.parse(token).also{u->require(u.query==null&&u.fragment==null&&u.scheme=="content"&&u.authority=="media"&&u.pathSegments.size==4&&u.pathSegments.take(3)==listOf("external_primary","video","media")&&u.lastPathSegment?.toLongOrNull()?.let{it>0}==true)}
 override fun freeBytes()=StatFs(Environment.getExternalStorageDirectory().absolutePath).availableBytes
 override fun insert(saveId:String):String= requireNotNull(resolver.insert(collection,ContentValues().apply{put(MediaStore.Video.Media.DISPLAY_NAME,name(saveId));put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");put(MediaStore.Video.Media.RELATIVE_PATH,path);put(MediaStore.Video.Media.IS_PENDING,1)})).toString().also{uri(it)}
 override fun find(saveId:String,token:String?):PendingSaveRow? {
  val expected=name(saveId);val target=token?.let{uri(it)}?:collection
  val columns=arrayOf(MediaStore.Video.Media._ID,MediaStore.Video.Media.DISPLAY_NAME,MediaStore.Video.Media.MIME_TYPE,MediaStore.Video.Media.RELATIVE_PATH,MediaStore.Video.Media.IS_PENDING,MediaStore.Video.Media.OWNER_PACKAGE_NAME)
  val where="${MediaStore.Video.Media.DISPLAY_NAME}=? AND ${MediaStore.Video.Media.RELATIVE_PATH}=? AND ${MediaStore.Video.Media.OWNER_PACKAGE_NAME}=?"
  val args=arrayOf(expected,path,context.packageName)
  val cursor=if(Build.VERSION.SDK_INT>=30)resolver.query(target,columns,selection(where,args),null)else resolver.query(MediaStore.setIncludePending(target),columns,where,args,null)
  return cursor?.use{c->if(!c.moveToFirst())return@use null
   val row=ContentUris.withAppendedId(collection,c.getLong(0));require(c.getString(1)==expected&&c.getString(2)=="video/mp4"&&c.getString(3)==path&&c.getString(5)==context.packageName);require(token==null||row==target)
   val result=PendingSaveRow(row.toString(),saveId,c.getInt(4)==1);require(!c.moveToNext());result}
 }
 private fun checked(row:PendingSaveRow):Uri {val actual=requireNotNull(find(row.saveId,row.token));require(actual==row);return uri(row.token)}
 override fun open(row:PendingSaveRow):SaveOutput {
  require(row.pending);val descriptor=requireNotNull(resolver.openFileDescriptor(checked(row),"w"));val out=ParcelFileDescriptor.AutoCloseOutputStream(descriptor)
  return object:SaveOutput{override val stream:java.io.OutputStream=out;override fun sync(){out.fd.sync()};override fun close(){out.close()}}
 }
 override fun openRead(row:PendingSaveRow):java.io.InputStream=requireNotNull(resolver.openInputStream(checked(row)))
 private fun selection(where:String,args:Array<String>)=Bundle().apply{putString(ContentResolver.QUERY_ARG_SQL_SELECTION,where);putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,args);if(Build.VERSION.SDK_INT>=30)putInt(MediaStore.QUERY_ARG_MATCH_PENDING,MediaStore.MATCH_INCLUDE)}
 private val identityWhere="${MediaStore.Video.Media.IS_PENDING}=? AND ${MediaStore.Video.Media.DISPLAY_NAME}=? AND ${MediaStore.Video.Media.RELATIVE_PATH}=? AND ${MediaStore.Video.Media.MIME_TYPE}=? AND ${MediaStore.Video.Media.OWNER_PACKAGE_NAME}=?"
 private fun identityArgs(row:PendingSaveRow)=arrayOf("1",name(row.saveId),path,"video/mp4",context.packageName)
 override fun publish(row:PendingSaveRow){require(row.pending);val target=checked(row);val values=ContentValues().apply{put(MediaStore.Video.Media.IS_PENDING,0)}
  val n=if(Build.VERSION.SDK_INT>=30)resolver.update(target,values,selection(identityWhere,identityArgs(row)))else resolver.update(target,values,identityWhere,identityArgs(row));require(n==1)
 }
 override fun deletePending(row:PendingSaveRow){require(row.pending);val current=find(row.saveId,row.token)?:return;if(!current.pending)return
  if(Build.VERSION.SDK_INT>=30)resolver.delete(uri(row.token),selection(identityWhere,identityArgs(row)))else resolver.delete(uri(row.token),identityWhere,identityArgs(row))
 }
}
