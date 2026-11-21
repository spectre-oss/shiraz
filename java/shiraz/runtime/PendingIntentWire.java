// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

final class PendingIntentWire {
    static String request(String op,String token,int kind,int code,int flags,byte[] identity,byte[] payload)throws IOException{
        StringWriter text=new StringWriter();JsonWriter out=new JsonWriter(text);
        out.beginObject().name("op").value(op);
        if(op.equals("pending_intent_obtain")){
            if(identity==null||identity.length<1||identity.length>8192||payload==null||payload.length<1||payload.length>32768)
                throw new IllegalArgumentException("PendingIntent snapshot limit");
            out.name("kind").value(kind).name("request_code").value(code).name("flags").value(flags)
                .name("identity").value(java.util.Base64.getEncoder().encodeToString(identity))
                .name("payload").value(java.util.Base64.getEncoder().encodeToString(payload));
        }else{
            if(!op.equals("pending_intent_inspect")&&!op.equals("pending_intent_cancel"))throw new IllegalArgumentException("PendingIntent operation");
            if(token==null||!token.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("PendingIntent token");
            out.name("token").value(token);
        }
        out.endObject();out.close();return text.toString();
    }
    // 0 success, 1 canceled before delivery, 2 rejected, 3 claimed/uncertain.
    static int status(byte[] bytes,boolean delivery)throws IOException{
        if(bytes.length<1||bytes.length>4096)throw new IOException("PendingIntent status limit");
        JsonReader in=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()));
        Boolean ok=null,canceled=null,uncertain=null;String error=null;
        HashSet<String> fields=new HashSet<String>();in.beginObject();
        while(in.hasNext()){
            String key=in.nextName();if(!fields.add(key))throw new IOException("Duplicate PendingIntent status");
            if(key.equals("ok"))ok=in.nextBoolean();
            else if(key.equals("canceled"))canceled=in.nextBoolean();
            else if(key.equals("uncertain")&&delivery)uncertain=in.nextBoolean();
            else if(key.equals("error")&&!delivery)error=in.nextString();
            else throw new IOException("Unknown PendingIntent status");
        }
        in.endObject();if(in.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing PendingIntent status");in.close();
        if(ok==null)throw new IOException("Missing PendingIntent status");
        if(ok){if(fields.size()!=1)throw new IOException("Invalid PendingIntent success");return 0;}
        if(delivery){
            if(canceled==null||uncertain==null||(canceled&&uncertain))throw new IOException("Invalid delivery failure");
            return canceled?1:uncertain?3:2;
        }
        if(error==null)throw new IOException("Missing PendingIntent denial");
        return Boolean.TRUE.equals(canceled)?1:2;
    }
    static PendingIntentCapability reply(byte[] bytes)throws IOException{
        if(bytes.length<1||bytes.length>65536)throw new IOException("PendingIntent reply limit");
        JsonReader in=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()));
        Boolean ok=null;boolean canceled=false,present=false;String error=null;PendingIntentCapability result=null;
        HashSet<String> fields=new HashSet<String>();in.beginObject();
        while(in.hasNext()){
            String key=in.nextName();if(!fields.add(key))throw new IOException("Duplicate PendingIntent reply field");
            if(key.equals("ok"))ok=in.nextBoolean();
            else if(key.equals("canceled"))canceled=in.nextBoolean();
            else if(key.equals("error"))error=in.nextString();
            else if(key.equals("pending_intent")){
                present=true;
                if(in.peek()==JsonToken.NULL)in.nextNull();
                else{
                    String token=null,pkg=null,sha=null;Integer uid=null,kind=null,flags=null;
                    HashSet<String> names=new HashSet<String>();in.beginObject();
                    while(in.hasNext()){
                        String name=in.nextName();if(!names.add(name))throw new IOException("Duplicate PendingIntent fact");
                        if((name.equals("uid")||name.equals("kind")||name.equals("flags"))&&in.peek()!=JsonToken.NUMBER)throw new IOException("Invalid numeric PendingIntent fact");
                        if((name.equals("token")||name.equals("package")||name.equals("sha256"))&&in.peek()!=JsonToken.STRING)throw new IOException("Invalid text PendingIntent fact");
                        if(name.equals("token"))token=in.nextString();else if(name.equals("package"))pkg=in.nextString();
                        else if(name.equals("sha256"))sha=in.nextString();else if(name.equals("uid"))uid=in.nextInt();
                        else if(name.equals("kind"))kind=in.nextInt();else if(name.equals("flags"))flags=in.nextInt();
                        else throw new IOException("Unknown PendingIntent fact");
                    }
                    in.endObject();if(uid==null||kind==null||flags==null)throw new IOException("Missing PendingIntent facts");
                    result=new PendingIntentCapability(token,pkg,sha,uid,kind,flags);
                }
            }else throw new IOException("Unknown PendingIntent reply field");
        }
        in.endObject();if(in.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing PendingIntent reply");in.close();
        if(ok==null)throw new IOException("Missing PendingIntent status");
        if(!ok){
            if(error==null||present)throw new IOException("Invalid PendingIntent denial");
            if(canceled)throw new ServiceBrokerClient.PendingIntentCanceledException();
            throw new SecurityException("PendingIntent request denied: "+error);
        }
        if(error!=null||fields.contains("canceled")||!present)throw new IOException("Invalid PendingIntent success");
        return result;
    }
}
