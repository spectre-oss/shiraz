// SPDX-License-Identifier: GPL-3.0-only
package android.app;
import android.os.Parcel;
import android.os.Parcelable;
public final class InvalidForegroundServiceTypeException extends ForegroundServiceTypeException implements Parcelable {
 public InvalidForegroundServiceTypeException(String message){super(message);}
 public int describeContents(){return 0;}
 public void writeToParcel(Parcel destination,int flags){destination.writeString(getMessage());}
 public static final Parcelable.Creator<InvalidForegroundServiceTypeException> CREATOR=new Parcelable.Creator<InvalidForegroundServiceTypeException>(){
  public InvalidForegroundServiceTypeException createFromParcel(Parcel source){return new InvalidForegroundServiceTypeException(source.readString());}
  public InvalidForegroundServiceTypeException[] newArray(int size){return new InvalidForegroundServiceTypeException[size];}
 };
}
