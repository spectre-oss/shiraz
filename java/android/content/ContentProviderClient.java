// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.content.res.AssetFileDescriptor;
import java.io.FileNotFoundException;

/** Local provider handle. Releasing a handle does not shut down the shared provider. */
public class ContentProviderClient implements AutoCloseable {
 private ContentProvider provider;
 ContentProviderClient(ContentProvider provider){if(provider==null)throw new NullPointerException("provider");this.provider=provider;}
 private synchronized ContentProvider active(){if(provider==null)throw new IllegalStateException("Content provider client released");return provider;}
 public ContentProvider getLocalContentProvider(){return active();}
 private static void requireUri(Uri uri){if(uri==null)throw new NullPointerException("uri");}
 public android.os.Bundle call(String method,String arg,android.os.Bundle extras){
  if(method==null)throw new NullPointerException("method");
  return active().call(method,arg,extras);
 }
 public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){return query(uri,projection,selection,selectionArgs,sortOrder,null);}
 public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder,CancellationSignal signal){
  requireUri(uri);ContentProvider target=active();if(signal!=null)signal.throwIfCanceled();
  return target.query(uri,projection,selection,selectionArgs,sortOrder,signal);
 }
 public String getType(Uri uri){requireUri(uri);return active().getType(uri);}
 public Uri insert(Uri uri,ContentValues values){requireUri(uri);return active().insert(uri,values);}
 public int bulkInsert(Uri uri,ContentValues[] values){requireUri(uri);if(values==null)throw new NullPointerException("values");return active().bulkInsert(uri,values);}
 public int update(Uri uri,ContentValues values,String selection,String[] args){requireUri(uri);return active().update(uri,values,selection,args);}
 public int delete(Uri uri,String selection,String[] args){requireUri(uri);return active().delete(uri,selection,args);}
 public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException,RemoteException{return openFile(uri,mode,null);}
 public ParcelFileDescriptor openFile(Uri uri,String mode,CancellationSignal signal)throws FileNotFoundException,RemoteException{
  requireUri(uri);if(mode==null)throw new NullPointerException("mode");
  ContentProvider target=active();if(signal!=null)signal.throwIfCanceled();
  return target.openFile(uri,mode,signal);
 }
 public AssetFileDescriptor openAssetFile(Uri uri,String mode)throws FileNotFoundException,RemoteException{return openAssetFile(uri,mode,null);}
 public AssetFileDescriptor openAssetFile(Uri uri,String mode,CancellationSignal signal)throws FileNotFoundException,RemoteException{
  requireUri(uri);if(mode==null)throw new NullPointerException("mode");
  ContentProvider target=active();if(signal!=null)signal.throwIfCanceled();
  return target.openAssetFile(uri,mode,signal);
 }
 public synchronized boolean release(){if(provider==null)return false;provider=null;return true;}
 @Override public void close(){release();}
}
