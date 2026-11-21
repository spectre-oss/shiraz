// SPDX-License-Identifier: GPL-3.0-only
package android.app.admin;

import android.os.Parcel;
import android.os.Parcelable;

/** Update policy value; creating a value never configures the host updater. */
public final class SystemUpdatePolicy implements Parcelable {
 public static final int TYPE_INSTALL_AUTOMATIC=1,TYPE_INSTALL_WINDOWED=2,TYPE_POSTPONE=3;
 private final int type,start,end;
 private SystemUpdatePolicy(int type,int start,int end){
  if(type<1||type>3)throw new IllegalArgumentException("Unknown update policy");
  if(type==TYPE_INSTALL_WINDOWED&&(start<0||start>=1440||end<0||end>=1440))throw new IllegalArgumentException("Update window must be minutes in a day");
  this.type=type;this.start=start;this.end=end;
 }
 public static SystemUpdatePolicy createAutomaticInstallPolicy(){return new SystemUpdatePolicy(TYPE_INSTALL_AUTOMATIC,-1,-1);}
 public static SystemUpdatePolicy createWindowedInstallPolicy(int startTime,int endTime){return new SystemUpdatePolicy(TYPE_INSTALL_WINDOWED,startTime,endTime);}
 public static SystemUpdatePolicy createPostponeInstallPolicy(){return new SystemUpdatePolicy(TYPE_POSTPONE,-1,-1);}
 public int getPolicyType(){return type;}
 public int getInstallWindowStart(){return type==TYPE_INSTALL_WINDOWED?start:-1;}
 public int getInstallWindowEnd(){return type==TYPE_INSTALL_WINDOWED?end:-1;}
 public int describeContents(){return 0;}
 public void writeToParcel(Parcel out,int flags){out.writeInt(type);out.writeInt(start);out.writeInt(end);}
 public static final Parcelable.Creator<SystemUpdatePolicy> CREATOR=new Parcelable.Creator<SystemUpdatePolicy>(){
  public SystemUpdatePolicy createFromParcel(Parcel in){return new SystemUpdatePolicy(in.readInt(),in.readInt(),in.readInt());}
  public SystemUpdatePolicy[] newArray(int size){return new SystemUpdatePolicy[size];}
 };
 private final java.util.ArrayList<FreezePeriod> freezePeriods=new java.util.ArrayList<FreezePeriod>();
 public SystemUpdatePolicy setFreezePeriods(java.util.List<FreezePeriod> periods){
  freezePeriods.clear();
  if(periods!=null)freezePeriods.addAll(periods);
  return this;
 }
 public java.util.List<FreezePeriod> getFreezePeriods(){return new java.util.ArrayList<FreezePeriod>(freezePeriods);}
 public static final class ValidationFailedException extends IllegalArgumentException implements Parcelable {
  public static final int ERROR_UNKNOWN=1,ERROR_DUPLICATE_OR_OVERLAP=2,ERROR_NEW_FREEZE_PERIOD_TOO_LONG=3,
   ERROR_NEW_FREEZE_PERIOD_TOO_CLOSE=4,ERROR_COMBINED_FREEZE_PERIOD_TOO_LONG=5,ERROR_COMBINED_FREEZE_PERIOD_TOO_CLOSE=6;
  private final int errorCode;
  ValidationFailedException(int errorCode,String message){super(message);this.errorCode=errorCode;}
  public int getErrorCode(){return errorCode;}
  public int describeContents(){return 0;}
  public void writeToParcel(Parcel dest,int flags){dest.writeInt(errorCode);dest.writeString(getMessage());}
  public static final Parcelable.Creator<ValidationFailedException> CREATOR=new Parcelable.Creator<ValidationFailedException>(){
   public ValidationFailedException createFromParcel(Parcel source){return new ValidationFailedException(source.readInt(),source.readString());}
   public ValidationFailedException[] newArray(int size){return new ValidationFailedException[size];}
  };
 }
}
