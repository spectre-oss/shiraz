// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
/** Type identity for absent optional notification views; rendering remains explicit. */
public class RemoteViews implements android.os.Parcelable {
 public RemoteViews(String pkg,int layout){throw new UnsupportedOperationException("RemoteViews rendering is not implemented");}
 public int describeContents(){return 0;}public void writeToParcel(android.os.Parcel out,int flags){throw new UnsupportedOperationException("RemoteViews IPC");}
}
