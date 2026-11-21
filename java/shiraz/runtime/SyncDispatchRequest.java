// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.accounts.Account;
import android.os.Bundle;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

/** Strict private-control payload, supplied only by the host coordinator. */
final class SyncDispatchRequest {
    final String id, authority;
    final Account account;
    final Bundle extras;
    private SyncDispatchRequest(String id,String authority,Account account,Bundle extras){this.id=id;this.authority=authority;this.account=account;this.extras=extras;}
    private static String string(ByteBuffer input,boolean empty)throws IOException{
        int length=input.getInt();
        if(length<0||length>32768||length>input.remaining())throw new IOException("Sync string length");
        byte[] bytes=new byte[length];input.get(bytes);
        String value=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        if((!empty&&value.isEmpty())||value.indexOf('\0')>=0)throw new IOException("Invalid sync string");
        return value;
    }
    private static Account account(ByteBuffer input)throws IOException{return new Account(string(input,false),string(input,false));}
    static SyncDispatchRequest decode(byte[] bytes,String pkg,String component)throws IOException{
        if(bytes.length>65536)throw new IOException("Sync dispatch frame limit");
        try{
            ByteBuffer input=ByteBuffer.wrap(bytes);
            if(input.getInt()!=3)throw new IOException("Sync control operation");
            String id=string(input,false);
            if(!id.matches("[0-9a-f]{64}")||!pkg.equals(string(input,false))||!component.equals(string(input,false)))throw new IOException("Sync target identity mismatch");
            String authority=string(input,false);Account account=account(input);Bundle extras=new Bundle();
            int count=input.getInt();if(count<0||count>128)throw new IOException("Sync extra count limit");
            HashSet<String> keys=new HashSet<String>();
            for(int i=0;i<count;i++){
                String key=string(input,false);if(!keys.add(key))throw new IOException("Duplicate sync extra");
                switch(input.get()){
                    case 0:extras.putString(key,null);break;
                    case 1:int value=input.get();if(value!=0&&value!=1)throw new IOException("Sync boolean");extras.putBoolean(key,value==1);break;
                    case 2:extras.putInt(key,input.getInt());break;
                    case 3:extras.putLong(key,input.getLong());break;
                    case 4:float f=input.getFloat();if(Float.isNaN(f)||Float.isInfinite(f))throw new IOException("Sync float");extras.putFloat(key,f);break;
                    case 5:double d=input.getDouble();if(Double.isNaN(d)||Double.isInfinite(d))throw new IOException("Sync double");extras.putDouble(key,d);break;
                    case 6:extras.putString(key,string(input,true));break;
                    case 7:extras.putParcelable(key,account(input));break;
                    default:throw new IOException("Sync extra type");
                }
            }
            if(input.hasRemaining())throw new IOException("Trailing sync dispatch data");
            return new SyncDispatchRequest(id,authority,account,extras);
        }catch(java.nio.BufferUnderflowException|IllegalArgumentException failure){throw new IOException("Malformed sync dispatch",failure);}
    }
}
