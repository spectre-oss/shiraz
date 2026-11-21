// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.SyncAdapterType;
import android.util.JsonReader;
import android.util.JsonToken;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Host declarations retain component and generation; no selected-owner or bind capability. */
public final class SyncAdapterDiscovery {
    private SyncAdapterDiscovery(){}
    public static SyncAdapterType[] getSyncAdapterTypes(){
        ServiceBrokerClient client=RuntimeBroker.client();
        if(client==null)throw new UnsupportedOperationException("Sync discovery requires a registered host session");
        try{
            Declaration[] declarations=client.listSelectedSyncAdapters();
            SyncAdapterType[] result=new SyncAdapterType[declarations.length];
            for(int i=0;i<result.length;i++)result[i]=declarations[i].type;
            return result;
        }catch(IOException failure){throw new IllegalStateException("Host sync discovery failed",failure);}
    }
    public static final class Declaration {
        public final SyncAdapterType type;
        public final String component,process,apkSha256;
        public final int uid;
        private Declaration(SyncAdapterType type,String component,String process,String hash,int uid){
            this.type=type;this.component=component;this.process=process;apkSha256=hash;this.uid=uid;
        }
    }
    private static String string(JsonReader reader)throws IOException{
        if(reader.peek()!=JsonToken.STRING)throw new IOException("Sync descriptor string required");
        String value=reader.nextString();
        if(value.isEmpty()||value.length()>1024||value.indexOf('\0')>=0)throw new IOException("Invalid sync descriptor string");
        return value;
    }
    private static boolean name(String value){return value!=null&&value.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");}
    private static Declaration declaration(JsonReader reader)throws IOException{
        HashSet<String> fields=new HashSet<String>();
        String authority=null,account=null,pkg=null,component=null,process=null,hash=null,settings=null;
        int uid=-1;boolean visible=false,upload=false,always=false,parallel=false;
        reader.beginObject();while(reader.hasNext()){
            String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate sync descriptor field");
            switch(key){
                case "authority":authority=string(reader);break;
                case "account_type":account=string(reader);break;
                case "package":pkg=string(reader);break;
                case "component":component=string(reader);break;
                case "process":process=string(reader);break;
                case "apk_sha256":hash=string(reader);break;
                case "settings_activity":if(reader.peek()==JsonToken.NULL)reader.nextNull();else settings=string(reader);break;
                case "user_visible":visible=reader.nextBoolean();break;
                case "supports_uploading":upload=reader.nextBoolean();break;
                case "always_syncable":always=reader.nextBoolean();break;
                case "allow_parallel_syncs":parallel=reader.nextBoolean();break;
                case "uid":
                    if(reader.peek()!=JsonToken.NUMBER)throw new IOException("Sync UID must be integer");
                    String number=reader.nextString();if(!number.matches("1[0-9]{4}"))throw new IOException("Invalid sync UID");uid=Integer.parseInt(number);break;
                default:throw new IOException("Unknown sync descriptor field");
            }
        }reader.endObject();
        if(fields.size()!=12||!name(pkg)||!name(component)||hash==null||!hash.matches("[0-9a-f]{64}")||uid<10000||process==null)
            throw new IOException("Incomplete sync descriptor identity");
        if(!(name(process)||process.startsWith(":")&&name(process.substring(1))||process.startsWith(pkg+":")&&name(process.substring(pkg.length()+1))))throw new IOException("Invalid sync process");
        return new Declaration(new SyncAdapterType(authority,account,visible,upload,always,parallel,settings,pkg),component,process,hash,uid);
    }
    static Declaration[] decode(byte[] bytes)throws IOException{
        if(bytes.length>65536)throw new IOException("Sync discovery reply size limit");
        try(JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()))){
            HashSet<String> fields=new HashSet<String>();Boolean ok=null;String error=null;boolean found=false;
            ArrayList<Declaration> result=new ArrayList<Declaration>();
            reader.beginObject();while(reader.hasNext()){
                String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate sync discovery field");
                if(key.equals("ok"))ok=reader.nextBoolean();
                else if(key.equals("error"))error=string(reader);
                else if(key.equals("sync_adapters")){
                    found=true;reader.beginArray();while(reader.hasNext()){
                        if(result.size()>=256)throw new IOException("Sync descriptor count limit");result.add(declaration(reader));
                    }reader.endArray();
                }else throw new IOException("Unknown sync discovery field");
            }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing sync discovery data");
            if(ok==null)throw new IOException("Missing sync discovery status");
            if(!ok){if(error==null||found)throw new IOException("Malformed sync discovery denial");throw new SecurityException("Sync discovery denied: "+error);}
            if(error!=null||!found)throw new IOException("Malformed sync discovery success");
            return result.toArray(new Declaration[result.size()]);
        }catch(IllegalArgumentException|IllegalStateException malformed){throw new IOException("Malformed sync discovery reply",malformed);}
    }
}
