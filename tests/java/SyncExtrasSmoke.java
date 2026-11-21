// SPDX-License-Identifier: GPL-3.0-only
import android.content.ContentResolver;
import android.accounts.Account;
import android.os.Bundle;
import java.io.Serializable;

public final class SyncExtrasSmoke {
    private static void invalid(Bundle bundle){
        try{ContentResolver.validateSyncExtrasBundle(bundle);throw new AssertionError("Invalid extras accepted");}
        catch(IllegalArgumentException expected){}
    }
    public static void main(String[] args){
        Bundle valid=new Bundle();valid.putInt("i",1);valid.putLong("l",Long.MAX_VALUE);
        valid.putBoolean("b",true);valid.putFloat("f",Float.NaN);valid.putDouble("d",Double.POSITIVE_INFINITY);
        valid.putString("s","text");valid.putString("null",null);Account account=new Account("fixture","test");valid.putParcelable("a",account);
        ContentResolver.validateSyncExtrasBundle(valid);
        if(valid.size()!=8||valid.getParcelable("a")!=account)throw new AssertionError("Validation mutated extras");
        for(Serializable value:new Serializable[]{Byte.valueOf((byte)1),Short.valueOf((short)2),Character.valueOf('x'),new int[]{1},new String[]{"x"},new java.util.ArrayList<String>()}){
            Bundle bundle=new Bundle();bundle.putSerializable("rejected",value);invalid(bundle);
        }
        Bundle nested=new Bundle();nested.putBundle("nested",new Bundle());invalid(nested);
        Bundle binder=new Bundle();binder.putBinder("binder",new android.os.Binder());invalid(binder);
        Bundle parcelable=new Bundle();parcelable.putParcelable("uri",android.net.Uri.parse("content://test/path"));invalid(parcelable);
        try{ContentResolver.validateSyncExtrasBundle(null);throw new AssertionError("Null bundle accepted");}
        catch(IllegalArgumentException expected){if(!(expected.getCause() instanceof NullPointerException))throw new AssertionError("Null cause lost");}
        System.out.println("SYNC_EXTRAS_VALIDATION_OK");
    }
}
