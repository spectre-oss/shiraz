// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.util.List;
/** Host file/resource reads plus in-process content providers for the loaded package. */
public class ContentResolver {
 private final Context context;
 public ContentResolver(Context context){if(context==null)throw new NullPointerException("context");this.context=context;}
 private ContentProvider localProvider(Uri uri)throws FileNotFoundException{
  String authority=uri.getAuthority();if("downloads".equals(authority)){android.app.DownloadManager manager=(android.app.DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);if(manager!=null)return manager.shirazProvider();}
  ContentProvider provider=ContentProvider.shirazLocal(authority);
  if(provider==null)throw new FileNotFoundException("No content provider for "+uri);return provider;
 }
 public final InputStream openInputStream(Uri uri)throws FileNotFoundException {
  if(uri==null)throw new NullPointerException("uri");String scheme=uri.getScheme();
  if("file".equals(scheme)){
   String authority=uri.getAuthority();if(authority!=null&&!authority.isEmpty()&&!"localhost".equals(authority))throw new FileNotFoundException("Remote file authority unavailable: "+authority);
   String path=uri.getPath();if(path==null||path.indexOf('\0')>=0)throw new FileNotFoundException("Invalid file path");return new FileInputStream(path);
  }
  if("android.resource".equals(scheme)){
   String authority=uri.getAuthority();Resources resources;
   if("android".equals(authority))resources=Resources.getSystem();else if(context.getPackageName().equals(authority))resources=context.getResources();else throw new FileNotFoundException("Resource package unavailable: "+authority);
   List<String> parts=uri.getPathSegments();int id=0;
   try{if(parts.size()==1)id=Integer.parseInt(parts.get(0));else if(parts.size()==2)id=resources.getIdentifier(parts.get(1),parts.get(0),authority);else throw new FileNotFoundException("Resource URI requires id or type/name");
    if(id==0)throw new FileNotFoundException("Resource not found: "+uri);return resources.openRawResource(id);
   }catch(NumberFormatException|Resources.NotFoundException error){FileNotFoundException failure=new FileNotFoundException("Resource not found: "+uri);failure.initCause(error);throw failure;}
  }
  if("content".equals(scheme)){
   try{
    AssetFileDescriptor asset=localProvider(uri).openAssetFile(uri,"r");
    if(asset==null)throw new FileNotFoundException("No entry for "+uri);
    return asset.createInputStream();
   }catch(FileNotFoundException error){throw error;}
   catch(IOException error){FileNotFoundException failure=new FileNotFoundException("Unable to create stream for "+uri);failure.initCause(error);throw failure;}
  }
  throw new FileNotFoundException("No content provider for "+uri);
 }
 public final OutputStream openOutputStream(Uri uri)throws FileNotFoundException{return openOutputStream(uri,"w");}
 public final OutputStream openOutputStream(Uri uri,String mode)throws FileNotFoundException{
  if(uri==null)throw new NullPointerException("uri");if(mode==null)throw new NullPointerException("mode");
  if(!"content".equals(uri.getScheme()))throw new FileNotFoundException("No content provider for "+uri);
  AssetFileDescriptor asset=localProvider(uri).openAssetFile(uri,mode);
  if(asset==null)throw new FileNotFoundException("No entry for "+uri);
  return new FileOutputStream(asset.getFileDescriptor());
 }
 public final ParcelFileDescriptor openFileDescriptor(Uri uri,String mode)throws FileNotFoundException{
  if(uri==null)throw new NullPointerException("uri");if(mode==null)throw new NullPointerException("mode");
  if(!"content".equals(uri.getScheme()))throw new FileNotFoundException("No content provider for "+uri);
  ParcelFileDescriptor fd=localProvider(uri).openFile(uri,mode);if(fd==null)throw new FileNotFoundException("No entry for "+uri);return fd;
 }
 public final AssetFileDescriptor openAssetFileDescriptor(Uri uri,String mode)throws FileNotFoundException{
  if(uri==null)throw new NullPointerException("uri");if(mode==null)throw new NullPointerException("mode");
  if(!"content".equals(uri.getScheme()))throw new FileNotFoundException("No content provider for "+uri);
  AssetFileDescriptor asset=localProvider(uri).openAssetFile(uri,mode);if(asset==null)throw new FileNotFoundException("No entry for "+uri);return asset;
 }
 public final String getType(Uri uri){
  if(uri==null)throw new NullPointerException("uri");
  if(!"content".equals(uri.getScheme()))return null;
  try{return localProvider(uri).getType(uri);}catch(FileNotFoundException missing){return null;}
 }
 public final android.database.Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){
  return query(uri,projection,selection,selectionArgs,sortOrder,null);
 }
 public final android.database.Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder,android.os.CancellationSignal cancellationSignal){
  if(uri==null)throw new NullPointerException("uri");
  if(!"content".equals(uri.getScheme()))return null;
  try{return localProvider(uri).query(uri,projection,selection,selectionArgs,sortOrder,cancellationSignal);}catch(FileNotFoundException missing){return null;}
 }
 public final android.database.Cursor query(Uri uri,String[] projection,android.os.Bundle queryArgs,android.os.CancellationSignal cancellationSignal){
  return query(uri,projection,null,null,null,cancellationSignal);
 }
 public final ContentProviderClient acquireContentProviderClient(Uri uri){
  if(uri==null)throw new NullPointerException("uri");
  ContentProvider provider="content".equals(uri.getScheme())?ContentProvider.shirazLocal(uri.getAuthority()):null;
  return provider==null?null:new ContentProviderClient(provider);
 }
 public final ContentProviderClient acquireContentProviderClient(String name){
  ContentProvider provider=ContentProvider.shirazLocal(name);
  return provider==null?null:new ContentProviderClient(provider);
 }
 public final ContentProviderClient acquireUnstableContentProviderClient(Uri uri){return acquireContentProviderClient(uri);}
 public final ContentProviderClient acquireUnstableContentProviderClient(String name){return acquireContentProviderClient(name);}
 public final Uri insert(Uri url,ContentValues values){
  if(url==null)throw new NullPointerException("url");
  if(!"content".equals(url.getScheme()))return null;
  ContentProvider provider=ContentProvider.shirazLocal(url.getAuthority());
  return provider==null?null:provider.insert(url,values);
 }
 public final int delete(Uri url,String where,String[] selectionArgs){
  if(url==null)throw new NullPointerException("url");
  if(!"content".equals(url.getScheme()))return 0;
  ContentProvider provider=ContentProvider.shirazLocal(url.getAuthority());
  return provider==null?0:provider.delete(url,where,selectionArgs);
 }
 public final int update(Uri uri,ContentValues values,String where,String[] selectionArgs){
  if(uri==null)throw new NullPointerException("uri");
  if(!"content".equals(uri.getScheme()))return 0;
  ContentProvider provider=ContentProvider.shirazLocal(uri.getAuthority());
  return provider==null?0:provider.update(uri,values,where,selectionArgs);
 }
 public final void registerContentObserver(Uri uri,boolean notifyForDescendants,android.database.ContentObserver observer){}
 public final void unregisterContentObserver(android.database.ContentObserver observer){}
 public void notifyChange(Uri uri,android.database.ContentObserver observer){}
 public void notifyChange(Uri uri,android.database.ContentObserver observer,boolean syncToNetwork){}
 /** No Android settings provider is installed. Absence is distinct from a value of zero. */
 public String shirazReadSetting(String table,String name){if(name==null)throw new NullPointerException("name");if(!"global".equals(table)&&!"system".equals(table)&&!"secure".equals(table))throw new IllegalArgumentException("Settings table");if(shiraz.runtime.DeviceProfile.selected()){if("secure".equals(table)&&"android_id".equals(name)){shiraz.runtime.HostAppInstaller.recordAccess("settings:secure:android_id");return shiraz.runtime.DeviceProfile.get("settings.androidId",null);}if("system".equals(table)&&"screen_brightness".equals(name)){shiraz.runtime.HostAppInstaller.recordAccess("settings:system:screen_brightness");return shiraz.runtime.DeviceProfile.get("settings.screenBrightness",null);}}return null;}
}
