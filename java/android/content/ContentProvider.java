// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import java.io.FileNotFoundException;
import java.util.concurrent.ConcurrentHashMap;
/** In-process content provider base; the session routes local authorities only. */
public abstract class ContentProvider {
 private static final ConcurrentHashMap<String,ContentProvider> local=new ConcurrentHashMap<String,ContentProvider>();
 private Context context;private ProviderInfo info;
 public ContentProvider(){}
 public abstract boolean onCreate();
 public abstract Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder);
 public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder,CancellationSignal signal){return query(uri,projection,selection,selectionArgs,sortOrder);}
 public abstract String getType(Uri uri);
 public abstract Uri insert(Uri uri,ContentValues values);
 public abstract int delete(Uri uri,String selection,String[] selectionArgs);
 public abstract int update(Uri uri,ContentValues values,String selection,String[] selectionArgs);
 public int bulkInsert(Uri uri,ContentValues[] values){int count=0;for(ContentValues value:values){if(insert(uri,value)!=null)count++;}return count;}
 public Bundle call(String method,String arg,Bundle extras){return null;}
 public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{throw new FileNotFoundException("No files supported by provider at "+uri);}
 public AssetFileDescriptor openAssetFile(Uri uri,String mode)throws FileNotFoundException{
  ParcelFileDescriptor fd=openFile(uri,mode);return fd==null?null:new AssetFileDescriptor(fd,0,AssetFileDescriptor.UNKNOWN_LENGTH);
 }
 public final Context getContext(){return context;}
 public void attachInfo(Context context,ProviderInfo info){
  if(this.context!=null)return;this.context=context;this.info=info;onCreate();
  if(info!=null&&info.authority!=null){
   String authorities=info.authority;int start=0;
   while(start<=authorities.length()){
    int end=authorities.indexOf(';',start);if(end<0)end=authorities.length();
    if(end>start){String authority=authorities.substring(start,end);if(local.putIfAbsent(authority,this)!=null)android.util.Log.w("ContentProvider","Content authority already published: "+authority);}
    start=end+1;
   }
  }
 }
 static ContentProvider shirazLocal(String authority){return authority==null?null:local.get(authority);}
 protected final ProviderInfo shirazInfo(){return info;}
 public void onConfigurationChanged(android.content.res.Configuration configuration){}
 public void onLowMemory(){}
 public void onTrimMemory(int level){}
 public void shutdown(){}
}
