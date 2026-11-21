// SPDX-License-Identifier: GPL-3.0-only
package android.accounts;
import android.os.Parcel;
import android.os.Parcelable;
/** Account authenticator metadata; equality identifies the account type. */
public class AuthenticatorDescription implements Parcelable {
 public final String type,packageName;
 public final int labelId,iconId,smallIconId,accountPreferencesId;
 public final boolean customTokens;
 public AuthenticatorDescription(String type,String packageName,int label,int icon,int smallIcon,int preferences,boolean customTokens){
  this(type,packageName,label,icon,smallIcon,preferences,customTokens,false);
 }
 public AuthenticatorDescription(String type,String packageName,int label,int icon,int smallIcon,int preferences){this(type,packageName,label,icon,smallIcon,preferences,false);}
 private AuthenticatorDescription(String type,String pkg,int label,int icon,int smallIcon,int preferences,boolean custom,boolean key){
  if(type==null||!key&&pkg==null)throw new IllegalArgumentException("Authenticator type and package required");
  this.type=type;packageName=pkg;labelId=label;iconId=icon;smallIconId=smallIcon;accountPreferencesId=preferences;customTokens=custom;
 }
 public static AuthenticatorDescription newKey(String type){return new AuthenticatorDescription(type,null,0,0,0,0,false,true);}
 @Override public boolean equals(Object value){return value instanceof AuthenticatorDescription&&type.equals(((AuthenticatorDescription)value).type);}
 @Override public int hashCode(){return type.hashCode();}
 @Override public String toString(){return "AuthenticatorDescription {type="+type+"}";}
 public int describeContents(){return 0;}
 public void writeToParcel(Parcel parcel,int flags){parcel.writeString(type);parcel.writeString(packageName);parcel.writeInt(labelId);parcel.writeInt(iconId);parcel.writeInt(smallIconId);parcel.writeInt(accountPreferencesId);parcel.writeByte((byte)(customTokens?1:0));}
 public static final Parcelable.Creator<AuthenticatorDescription> CREATOR=new Parcelable.Creator<AuthenticatorDescription>(){
  public AuthenticatorDescription createFromParcel(Parcel parcel){return new AuthenticatorDescription(parcel.readString(),parcel.readString(),parcel.readInt(),parcel.readInt(),parcel.readInt(),parcel.readInt(),parcel.readByte()==1,true);}
  public AuthenticatorDescription[] newArray(int size){return new AuthenticatorDescription[size];}
 };
}
