// SPDX-License-Identifier: GPL-3.0-only
package android.app;
import android.os.Parcel;
import android.os.Parcelable;
public final class MissingForegroundServiceTypeException extends ForegroundServiceTypeException implements Parcelable {
 public MissingForegroundServiceTypeException(String message){super(message);}
 public int describeContents(){return 0;}
 public void writeToParcel(Parcel destination,int flags){destination.writeString(getMessage());}
 public static final Parcelable.Creator<MissingForegroundServiceTypeException> CREATOR=new Parcelable.Creator<MissingForegroundServiceTypeException>(){
  public MissingForegroundServiceTypeException createFromParcel(Parcel source){return new MissingForegroundServiceTypeException(source.readString());}
  public MissingForegroundServiceTypeException[] newArray(int size){return new MissingForegroundServiceTypeException[size];}
 };
}
