// SPDX-License-Identifier: GPL-3.0-only
package android.os;
public final class ParcelUuid implements Parcelable {
    private final java.util.UUID uuid;
    public ParcelUuid(java.util.UUID value){if(value==null)throw new NullPointerException("uuid");uuid=value;}
    public static ParcelUuid fromString(String value){return new ParcelUuid(java.util.UUID.fromString(value));}
    public java.util.UUID getUuid(){return uuid;}
    public String toString(){return uuid.toString();}
    public int hashCode(){return uuid.hashCode();}
    public boolean equals(Object other){return other instanceof ParcelUuid&&uuid.equals(((ParcelUuid)other).uuid);}
    public int describeContents(){return 0;}
    public void writeToParcel(Parcel out,int flags){throw new UnsupportedOperationException("UUID parcel transport is not implemented");}
    public static final Parcelable.Creator<ParcelUuid> CREATOR=new Parcelable.Creator<ParcelUuid>(){public ParcelUuid createFromParcel(Parcel in){throw new UnsupportedOperationException("UUID parcel transport is not implemented");}public ParcelUuid[] newArray(int size){return new ParcelUuid[size];}};
}
