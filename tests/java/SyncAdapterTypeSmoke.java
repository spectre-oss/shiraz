// SPDX-License-Identifier: GPL-3.0-only
import android.content.SyncAdapterType;
import android.os.Parcel;
import java.util.HashMap;

public final class SyncAdapterTypeSmoke {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        SyncAdapterType plain=new SyncAdapterType("data","accounts",true,false);
        check(plain.isUserVisible()&&!plain.supportsUploading()&&!plain.isAlwaysSyncable()&&!plain.allowParallelSyncs(),"constructor defaults");
        check(plain.getSettingsActivity()==null&&plain.getPackageName()==null&&!plain.isKey&&plain.describeContents()==0,"optional defaults");
        SyncAdapterType key=SyncAdapterType.newKey("data","accounts");
        HashMap<SyncAdapterType,String> map=new HashMap<SyncAdapterType,String>();map.put(plain,"found");
        check("found".equals(map.get(key))&&key.equals(plain)&&plain.equals(key),"key lookup symmetry");
        check(key.isKey&&key.getPackageName()==null,"key identity");
        Object absent=null;
        check(!plain.equals(absent)&&!plain.equals("data")&&!plain.equals(SyncAdapterType.newKey("other","accounts"))&&!plain.equals(SyncAdapterType.newKey("data","other")),"pair identity");
        for(String method:new String[]{"isUserVisible","supportsUploading","isAlwaysSyncable","allowParallelSyncs","getSettingsActivity"}){
            try{key.getClass().getMethod(method).invoke(key);throw new AssertionError("key capability: "+method);}
            catch(java.lang.reflect.InvocationTargetException expected){check(expected.getCause() instanceof IllegalStateException,"key exception");}
        }
        Parcel blocked=Parcel.obtain();try{
            try{key.writeToParcel(blocked,0);throw new AssertionError("key serialized");}catch(IllegalStateException expected){}
            check(blocked.dataSize()==0,"key partial write");
        }finally{blocked.recycle();}
        for(int flags=0;flags<16;flags++){
            Parcel input=Parcel.obtain(),output=Parcel.obtain();try{
                input.writeString("data");input.writeString("accounts");
                for(int bit=0;bit<4;bit++)input.writeInt((flags&(1<<bit))==0?0:-7);
                input.writeString("Settings");input.writeString("owner");input.setDataPosition(0);
                SyncAdapterType decoded=SyncAdapterType.CREATOR.createFromParcel(input);
                check(decoded.isUserVisible()==((flags&1)!=0)&&decoded.supportsUploading()==((flags&2)!=0)&&decoded.isAlwaysSyncable()==((flags&4)!=0)&&decoded.allowParallelSyncs()==((flags&8)!=0),"wire boolean order");
                check(decoded.equals(key)&&decoded.hashCode()==key.hashCode()&&!decoded.isKey,"capabilities excluded from equality");
                check("Settings".equals(decoded.getSettingsActivity())&&"owner".equals(decoded.getPackageName()),"wire strings");
                input.enforceNoDataAvail();decoded.writeToParcel(output,0);output.setDataPosition(0);
                check("data".equals(output.readString())&&"accounts".equals(output.readString()),"serialized identity");
                for(int bit=0;bit<4;bit++)check(output.readInt()==((flags>>bit)&1),"canonical boolean serialization");
                check("Settings".equals(output.readString())&&"owner".equals(output.readString()),"serialized strings");output.enforceNoDataAvail();
            }finally{input.recycle();output.recycle();}
        }
        for(String invalid:new String[]{null,""}){
            try{new SyncAdapterType(invalid,"a",false,false);throw new AssertionError("invalid authority");}catch(IllegalArgumentException expected){}
            try{SyncAdapterType.newKey("a",invalid);throw new AssertionError("invalid account type");}catch(IllegalArgumentException expected){}
        }
        check(SyncAdapterType.CREATOR.newArray(3).length==3,"creator array");
        System.out.println("SYNC_ADAPTER_TYPE_OK");
    }
}
