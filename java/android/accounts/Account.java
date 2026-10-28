// SPDX-License-Identifier: GPL-3.0-only
package android.accounts;
import android.os.Parcel;
import android.os.Parcelable;
public class Account implements Parcelable {
    public final String name,type;
    public Account(String name,String type){
        if(name==null||name.isEmpty()||type==null||type.isEmpty())throw new IllegalArgumentException("Account name and type are required");
        this.name=name;this.type=type;
    }
    public boolean equals(Object other){return other instanceof Account&&name.equals(((Account)other).name)&&type.equals(((Account)other).type);}
    public int hashCode(){return 31*(527+name.hashCode())+type.hashCode();}
    public String toString(){return "Account {name="+name+", type="+type+"}";}
    public int describeContents(){return 0;}
    public void writeToParcel(Parcel out,int flags){out.writeString(name);out.writeString(type);}
    public static final Parcelable.Creator<Account> CREATOR=new Parcelable.Creator<Account>(){
        public Account createFromParcel(Parcel in){return new Account(in.readString(),in.readString());}
        public Account[] newArray(int size){return new Account[size];}
    };
}
