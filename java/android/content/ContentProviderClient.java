// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
public class ContentProviderClient implements AutoCloseable {
 private final ContentProvider provider;
 ContentProviderClient(ContentProvider provider){this.provider=provider;}
 public android.os.Bundle call(String method,String arg,android.os.Bundle extras){
  if(method==null)throw new NullPointerException("method");
  if(provider==null)throw new IllegalStateException("No content provider");
  return provider.call(method,arg,extras);
 }
 public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder,CancellationSignal signal){return provider==null?null:provider.query(uri,projection,selection,selectionArgs,sortOrder,signal);}
 public boolean release(){return true;}
 @Override public void close(){release();}
}
