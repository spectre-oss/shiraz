// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.os.Parcel;
import android.os.Parcelable;

/** Sync descriptor value and lookup key, with the Android API 27 parcel layout. */
public class SyncAdapterType implements Parcelable {
    public final String authority,accountType;
    public final boolean isKey;
    private final int capabilities;
    private final String settingsActivity,packageName;
    public SyncAdapterType(String authority,String accountType,boolean visible,boolean uploading){
        this(authority,accountType,visible,uploading,false,false,null,null);
    }
    public SyncAdapterType(String authority,String accountType,boolean visible,boolean uploading,
            boolean always,boolean parallel,String settings,String packageName){
        this(authority,accountType,(visible?1:0)|(uploading?2:0)|(always?4:0)|(parallel?8:0),settings,packageName,false);
    }
    private SyncAdapterType(String authority,String accountType,int capabilities,String settings,String packageName,boolean key){
        if(authority==null||authority.isEmpty()||accountType==null||accountType.isEmpty())
            throw new IllegalArgumentException("Sync authority and account type must be nonempty");
        this.authority=authority;this.accountType=accountType;this.capabilities=capabilities;
        settingsActivity=settings;this.packageName=packageName;isKey=key;
    }
    private void requireDescriptor(){if(isKey)throw new IllegalStateException("Sync lookup key has no descriptor capabilities");}
    private boolean capability(int mask){requireDescriptor();return (capabilities&mask)!=0;}
    public boolean isUserVisible(){return capability(1);}
    public boolean supportsUploading(){return capability(2);}
    public boolean isAlwaysSyncable(){return capability(4);}
    public boolean allowParallelSyncs(){return capability(8);}
    public String getSettingsActivity(){requireDescriptor();return settingsActivity;}
    public String getPackageName(){return packageName;}
    public static SyncAdapterType newKey(String authority,String accountType){return new SyncAdapterType(authority,accountType,3,null,null,true);}
    @Override public boolean equals(Object value){
        if(!(value instanceof SyncAdapterType))return false;
        SyncAdapterType other=(SyncAdapterType)value;
        return authority.equals(other.authority)&&accountType.equals(other.accountType);
    }
    @Override public int hashCode(){return (17*31+authority.hashCode())*31+accountType.hashCode();}
    @Override public String toString(){return "SyncAdapterType["+authority+", "+accountType+", key="+isKey+", capabilities="+capabilities+", package="+packageName+"]";}
    public int describeContents(){return 0;}
    public void writeToParcel(Parcel target,int flags){
        requireDescriptor();target.writeString(authority);target.writeString(accountType);
        for(int mask=1;mask<=8;mask<<=1)target.writeInt((capabilities&mask)==0?0:1);
        target.writeString(settingsActivity);target.writeString(packageName);
    }
    public SyncAdapterType(Parcel source){
        this(source.readString(),source.readString(),source.readInt()!=0,source.readInt()!=0,
            source.readInt()!=0,source.readInt()!=0,source.readString(),source.readString());
    }
    public static final Parcelable.Creator<SyncAdapterType> CREATOR=new Parcelable.Creator<SyncAdapterType>(){
        public SyncAdapterType createFromParcel(Parcel source){return new SyncAdapterType(source);}
        public SyncAdapterType[] newArray(int size){return new SyncAdapterType[size];}
    };
}
