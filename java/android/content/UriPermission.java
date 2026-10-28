// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

/** Immutable grant description. Creating this value does not grant access. */
public final class UriPermission implements Parcelable {
 public static final long INVALID_TIME=Long.MIN_VALUE;
 private final Uri uri;
 private final int modes;
 private final long persisted;
 public UriPermission(Uri uri,int modes,long persisted){this.uri=uri;this.modes=modes;this.persisted=persisted;}
 public UriPermission(Parcel input){this((Uri)input.readParcelable(Uri.class.getClassLoader()),input.readInt(),input.readLong());}
 public Uri getUri(){return uri;}
 public boolean isReadPermission(){return (modes&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0;}
 public boolean isWritePermission(){return (modes&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0;}
 public long getPersistedTime(){return persisted;}
 public int describeContents(){return 0;}
 public void writeToParcel(Parcel output,int flags){output.writeParcelable(uri,flags);output.writeInt(modes);output.writeLong(persisted);}
 public static final Parcelable.Creator<UriPermission> CREATOR=new Parcelable.Creator<UriPermission>(){
  public UriPermission createFromParcel(Parcel input){return new UriPermission(input);}
  public UriPermission[] newArray(int size){return new UriPermission[size];}
 };
}
