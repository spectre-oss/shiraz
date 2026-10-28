// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.net.Uri;
import android.os.Parcel;
public class UriPermissionSmoke {
 public static void main(String[] args){
  Uri uri=Uri.parse("content://fixture/document/1");
  UriPermission read=new UriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION,123);
  if(!uri.equals(read.getUri())||!read.isReadPermission()||read.isWritePermission()||read.getPersistedTime()!=123)throw new AssertionError("grant value");
  // Uri transport is not supported yet; grant values must not hide that limit.
  Parcel parcel=Parcel.obtain();
  try{read.writeToParcel(parcel,0);throw new AssertionError("unsupported URI transport");}catch(UnsupportedOperationException expected){}finally{parcel.recycle();}
  UriPermission write=new UriPermission(uri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION,UriPermission.INVALID_TIME);
  if(write.isReadPermission()||!write.isWritePermission())throw new AssertionError("mode flags");
  ContentResolver resolver=new ContentResolver(new ContextWrapper(null));
  resolver.getPersistedUriPermissions().add(read);
  if(!resolver.getPersistedUriPermissions().isEmpty()||!resolver.getOutgoingPersistedUriPermissions().isEmpty())throw new AssertionError("fabricated grant");
  try{resolver.takePersistableUriPermission(uri,1);throw new AssertionError("unauthorized persistence");}catch(SecurityException expected){}
  try{resolver.releasePersistableUriPermission(uri,1);throw new AssertionError("nonexistent grant");}catch(SecurityException expected){}
  System.out.println("URI_PERMISSION_VALUE_OK");
 }
}
