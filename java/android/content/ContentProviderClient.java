// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
public class ContentProviderClient implements AutoCloseable {
 private final ContentProvider provider;
 ContentProviderClient(ContentProvider provider){this.provider=provider;}
 public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder,CancellationSignal signal){return provider==null?null:provider.query(uri,projection,selection,selectionArgs,sortOrder,signal);}
 public boolean release(){return true;}
 @Override public void close(){release();}
}
