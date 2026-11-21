// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.app.job.JobWorkItem;
import android.content.Intent;
import android.os.Parcel;
import android.util.JsonReader;
import android.util.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

/** Strict private-channel reply decoder; no package identity is accepted here. */
final class JobWorkReply {
 final JobWorkItem item;final boolean drained,completed;
 private JobWorkReply(JobWorkItem item,boolean drained,boolean completed){this.item=item;this.drained=drained;this.completed=completed;}
 static JobWorkReply decode(byte[] bytes,boolean completing)throws IOException {
  if(bytes==null||bytes.length>65536)throw new IOException("Job work reply size");
  try(JsonReader in=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()))){
   HashSet<String> seen=new HashSet<String>();Boolean ok=null,drained=null,completed=null;JobWorkItem item=null;
   in.beginObject();while(in.hasNext()){
    String name=in.nextName();if(!seen.add(name))throw new IOException("Duplicate job work field");
    if(name.equals("ok"))ok=in.nextBoolean();
    else if(name.equals("drained"))drained=in.nextBoolean();
    else if(name.equals("completed")&&completing)completed=in.nextBoolean();
    else if(name.equals("item")&&!completing){if(in.peek()==JsonToken.NULL)in.nextNull();else item=readItem(in);}
    else throw new IOException("Unknown job work field");
   }in.endObject();
   if(in.peek()!=JsonToken.END_DOCUMENT||!Boolean.TRUE.equals(ok)||drained==null||seen.size()!=3||
      (completing?completed==null:!seen.contains("item"))||drained&&(item!=null||completing&&!completed))throw new IOException("Invalid job work reply");
   return new JobWorkReply(item,drained,Boolean.TRUE.equals(completed));
  }catch(RuntimeException error){throw new IOException("Malformed job work reply",error);}
 }
 private static JobWorkItem readItem(JsonReader in)throws IOException {
  HashSet<String> seen=new HashSet<String>();int id=0,count=0;String payload=null;
  in.beginObject();while(in.hasNext()){
   String name=in.nextName();if(!seen.add(name))throw new IOException("Duplicate job work item field");
   if(name.equals("id"))id=in.nextInt();else if(name.equals("deliveries"))count=in.nextInt();else if(name.equals("payload"))payload=in.nextString();else throw new IOException("Unknown job work item field");
  }in.endObject();
  if(seen.size()!=3||id<1||count<1||payload==null||payload.length()>43692)throw new IOException("Invalid job work item");
  byte[] bytes=java.util.Base64.getDecoder().decode(payload);
  if(bytes.length<1||bytes.length>32768||!java.util.Base64.getEncoder().encodeToString(bytes).equals(payload))throw new IOException("Invalid job work payload");
  Parcel source=Parcel.obtain(),out=Parcel.obtain();
  try{source.unmarshall(bytes,0,bytes.length);source.setDataPosition(0);Intent intent=Intent.CREATOR.createFromParcel(source);source.enforceNoDataAvail();
   out.writeInt(1);intent.writeToParcel(out,0);out.writeInt(count);out.writeInt(id);out.setDataPosition(0);return JobWorkItem.CREATOR.createFromParcel(out);
  }finally{source.recycle();out.recycle();}
 }
}
