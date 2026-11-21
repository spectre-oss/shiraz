// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.os.ParcelFileDescriptor;
import android.os.BinderTransport;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Parcel;
import android.content.Intent;
import android.content.ComponentName;
import android.system.OsConstants;
import android.system.StructTimeval;
import android.system.ErrnoException;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.JsonToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import libcore.io.Libcore;

/** Private host-issued control descriptor. Resolution is not a successful binding. */
public final class ServiceBrokerClient implements Closeable {
    private final ParcelFileDescriptor channel;
    private boolean closed;
    public static final class Resolution {
        public final String packageName, component, process, apkSha256;
        public final int uid;
        private Resolution(String pkg,String component,String process,String hash,int uid) {
            this.packageName=pkg;this.component=component;this.process=process;apkSha256=hash;this.uid=uid;
        }
    }
    /** Takes ownership of the inherited descriptor, including on construction failure. */
    public ServiceBrokerClient(int descriptor) throws IOException {
        ParcelFileDescriptor read=ParcelFileDescriptor.adoptFd(descriptor);
        try {
            Libcore.os.setsockoptTimeval(read.getFileDescriptor(),OsConstants.SOL_SOCKET,OsConstants.SO_RCVTIMEO,StructTimeval.fromMillis(10000));
            Libcore.os.setsockoptTimeval(read.getFileDescriptor(),OsConstants.SOL_SOCKET,OsConstants.SO_SNDTIMEO,StructTimeval.fromMillis(10000));
            channel=read;
        } catch(ErrnoException error) {
            read.close();throw error.rethrowAsIOException();
        } catch(RuntimeException error) {
            read.close();throw error;
        }
    }
    private static final class Reply {
        final Resolution resolution;final ParcelFileDescriptor endpoint;final String lease;final boolean restartCancelled;
        Reply(Resolution resolution,ParcelFileDescriptor endpoint,String lease){this(resolution,endpoint,lease,false);}
        Reply(Resolution resolution,ParcelFileDescriptor endpoint,String lease,boolean restartCancelled){this.resolution=resolution;this.endpoint=endpoint;this.lease=lease;this.restartCancelled=restartCancelled;}
    }
    public static final class Connection implements Closeable {
        public final Resolution resolution;public final IBinder binder;
        final BinderTransport transport;
        private final ServiceBrokerClient owner;private final String lease;private boolean closed;
        private Connection(Resolution resolution,IBinder binder,BinderTransport transport,ServiceBrokerClient owner,String lease){this.resolution=resolution;this.binder=binder;this.transport=transport;this.owner=owner;this.lease=lease;}
        public synchronized void close(){if(closed)return;closed=true;transport.close();owner.release(lease);}
    }
    static final class ServiceUnavailableException extends SecurityException {
        ServiceUnavailableException(String message){super(message);}
    }
    static final class ReceiverFilteredException extends SecurityException {
        ReceiverFilteredException(String message){super(message);}
    }
    String registerDynamicReceiver(String receiver,android.content.IntentFilter filter,String permission,int flags)throws IOException {
        StringWriter text=new StringWriter();JsonWriter writer=new JsonWriter(text);
        writer.beginObject().name("op").value("register_dynamic_receiver").name("receiver").value(receiver)
            .name("permission").value(permission).name("flags").value(flags).name("filter").beginObject();
        writer.name("action").beginArray();for(int i=0;i<filter.countActions();i++)writer.value(filter.getAction(i));writer.endArray();
        writer.name("category").beginArray();for(int i=0;i<filter.countCategories();i++)writer.value(filter.getCategory(i));writer.endArray();
        writer.name("mime").beginArray();for(int i=0;i<filter.countDataTypes();i++){String type=filter.getDataType(i);writer.value(type.indexOf('/')<0?type+"/*":type);}writer.endArray();
        writer.name("scheme").beginArray();for(int i=0;i<filter.countDataSchemes();i++)writer.value(filter.getDataScheme(i));writer.endArray();
        writer.name("priority").value(filter.getPriority()).endObject().endObject();writer.close();
        return dynamicRequest(text.toString(),true);
    }
    void unregisterDynamicReceiver(String receiver)throws IOException {
        dynamicRequest("{\"op\":\"unregister_dynamic_receiver\",\"receiver\":\""+receiver+"\"}",false);
    }
    private synchronized String dynamicRequest(String request,boolean register)throws IOException {
        if(closed)throw new IOException("Broker closed");
        byte[] bytes=request.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>32768)throw new IllegalArgumentException("Dynamic registration byte limit");
        try{
            NativeIpc.send(channel.getFd(),bytes,new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.descriptorCount()!=0||packet.bytes.length>65536)throw new IOException("Dynamic registration reply framing");
                JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                HashSet<String> fields=new HashSet<String>();Boolean ok=null;String error=null,token=null;
                reader.beginObject();while(reader.hasNext()){
                    String name=reader.nextName();if(!fields.add(name))throw new IOException("Duplicate dynamic reply field");
                    if(name.equals("ok"))ok=reader.nextBoolean();
                    else if(name.equals("error"))error=reader.nextString();
                    else if(name.equals("registration"))token=reader.nextString();
                    else throw new IOException("Unknown dynamic reply field");
                }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing dynamic reply");reader.close();
                if(ok==null)throw new IOException("Missing dynamic result");
                if(!ok){if(error==null||token!=null)throw new IOException("Malformed dynamic denial");throw new SecurityException("Dynamic registration denied: "+error);}
                if(error!=null||(register?(token==null||!token.matches("[0-9a-f]{32}")):token!=null))throw new IOException("Malformed dynamic success");
                return token;
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    /** A committed mutation, even when an obsolete worker still needs cleanup. */
    public static final class JobMutation {
        public final String revision;
        public final boolean removed, cleanupPending;
        private JobMutation(String revision,boolean removed,boolean cleanupPending){this.revision=revision;this.removed=removed;this.cleanupPending=cleanupPending;}
    }
    public JobMutation schedulePersistedJob(android.app.job.JobInfo job,String packageName)throws IOException {
        if(job==null||!job.isPersisted())throw new IllegalArgumentException("Persisted job required");
        return scheduleSharedJob(job,packageName);
    }
    public JobMutation scheduleSharedJob(android.app.job.JobInfo job,String packageName)throws IOException {
        String definition=new String(JobDefinition.encode(job,packageName),StandardCharsets.UTF_8);
        return (JobMutation)jobRequest("{\"op\":\"schedule_job\",\"definition\":"+definition+"}","schedule");
    }
    public JobMutation enqueueJob(android.app.job.JobInfo job,android.app.job.JobWorkItem work,String packageName)throws IOException {
        if(work==null||work.getIntent()==null)throw new IllegalArgumentException("Intent job work required");
        android.content.Intent intent=work.getIntent();int depth=0;
        for(android.content.Intent part=intent;part!=null;part=part.getSelector()){
            if(++depth>32)throw new IllegalArgumentException("Job work selector depth");
            if(part.getClipData()!=null||(part.getFlags()&0xc3)!=0)throw new UnsupportedOperationException("Job work URI grants are unavailable");
        }
        android.os.Parcel parcel=android.os.Parcel.obtain();String payload;
        try{intent.writeToParcel(parcel,0);byte[] bytes=parcel.marshall();if(bytes.length<1||bytes.length>32768)throw new IllegalArgumentException("Job work payload limit");payload=java.util.Base64.getEncoder().encodeToString(bytes);}
        finally{parcel.recycle();}
        String definition=new String(JobDefinition.encode(job,packageName),StandardCharsets.UTF_8);
        String request="{\"op\":\"enqueue_job\",\"definition\":"+definition+",\"payload\":\""+payload+"\"}";
        if(request.getBytes(StandardCharsets.UTF_8).length>65536)throw new IllegalArgumentException("Job enqueue request limit");
        return (JobMutation)jobRequest(request,"schedule");
    }
    public JobMutation cancelPersistedJob(int id)throws IOException {
        return (JobMutation)jobRequest("{\"op\":\"cancel_job\",\"id\":"+id+"}","cancel");
    }
    public JobMutation cancelAllSharedJobs()throws IOException {
        return (JobMutation)jobRequest("{\"op\":\"cancel_all_jobs\"}","cancel");
    }
    public int[] listPersistedJobIds()throws IOException {
        return (int[])jobRequest("{\"op\":\"list_job_ids\"}","list");
    }
    public synchronized android.app.job.JobInfo getPersistedJob(int id,String packageName)throws IOException {
        if(packageName==null||packageName.isEmpty())throw new IllegalArgumentException("Package required");
        if(closed)throw new IOException("Broker closed");
        try{
            NativeIpc.send(channel.getFd(),("{\"op\":\"get_job\",\"id\":"+id+"}").getBytes(StandardCharsets.UTF_8),new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.descriptorCount()!=0)throw new IOException("Unexpected job query descriptors");
                return JobDefinition.decodeQueryReply(packet.bytes,packageName,id);
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    private synchronized Object jobRequest(String request,String operation)throws IOException {
        if(closed)throw new IOException("Broker closed");
        try{
            NativeIpc.send(channel.getFd(),request.getBytes(StandardCharsets.UTF_8),new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.descriptorCount()!=0)throw new IOException("Unexpected job reply descriptors");
                return parseJobReply(packet.bytes,operation);
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    private static Object parseJobReply(byte[] bytes,String operation)throws IOException {
        if(bytes.length>65536)throw new IOException("Job reply byte limit");
        JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()));
        HashSet<String> fields=new HashSet<String>();Boolean ok=null,removed=null,cleanup=null;String error=null,revision=null;
        java.util.ArrayList<Integer> ids=null;
        try{
            reader.beginObject();while(reader.hasNext()){
                String name=reader.nextName();if(!fields.add(name))throw new IOException("Duplicate job reply field");
                if(name.equals("ok")||name.equals("removed")||name.equals("cleanup_pending")){
                    if(reader.peek()!=JsonToken.BOOLEAN)throw new IOException("Job reply boolean required");
                    boolean value=reader.nextBoolean();if(name.equals("ok"))ok=value;else if(name.equals("removed"))removed=value;else cleanup=value;
                }else if(name.equals("error")||name.equals("revision")){
                    if(reader.peek()!=JsonToken.STRING)throw new IOException("Job reply string required");
                    String value=reader.nextString();if(name.equals("error"))error=value;else revision=value;
                }else if(name.equals("ids")){
                    ids=new java.util.ArrayList<Integer>();HashSet<Integer> unique=new HashSet<Integer>();
                    reader.beginArray();while(reader.hasNext()){
                        if(ids.size()>=100||reader.peek()!=JsonToken.NUMBER)throw new IOException("Invalid job ID list");
                        String number=reader.nextString();if(!number.matches("-?(0|[1-9][0-9]*)"))throw new IOException("Integer job ID required");
                        int id=Integer.parseInt(number);if(!unique.add(id))throw new IOException("Duplicate job ID");ids.add(id);
                    }reader.endArray();
                }else throw new IOException("Unknown job reply field");
            }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT||ok==null)throw new IOException("Incomplete job reply");
            if(!ok){if(fields.size()!=2||error==null)throw new IOException("Malformed job denial");throw new SecurityException("Persisted job denied: "+error);}
            if(operation.equals("list")){
                if(fields.size()!=2||ids==null)throw new IOException("Malformed job list");
                int[] result=new int[ids.size()];for(int i=0;i<result.length;i++)result[i]=ids.get(i);return result;
            }
            if(fields.size()!=3||cleanup==null)throw new IOException("Malformed job mutation");
            if(operation.equals("schedule")&&revision!=null&&revision.matches("[0-9a-f]{32}"))return new JobMutation(revision,false,cleanup);
            if(operation.equals("cancel")&&removed!=null)return new JobMutation(null,removed,cleanup);
            throw new IOException("Malformed job mutation result");
        }catch(IllegalStateException|NumberFormatException errorValue){throw new IOException("Invalid job reply",errorValue);}
        finally{reader.close();}
    }
    public Resolution resolve(String pkg,String component)throws IOException{return request(pkg,component,false).resolution;}
    public Resolution resolveReceiver(String pkg,String component,String recipientPermission)throws IOException{
        if(recipientPermission!=null&&(recipientPermission.isEmpty()||recipientPermission.length()>1024))throw new IllegalArgumentException("Receiver permission length");
        return request(pkg,component,false,false,false,null,true,recipientPermission).resolution;
    }
    /** A single raw receiver delivery channel; caller must run await() or discard(). */
    static final class ReceiverConnection {
        private final ServiceBrokerClient owner;private final Reply reply;
        private boolean finished,released;
        ReceiverConnection(ServiceBrokerClient owner,Reply reply)throws IOException {
            this.owner=owner;this.reply=reply;
            try{
                Libcore.os.setsockoptTimeval(reply.endpoint.getFileDescriptor(),OsConstants.SOL_SOCKET,OsConstants.SO_RCVTIMEO,StructTimeval.fromMillis(35000));
                Libcore.os.setsockoptTimeval(reply.endpoint.getFileDescriptor(),OsConstants.SOL_SOCKET,OsConstants.SO_SNDTIMEO,StructTimeval.fromMillis(10000));
            }catch(ErrnoException error){discard();throw error.rethrowAsIOException();}
            catch(RuntimeException|Error error){discard();throw error;}
        }
        // Cancellation wakes an in-flight syscall without closing/reusing its fd.
        synchronized void cancel(){if(!finished)NativeIpc.shutdown(reply.endpoint.getFd());}
        void await(byte[] payload)throws IOException {
            try{
                NativeIpc.send(reply.endpoint.getFd(),payload,new int[0]);
                try(NativeIpc.Packet packet=NativeIpc.receive(reply.endpoint.getFd())){
                    if(packet.descriptorCount()!=0||packet.bytes.length>1024)throw new IOException("Receiver completion framing");
                    JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                    reader.beginObject();if(!reader.hasNext()||!reader.nextName().equals("ok")||!reader.nextBoolean()||reader.hasNext())throw new IOException("Receiver did not complete");
                    reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing receiver completion");reader.close();
                }
                released=true;owner.finishReceiver(reply.lease);
            }finally{discard();}
        }
        // Called by the delivery thread after I/O, or before that thread starts.
        void discard(){
            synchronized(this){if(finished)return;finished=true;try{reply.endpoint.close();}catch(IOException error){System.err.println("SHIRAZ_RECEIVER_CHANNEL_CLOSE_FAILED");}}
            if(!released)owner.release(reply.lease,true);
        }
    }
    ReceiverConnection openReceiver(Intent intent,String permission)throws IOException {return openReceiver(intent,permission,null);}
    ReceiverConnection openReceiver(Intent intent,String permission,String expectedHash)throws IOException {
        ComponentName name=intent.getComponent();if(name==null)throw new IllegalArgumentException("Explicit receiver required");
        if(intent.getPackage()!=null&&!intent.getPackage().equals(name.getPackageName()))throw new IllegalArgumentException("Receiver package mismatch");
        if(permission!=null&&(permission.isEmpty()||permission.length()>1024))throw new IllegalArgumentException("Receiver permission length");
        return new ReceiverConnection(this,request(name.getPackageName(),name.getClassName(),true,false,false,null,true,permission,expectedHash));
    }
    public synchronized java.util.List<Resolution> queryReceivers(Intent intent,String permission)throws IOException {
        if(closed)throw new IOException("Broker closed");
        String pkg=intent.getPackage(),action=intent.getAction();
        if(pkg==null||pkg.isEmpty()||pkg.length()>1024||action==null||action.isEmpty()||action.length()>1024||intent.getComponent()!=null)throw new IllegalArgumentException("Package-scoped receiver query required");
        if(intent.getSelector()!=null)throw new UnsupportedOperationException("Receiver selector resolution");
        if(permission!=null&&(permission.isEmpty()||permission.length()>1024))throw new IllegalArgumentException("Receiver permission length");
        try{
            StringWriter text=new StringWriter();JsonWriter writer=new JsonWriter(text);
            writer.beginObject().name("op").value("query_receivers").name("package").value(pkg).name("action").value(action).name("has_data").value(intent.getData()!=null);
            if(intent.getType()!=null)writer.name("mime").value(intent.getType());if(intent.getScheme()!=null)writer.name("scheme").value(intent.getScheme());
            if(permission!=null)writer.name("recipient_permission").value(permission);
            writer.name("categories").beginArray();if(intent.getCategories()!=null)for(String category:intent.getCategories())writer.value(category);writer.endArray().endObject();writer.close();
            byte[] bytes=text.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>65536)throw new IOException("Receiver query length");
            NativeIpc.send(channel.getFd(),bytes,new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.descriptorCount()!=0||packet.bytes.length>65536)throw new IOException("Receiver query framing");
                JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                HashSet<String> fields=new HashSet<String>(),names=new HashSet<String>();Boolean ok=null;String error=null;boolean found=false;
                java.util.ArrayList<Resolution> result=new java.util.ArrayList<Resolution>();
                reader.beginObject();while(reader.hasNext()){
                    String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate receiver query field");
                    if(key.equals("ok"))ok=reader.nextBoolean();else if(key.equals("error"))error=reader.nextString();
                    else if(key.equals("receivers")){found=true;reader.beginArray();while(reader.hasNext()){
                        if(result.size()>=256)throw new IOException("Receiver query count");Resolution item=readResolution(reader);
                        if(!pkg.equals(item.packageName)||!names.add(item.component))throw new IOException("Receiver query identity mismatch");
                        if(!result.isEmpty()&&(item.uid!=result.get(0).uid||!item.apkSha256.equals(result.get(0).apkSha256)))throw new IOException("Mixed receiver query generations");result.add(item);
                    }reader.endArray();}else throw new IOException("Unknown receiver query field");
                }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing receiver query");reader.close();
                if(ok==null)throw new IOException("Missing receiver query result");
                if(!ok){if(error==null||found)throw new IOException("Malformed receiver query denial");throw new SecurityException("Receiver query denied: "+error);}
                if(error!=null||!found)throw new IOException("Malformed receiver query success");return result;
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    public static final class DynamicResolution {
        public final String packageName,process,apkSha256,receiver,registration;
        public final int uid,priority;
        private DynamicResolution(String pkg,String process,String hash,String receiver,String registration,int uid,int priority){
            this.packageName=pkg;this.process=process;this.apkSha256=hash;this.receiver=receiver;this.registration=registration;this.uid=uid;this.priority=priority;
        }
    }
    private static int dynamicInteger(JsonReader reader)throws IOException {
        if(reader.peek()!=JsonToken.NUMBER)throw new IOException("Dynamic identity integer type");
        String value=reader.nextString();if(!value.matches("-?(0|[1-9][0-9]*)"))throw new IOException("Dynamic identity integer syntax");
        try{return Integer.parseInt(value);}catch(NumberFormatException error){throw new IOException("Dynamic identity integer range",error);}
    }
    private static DynamicResolution readDynamicResolution(JsonReader reader)throws IOException {
        String pkg=null,process=null,hash=null,receiver=null,registration=null;Integer uid=null,priority=null;
        HashSet<String> fields=new HashSet<String>();reader.beginObject();
        while(reader.hasNext()){
            String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate dynamic identity field");
            if(key.equals("package"))pkg=reader.nextString();else if(key.equals("process"))process=reader.nextString();
            else if(key.equals("apk_sha256"))hash=reader.nextString();else if(key.equals("receiver"))receiver=reader.nextString();
            else if(key.equals("registration"))registration=reader.nextString();else if(key.equals("uid"))uid=dynamicInteger(reader);
            else if(key.equals("priority"))priority=dynamicInteger(reader);else throw new IOException("Unknown dynamic identity field");
        }reader.endObject();
        String name="[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*";
        if(pkg==null||pkg.length()>1024||!pkg.matches(name)||process==null||process.length()>1024||!process.matches(name+"(:"+name+")?")||hash==null||!hash.matches("[0-9a-f]{64}")||receiver==null||!receiver.matches("[0-9a-f]{32}")||registration==null||!registration.matches("[0-9a-f]{32}")||uid==null||uid<10000||uid>19999||priority==null)
            throw new IOException("Invalid dynamic receiver identity");
        if(process.indexOf(':')>=0&&!process.startsWith(pkg+":"))throw new IOException("Dynamic private process mismatch");
        return new DynamicResolution(pkg,process,hash,receiver,registration,uid,priority);
    }
    public synchronized java.util.List<DynamicResolution> queryDynamicReceivers(Intent intent,String permission)throws IOException {
        if(closed)throw new IOException("Broker closed");
        String pkg=intent.getPackage(),action=intent.getAction();
        if(intent.getComponent()!=null||action==null||action.isEmpty()||action.length()>4096)throw new IllegalArgumentException("Implicit dynamic receiver query required");
        if(intent.getSelector()!=null)throw new UnsupportedOperationException("Dynamic receiver selector");
        try{
            StringWriter text=new StringWriter();JsonWriter writer=new JsonWriter(text);
            writer.beginObject().name("op").value("query_dynamic_receivers").name("action").value(action).name("has_data").value(intent.getData()!=null);
            if(pkg!=null)writer.name("package").value(pkg);if(permission!=null)writer.name("recipient_permission").value(permission);
            if(intent.getType()!=null)writer.name("mime").value(intent.getType());if(intent.getScheme()!=null)writer.name("scheme").value(intent.getScheme());
            writer.name("categories").beginArray();if(intent.getCategories()!=null)for(String category:intent.getCategories())writer.value(category);writer.endArray().endObject();writer.close();
            byte[] bytes=text.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>65536)throw new IOException("Dynamic query length");
            NativeIpc.send(channel.getFd(),bytes,new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.descriptorCount()!=0||packet.bytes.length>65536)throw new IOException("Dynamic query framing");
                JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                HashSet<String> fields=new HashSet<String>(),owners=new HashSet<String>(),tokens=new HashSet<String>();
                java.util.HashMap<String,String> generations=new java.util.HashMap<String,String>();
                java.util.ArrayList<DynamicResolution> result=new java.util.ArrayList<DynamicResolution>();Boolean ok=null;String error=null;boolean found=false;
                reader.beginObject();while(reader.hasNext()){
                    String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate dynamic query field");
                    if(key.equals("ok"))ok=reader.nextBoolean();else if(key.equals("error"))error=reader.nextString();
                    else if(key.equals("receivers")){found=true;reader.beginArray();while(reader.hasNext()){
                        if(result.size()>=256)throw new IOException("Dynamic query count");DynamicResolution item=readDynamicResolution(reader);
                        if(pkg!=null&&!pkg.equals(item.packageName)||!tokens.add(item.registration)||!owners.add(item.packageName+"/"+item.process+"/"+item.receiver))throw new IOException("Dynamic query target mismatch");
                        String generation=item.uid+":"+item.apkSha256,prior=generations.put(item.packageName,generation);
                        if(prior!=null&&!prior.equals(generation))throw new IOException("Mixed dynamic generations");result.add(item);
                    }reader.endArray();}else throw new IOException("Unknown dynamic query field");
                }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing dynamic query");reader.close();
                if(ok==null)throw new IOException("Missing dynamic result");
                if(!ok){if(error==null||found)throw new IOException("Malformed dynamic query denial");throw new SecurityException("Dynamic query denied: "+error);}
                if(error!=null||!found)throw new IOException("Malformed dynamic query success");return result;
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    synchronized ReceiverConnection openDynamicReceiver(Intent intent,String permission,DynamicResolution target)throws IOException {
        if(closed)throw new IOException("Broker closed");
        if(intent.getComponent()!=null||intent.getAction()==null)throw new IllegalArgumentException("Implicit dynamic broadcast required");
        try{
            StringWriter text=new StringWriter();JsonWriter writer=new JsonWriter(text);
            writer.beginObject().name("op").value("open_dynamic_receiver").name("action").value(intent.getAction())
                .name("registration").value(target.registration).name("expected_sha256").value(target.apkSha256).name("package").value(target.packageName).name("has_data").value(intent.getData()!=null);
            if(intent.getType()!=null)writer.name("mime").value(intent.getType());if(intent.getScheme()!=null)writer.name("scheme").value(intent.getScheme());
            if(permission!=null)writer.name("recipient_permission").value(permission);
            writer.name("categories").beginArray();if(intent.getCategories()!=null)for(String category:intent.getCategories())writer.value(category);writer.endArray().endObject();writer.close();
            byte[] bytes=text.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>65536)throw new IOException("Dynamic admission size");
            NativeIpc.send(channel.getFd(),bytes,new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.bytes.length>65536)throw new IOException("Dynamic admission framing");
                JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                HashSet<String> fields=new HashSet<String>();Boolean ok=null;String error=null,lease=null;DynamicResolution item=null;
                reader.beginObject();while(reader.hasNext()){
                    String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate dynamic admission field");
                    if(key.equals("ok"))ok=reader.nextBoolean();else if(key.equals("error"))error=reader.nextString();else if(key.equals("lease"))lease=reader.nextString();
                    else if(key.equals("receiver"))item=readDynamicResolution(reader);else throw new IOException("Unknown dynamic admission field");
                }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing dynamic admission");reader.close();
                if(ok==null)throw new IOException("Missing dynamic admission result");
                if(!ok){if(error==null||lease!=null||item!=null||packet.descriptorCount()!=0)throw new IOException("Malformed dynamic admission denial");throw new SecurityException("Dynamic admission denied: "+error);}
                if(error!=null||item==null||lease==null||!lease.matches("[0-9a-f]{32}")||packet.descriptorCount()!=1||!item.packageName.equals(target.packageName)||!item.process.equals(target.process)||!item.apkSha256.equals(target.apkSha256)||!item.registration.equals(target.registration)||!item.receiver.equals(target.receiver)||item.uid!=target.uid||item.priority!=target.priority)throw new IOException("Dynamic admission target mismatch");
                return new ReceiverConnection(this,new Reply(new Resolution(item.packageName,item.registration,item.process,item.apkSha256,item.uid),packet.takeDescriptor(0),lease));
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    public synchronized SyncAdapterDiscovery.Declaration[] listSyncAdapters()throws IOException {
        return listSyncAdapters(false);
    }
    synchronized int requestSync(byte[] request)throws IOException {
        if(closed)throw new IOException("Broker closed");
        try {
            NativeIpc.send(channel.getFd(),request,new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())) {
                if(packet.descriptorCount()!=0)throw new IOException("Unexpected sync admission descriptors");
                return SyncRequests.decode(packet.bytes);
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    public synchronized SyncAdapterDiscovery.Declaration[] listSelectedSyncAdapters()throws IOException {
        return listSyncAdapters(true);
    }
    private SyncAdapterDiscovery.Declaration[] listSyncAdapters(boolean selected)throws IOException {
        if(closed)throw new IOException("Broker closed");
        try{
            String request=selected?"{\"op\":\"list_selected_sync_adapters\"}":"{\"op\":\"list_sync_adapters\"}";
            NativeIpc.send(channel.getFd(),request.getBytes(StandardCharsets.UTF_8),new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.descriptorCount()!=0)throw new IOException("Unexpected sync discovery descriptors");
                SyncAdapterDiscovery.Declaration[] result=SyncAdapterDiscovery.decode(packet.bytes);
                if(selected){
                    HashSet<android.content.SyncAdapterType> pairs=new HashSet<android.content.SyncAdapterType>();
                    for(SyncAdapterDiscovery.Declaration item:result)if(!pairs.add(item.type))throw new IOException("Duplicate selected sync owner");
                }
                return result;
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    public synchronized android.accounts.AuthenticatorDescription[] listAuthenticators()throws IOException {
        if(closed)throw new IOException("Broker closed");
        try{
            NativeIpc.send(channel.getFd(),"{\"op\":\"list_authenticators\"}".getBytes(StandardCharsets.UTF_8),new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.descriptorCount()!=0||packet.bytes.length>65536)throw new IOException("Authenticator reply framing");
                JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                HashSet<String> fields=new HashSet<String>(),types=new HashSet<String>();Boolean ok=null;String error=null;boolean found=false;
                java.util.ArrayList<android.accounts.AuthenticatorDescription> result=new java.util.ArrayList<android.accounts.AuthenticatorDescription>();
                reader.beginObject();while(reader.hasNext()){
                    String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate authenticator reply field");
                    if(key.equals("ok"))ok=reader.nextBoolean();else if(key.equals("error"))error=reader.nextString();
                    else if(key.equals("authenticators")){found=true;reader.beginArray();while(reader.hasNext()){
                        if(result.size()>=256)throw new IOException("Authenticator count limit");
                        android.accounts.AuthenticatorDescription item=readAuthenticator(reader).description;
                        if(!types.add(item.type))throw new IOException("Duplicate authenticator account type");result.add(item);
                    }reader.endArray();}else throw new IOException("Unknown authenticator reply field");
                }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing authenticator reply");reader.close();
                if(ok==null)throw new IOException("Missing authenticator result");
                if(!ok){if(error==null||found)throw new IOException("Malformed authenticator denial");throw new SecurityException("Authenticator discovery denied: "+error);}
                if(error!=null||!found)throw new IOException("Malformed authenticator success");
                return result.toArray(new android.accounts.AuthenticatorDescription[result.size()]);
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    /** Authorized target metadata only; no launch or callback capability is issued. */
    public static final class ActivityResolution {
        public final String packageName,requestedComponent,component,process,apkSha256;
        public final int uid,launchMode;
        private ActivityResolution(String pkg,String requested,String component,String process,String hash,int uid,int mode){
            packageName=pkg;requestedComponent=requested;this.component=component;this.process=process;apkSha256=hash;this.uid=uid;launchMode=mode;
        }
    }
    public synchronized ActivityResolution resolveActivity(String pkg,String component)throws IOException{
        if(closed)throw new IOException("Broker closed");
        if(!activityName(pkg)||!activityName(component))throw new IllegalArgumentException("Explicit activity target required");
        StringWriter text=new StringWriter();JsonWriter writer=new JsonWriter(text);
        writer.beginObject().name("op").value("resolve_activity").name("package").value(pkg).name("component").value(component).endObject();writer.close();
        try{
            NativeIpc.send(channel.getFd(),text.toString().getBytes(StandardCharsets.UTF_8),new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.descriptorCount()!=0||packet.bytes.length>65536)throw new IOException("Activity reply framing");
                JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                HashSet<String> fields=new HashSet<String>();Boolean ok=null;String error=null;ActivityResolution result=null;
                reader.beginObject();while(reader.hasNext()){
                    String field=reader.nextName();if(!fields.add(field))throw new IOException("Duplicate activity reply field");
                    if(field.equals("ok")){if(reader.peek()!=JsonToken.BOOLEAN)throw new IOException("Activity status boolean required");ok=reader.nextBoolean();}
                    else if(field.equals("error")){if(reader.peek()!=JsonToken.STRING)throw new IOException("Activity error string required");error=reader.nextString();}
                    else if(field.equals("activity"))result=readActivity(reader);
                    else throw new IOException("Unknown activity reply field");
                }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing activity reply");reader.close();
                if(ok==null)throw new IOException("Missing activity status");
                if(!ok){if(error==null||fields.contains("activity"))throw new IOException("Malformed activity denial");throw new SecurityException("Activity resolution denied");}
                if(error!=null||result==null||!pkg.equals(result.packageName)||!component.equals(result.requestedComponent))throw new IOException("Activity target mismatch");
                return result;
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException failure){try{close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    public static final class ActivityConnection implements Closeable {
        public final ActivityResolution resolution;
        public final IBinder binder;
        private final BinderTransport transport;private final ServiceBrokerClient owner;private final String lease;
        private boolean closed,sent;
        private ActivityConnection(ActivityResolution resolution,BinderTransport transport,ServiceBrokerClient owner,String lease)throws RemoteException{
            this.resolution=resolution;this.transport=transport;this.owner=owner;this.lease=lease;binder=transport.remoteRoot();
        }
        public void send(Intent intent)throws RemoteException{
            synchronized(this){if(closed||sent)throw new IllegalStateException("Activity connection unavailable");sent=true;}
            try{ActivityLaunchEndpoint.send(binder,intent);}catch(RemoteException|RuntimeException failure){close();throw failure;}
        }
        public void close(){
            synchronized(this){if(closed)return;closed=true;}
            try{transport.close();}finally{owner.release(lease,"release_activity");}
        }
    }
    public synchronized ActivityConnection openActivity(ActivityResolution target)throws IOException{
        if(closed)throw new IOException("Broker closed");if(target==null)throw new NullPointerException("target");
        StringWriter text=new StringWriter();JsonWriter writer=new JsonWriter(text);
        writer.beginObject().name("op").value("open_activity").name("package").value(target.packageName).name("component").value(target.requestedComponent)
            .name("expected_uid").value(target.uid).name("expected_sha256").value(target.apkSha256).endObject();writer.close();
        try{
            NativeIpc.send(channel.getFd(),text.toString().getBytes(StandardCharsets.UTF_8),new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.bytes.length>65536)throw new IOException("Activity admission reply limit");
                JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                HashSet<String> fields=new HashSet<String>();Boolean ok=null;String error=null,lease=null;ActivityResolution found=null;
                reader.beginObject();while(reader.hasNext()){
                    String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate activity admission field");
                    if(key.equals("ok")){if(reader.peek()!=JsonToken.BOOLEAN)throw new IOException("Activity status boolean required");ok=reader.nextBoolean();}
                    else if(key.equals("activity"))found=readActivity(reader);
                    else if(key.equals("error")||key.equals("lease")){if(reader.peek()!=JsonToken.STRING)throw new IOException("Activity admission string required");String value=reader.nextString();if(key.equals("error"))error=value;else lease=value;}
                    else throw new IOException("Unknown activity admission field");
                }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing activity admission reply");reader.close();
                if(ok==null)throw new IOException("Missing activity admission status");
                if(!ok){if(error==null||found!=null||lease!=null||packet.descriptorCount()!=0)throw new IOException("Malformed activity admission denial");throw new SecurityException("Activity admission denied");}
                if(error!=null||found==null||lease==null||!lease.matches("[0-9a-f]{32}")||packet.descriptorCount()!=1||found.uid!=target.uid||found.launchMode!=target.launchMode||!found.packageName.equals(target.packageName)||!found.requestedComponent.equals(target.requestedComponent)||!found.component.equals(target.component)||!found.process.equals(target.process)||!found.apkSha256.equals(target.apkSha256))throw new IOException("Activity admission identity mismatch");
                BinderTransport transport=null;
                try{
                    transport=new BinderTransport(packet.takeDescriptor(0).detachFd(),null,found.uid);
                    return new ActivityConnection(found,transport,this,lease);
                }catch(RemoteException failure){if(transport!=null)transport.close();release(lease,"release_activity");throw new IOException("Activity endpoint unavailable",failure);}
                catch(IOException|RuntimeException failure){if(transport!=null)transport.close();release(lease,"release_activity");throw failure;}
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException failure){try{close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    private static boolean activityName(String value){return value!=null&&value.length()<=1024&&value.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");}
    private static ActivityResolution readActivity(JsonReader reader)throws IOException{
        if(reader.peek()!=JsonToken.BEGIN_OBJECT)throw new IOException("Activity object required");
        HashSet<String> fields=new HashSet<String>();String pkg=null,requested=null,component=null,process=null,hash=null;Integer uid=null,mode=null;
        reader.beginObject();while(reader.hasNext()){
            String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate activity identity field");
            if(key.equals("uid"))uid=dynamicInteger(reader);
            else if(key.equals("launch_mode"))mode=dynamicInteger(reader);
            else if(key.equals("package")||key.equals("requested_component")||key.equals("component")||key.equals("process")||key.equals("apk_sha256")){
                if(reader.peek()!=JsonToken.STRING)throw new IOException("Activity identity string required");String value=reader.nextString();
                if(key.equals("package"))pkg=value;else if(key.equals("requested_component"))requested=value;else if(key.equals("component"))component=value;else if(key.equals("process"))process=value;else hash=value;
            }else throw new IOException("Unknown activity identity field");
        }reader.endObject();
        if(fields.size()!=7||!activityName(pkg)||!activityName(requested)||!activityName(component)||process==null||process.length()>1024||hash==null||!hash.matches("[0-9a-f]{64}")||uid==null||uid<10000||uid>19999||mode==null||mode<0||mode>4)throw new IOException("Invalid activity identity");
        boolean processValid=activityName(process)||process.startsWith(":")&&activityName(process.substring(1))||process.startsWith(pkg+":")&&activityName(process.substring(pkg.length()+1));
        if(!processValid)throw new IOException("Invalid activity process");
        return new ActivityResolution(pkg,requested,component,process,hash,uid,mode);
    }
    /** Read/write policy snapshot; obtaining it does not open or start a provider. */
    public static final class ProviderResolution {
        public final Resolution target;
        public final String authority,access;
        private ProviderResolution(Resolution target,String authority,String access){this.target=target;this.authority=authority;this.access=access;}
    }
    public synchronized ProviderResolution resolveProvider(String authority,String access)throws IOException{
        if(closed)throw new IOException("Broker closed");
        if(authority==null||authority.length()>1024||!authority.matches("[A-Za-z0-9_.-]+")||!("read".equals(access)||"write".equals(access)))throw new IllegalArgumentException("Provider authority and read/write access required");
        StringWriter text=new StringWriter();JsonWriter writer=new JsonWriter(text);
        writer.beginObject().name("op").value("resolve_provider").name("authority").value(authority).name("access").value(access).endObject();writer.close();
        try{
            NativeIpc.send(channel.getFd(),text.toString().getBytes(StandardCharsets.UTF_8),new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.descriptorCount()!=0||packet.bytes.length>65536)throw new IOException("Provider resolution framing");
                try(JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()))){
                    HashSet<String> fields=new HashSet<String>();Boolean ok=null;String error=null;ProviderResolution result=null;
                    reader.beginObject();while(reader.hasNext()){
                        String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate provider reply field");
                        if(key.equals("ok"))ok=reader.nextBoolean();
                        else if(key.equals("error")){if(reader.peek()!=JsonToken.STRING)throw new IOException("Provider error string required");error=reader.nextString();}
                        else if(key.equals("provider"))result=readProviderResolution(reader);
                        else throw new IOException("Unknown provider reply field");
                    }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing provider reply");
                    if(ok==null)throw new IOException("Missing provider reply status");
                    if(!ok){if(error==null||result!=null)throw new IOException("Malformed provider denial");throw new SecurityException("Provider resolution denied");}
                    if(error!=null||result==null||!authority.equals(result.authority)||!access.equals(result.access))throw new IOException("Provider resolution mismatch");
                    return result;
                }
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    private static ProviderResolution readProviderResolution(JsonReader reader)throws IOException{
        HashSet<String> fields=new HashSet<String>();String pkg=null,component=null,process=null,hash=null,authority=null,access=null;Integer uid=null;
        reader.beginObject();while(reader.hasNext()){
            String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate provider identity field");
            if(key.equals("uid")){uid=dynamicInteger(reader);continue;}
            if(reader.peek()!=JsonToken.STRING)throw new IOException("Provider identity string required");
            String value=reader.nextString();if(value.length()>1024)throw new IOException("Provider identity length limit");
            switch(key){case "package":pkg=value;break;case "component":component=value;break;case "process":process=value;break;
                case "apk_sha256":hash=value;break;case "authority":authority=value;break;case "access":access=value;break;
                default:throw new IOException("Unknown provider identity field");}
        }reader.endObject();
        if(fields.size()!=7||!activityName(pkg)||!activityName(component)||process==null||hash==null||!hash.matches("[0-9a-f]{64}")||uid==null||uid<10000||uid>19999||authority==null||!authority.matches("[A-Za-z0-9_.-]+")||!("read".equals(access)||"write".equals(access)))throw new IOException("Invalid provider identity");
        if(!(activityName(process)||process.startsWith(":")&&activityName(process.substring(1))||process.startsWith(pkg+":")&&activityName(process.substring(pkg.length()+1))))throw new IOException("Invalid provider process");
        return new ProviderResolution(new Resolution(pkg,component,process,hash,uid),authority,access);
    }
    /** Own-package policy snapshot; never a durable dispatch capability. */
    public synchronized Resolution resolveJob(String pkg,String component)throws IOException {
        if(closed)throw new IOException("Broker closed");
        if(!activityName(pkg)||!activityName(component))throw new IllegalArgumentException("Invalid job identity");
        StringWriter text=new StringWriter();JsonWriter writer=new JsonWriter(text);
        writer.beginObject().name("op").value("resolve_job").name("component").value(component).endObject();writer.close();
        try{
            NativeIpc.send(channel.getFd(),text.toString().getBytes(StandardCharsets.UTF_8),new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.descriptorCount()!=0||packet.bytes.length>65536)throw new IOException("Job resolution framing");
                JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                HashSet<String> fields=new HashSet<String>();Boolean ok=null;String error=null;Resolution result=null;
                reader.beginObject();while(reader.hasNext()){
                    String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate job resolution field");
                    if(key.equals("ok")){if(reader.peek()!=JsonToken.BOOLEAN)throw new IOException("Job status boolean required");ok=reader.nextBoolean();}
                    else if(key.equals("error")){if(reader.peek()!=JsonToken.STRING)throw new IOException("Job error string required");error=reader.nextString();}
                    else if(key.equals("job"))result=readJobResolution(reader);
                    else throw new IOException("Unknown job resolution field");
                }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing job resolution");reader.close();
                if(ok==null)throw new IOException("Missing job status");
                if(!ok){if(error==null||result!=null)throw new IOException("Malformed job denial");throw new SecurityException("Job service denied");}
                if(error!=null||result==null||!pkg.equals(result.packageName)||!component.equals(result.component))throw new IOException("Job identity mismatch");
                return result;
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException failure){try{close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw new IOException("Invalid job resolution reply",failure);}
    }
    private static Resolution readJobResolution(JsonReader reader)throws IOException {
        HashSet<String> fields=new HashSet<String>();String pkg=null,component=null,process=null,hash=null;Integer uid=null;
        reader.beginObject();while(reader.hasNext()){
            String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate job identity field");
            if(key.equals("uid"))uid=dynamicInteger(reader);
            else if(key.equals("package")||key.equals("component")||key.equals("process")||key.equals("apk_sha256")){
                if(reader.peek()!=JsonToken.STRING)throw new IOException("Job identity string required");String value=reader.nextString();
                if(key.equals("package"))pkg=value;else if(key.equals("component"))component=value;else if(key.equals("process"))process=value;else hash=value;
            }else throw new IOException("Unknown job identity field");
        }reader.endObject();
        if(fields.size()!=5||!activityName(pkg)||!activityName(component)||process==null||process.length()>1024||hash==null||!hash.matches("[0-9a-f]{64}")||uid==null||uid<10000||uid>19999)throw new IOException("Invalid job identity");
        if(!(activityName(process)||process.startsWith(":")&&activityName(process.substring(1))||process.startsWith(pkg+":")&&activityName(process.substring(pkg.length()+1))))throw new IOException("Invalid job process");
        return new Resolution(pkg,component,process,hash,uid);
    }
    public static final class AuthenticatorResolution {
        public final android.accounts.AuthenticatorDescription description;
        public final String component,process,apkSha256;
        public final int uid;
        private boolean addAccountOnly;
        private AuthenticatorResolution(android.accounts.AuthenticatorDescription description,String component,String process,String hash,int uid){
            this.description=description;this.component=component;this.process=process;this.apkSha256=hash;this.uid=uid;
        }
    }
    public synchronized AuthenticatorResolution resolveAuthenticator(String accountType)throws IOException {
        return resolveAuthenticator(accountType,false);
    }
    public synchronized AuthenticatorResolution resolveAddAccount(String accountType)throws IOException {
        return resolveAuthenticator(accountType,true);
    }
    private AuthenticatorResolution resolveAuthenticator(String accountType,boolean addOnly)throws IOException {
        if(closed)throw new IOException("Broker closed");
        if(accountType==null||accountType.isEmpty()||accountType.length()>1024||accountType.indexOf(0)>=0)throw new IllegalArgumentException("Account type required");
        StringWriter text=new StringWriter();JsonWriter writer=new JsonWriter(text);
        writer.beginObject().name("op").value(addOnly?"resolve_add_account":"resolve_authenticator").name("type").value(accountType).endObject();writer.close();
        try{
            NativeIpc.send(channel.getFd(),text.toString().getBytes(StandardCharsets.UTF_8),new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.descriptorCount()!=0||packet.bytes.length>65536)throw new IOException("Authenticator resolution framing");
                JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                HashSet<String> fields=new HashSet<String>();Boolean ok=null;String error=null;AuthenticatorResolution result=null;
                reader.beginObject();while(reader.hasNext()){
                    String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate authenticator resolution field");
                    if(key.equals("ok"))ok=reader.nextBoolean();else if(key.equals("error"))error=reader.nextString();
                    else if(key.equals("authenticator"))result=readAuthenticator(reader);else throw new IOException("Unknown authenticator resolution field");
                }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing authenticator resolution");reader.close();
                if(ok==null)throw new IOException("Missing authenticator resolution status");
                if(!ok){if(error==null||result!=null)throw new IOException("Malformed authenticator resolution denial");throw new SecurityException("Authenticator resolution denied");}
                if(error!=null||result==null||!accountType.equals(result.description.type))throw new IOException("Invalid authenticator resolution result");
                result.addAccountOnly=addOnly;
                return result;
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw new IOException("Invalid authenticator resolution reply",error);}
    }
    private static AuthenticatorResolution readAuthenticator(JsonReader reader)throws IOException {
        HashSet<String> fields=new HashSet<String>();String type=null,pkg=null,component=null,process=null,hash=null;Integer uid=null,label=null,icon=null,small=null,preferences=null;Boolean custom=null;
        reader.beginObject();while(reader.hasNext()){
            String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate authenticator field");
            if((key.equals("type")||key.equals("package")||key.equals("component")||key.equals("process")||key.equals("apk_sha256"))&&reader.peek()!=JsonToken.STRING)throw new IOException("Authenticator identity string required");
            if(key.equals("type"))type=reader.nextString();else if(key.equals("package"))pkg=reader.nextString();else if(key.equals("component"))component=reader.nextString();
            else if(key.equals("process"))process=reader.nextString();else if(key.equals("apk_sha256"))hash=reader.nextString();else if(key.equals("uid"))uid=dynamicInteger(reader);
            else if(key.equals("custom_tokens"))custom=reader.nextBoolean();else if(key.equals("label"))label=resourceInteger(reader);else if(key.equals("icon"))icon=resourceInteger(reader);
            else if(key.equals("smallIcon"))small=resourceInteger(reader);else if(key.equals("accountPreferences"))preferences=resourceInteger(reader);else throw new IOException("Unknown authenticator field");
        }reader.endObject();String name="[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*";
        if(type==null||type.isEmpty()||type.length()>1024||type.indexOf(0)>=0||pkg==null||pkg.length()>1024||!pkg.matches(name)||component==null||component.length()>1024||!component.matches(name)||process==null||process.length()>1024||!(process.matches(name)||process.matches(":"+name)||process.matches(java.util.regex.Pattern.quote(pkg)+":"+name))||hash==null||!hash.matches("[0-9a-f]{64}")||uid==null||uid<10000||uid>19999||label==null||icon==null||small==null||preferences==null||custom==null)throw new IOException("Invalid authenticator identity");
        return new AuthenticatorResolution(new android.accounts.AuthenticatorDescription(type,pkg,label,icon,small,preferences,custom),component,process,hash,uid);
    }
    private static int resourceInteger(JsonReader reader)throws IOException {
        if(reader.peek()!=JsonToken.NUMBER)throw new IOException("Resource ID type");String text=reader.nextString();
        if(!text.matches("0|[1-9][0-9]*"))throw new IOException("Resource ID syntax");
        try{long value=Long.parseLong(text);if(value>0xffffffffL)throw new IOException("Resource ID range");return (int)value;}
        catch(NumberFormatException error){throw new IOException("Resource ID range",error);}
    }
    /** Owned receive-only invalidation stream; close wakes a blocked reader. */
    public static final class AccountWatch implements Closeable {
        public final String initialRevision;
        private final ServiceBrokerClient owner;private final String lease;
        private final ParcelFileDescriptor endpoint;private final Object reads=new Object();private boolean closed;
        private AccountWatch(ServiceBrokerClient owner,String lease,String revision,ParcelFileDescriptor endpoint){this.owner=owner;this.lease=lease;initialRevision=revision;this.endpoint=endpoint;}
        public String readRevision()throws IOException{
            synchronized(reads){
                ParcelFileDescriptor input;synchronized(this){if(closed)throw new IOException("Account watch closed");input=endpoint.dup();}
                try(ParcelFileDescriptor borrowed=input;NativeIpc.Packet packet=NativeIpc.receive(borrowed.getFd())){
                    if(packet.descriptorCount()!=0||packet.bytes.length>256)throw new IOException("Account notice framing");
                    JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                    reader.beginObject();
                    if(!reader.hasNext()||!reader.nextName().equals("revision")||reader.peek()!=JsonToken.STRING)throw new IOException("Account revision notice required");
                    String revision=reader.nextString();
                    if(!revision.matches("[0-9a-f]{64}")||reader.hasNext())throw new IOException("Invalid account revision notice");
                    reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing account notice");reader.close();return revision;
                }catch(IOException failure){close();throw failure;}
                catch(RuntimeException failure){close();throw new IOException("Malformed account revision notice",failure);}
            }
        }
        public void close(){
            synchronized(this){if(closed)return;closed=true;NativeIpc.shutdown(endpoint.getFd());try{endpoint.close();}catch(IOException failure){System.err.println("SHIRAZ_ACCOUNT_WATCH_CLOSE_FAILED");}
                owner.release(lease,"unwatch_accounts");}
        }
    }
    public synchronized AccountWatch watchAccounts()throws IOException{
        if(closed)throw new IOException("Broker closed");
        try{
            NativeIpc.send(channel.getFd(),"{\"op\":\"watch_accounts\"}".getBytes(StandardCharsets.UTF_8),new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.bytes.length>65536)throw new IOException("Account watch reply limit");
                JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                HashSet<String> fields=new HashSet<String>();Boolean ok=null;String error=null,lease=null,revision=null;
                reader.beginObject();while(reader.hasNext()){
                    String field=reader.nextName();if(!fields.add(field))throw new IOException("Duplicate account watch field");
                    if(field.equals("ok")){if(reader.peek()!=JsonToken.BOOLEAN)throw new IOException("Account watch status required");ok=reader.nextBoolean();}
                    else if(field.equals("error")||field.equals("lease")||field.equals("revision")){
                        if(reader.peek()!=JsonToken.STRING)throw new IOException("Account watch string required");String value=reader.nextString();
                        if(field.equals("error"))error=value;else if(field.equals("lease"))lease=value;else revision=value;
                    }else throw new IOException("Unknown account watch field");
                }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing account watch reply");reader.close();
                if(ok==null)throw new IOException("Missing account watch status");
                if(!ok){if(error==null||lease!=null||revision!=null||packet.descriptorCount()!=0)throw new IOException("Malformed account watch denial");throw new SecurityException("Account watch denied");}
                if(error!=null||lease==null||!lease.matches("[0-9a-f]{32}")||revision==null||!revision.matches("[0-9a-f]{64}")||packet.descriptorCount()!=1)throw new IOException("Invalid account watch admission");
                return new AccountWatch(this,lease,revision,packet.takeDescriptor(0));
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException failure){try{close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw new IOException("Account watch admission failed",failure);}
    }
    /** A bounded, owned snapshot of the caller-visible account set. */
    public static final class AccountSnapshot {
        public final String revision;
        private final android.accounts.Account[] accounts;
        private AccountSnapshot(String revision,android.accounts.Account[] accounts){this.revision=revision;this.accounts=accounts;}
        public android.accounts.Account[] accounts(){return accounts.clone();}
    }
    public AccountSnapshot accountSnapshot()throws IOException{return (AccountSnapshot)accountOperation("snapshot",null,null,null,null);}
    private static android.accounts.Account[] readAccounts(JsonReader reader,String filter)throws IOException{
        if(reader.peek()!=JsonToken.BEGIN_ARRAY)throw new IOException("Account list required");
        java.util.ArrayList<android.accounts.Account> accounts=new java.util.ArrayList<android.accounts.Account>();HashSet<String> names=new HashSet<String>();
        reader.beginArray();while(reader.hasNext()){
            if(accounts.size()>=1024)throw new IOException("Account count limit");String accountName=null,accountType=null;HashSet<String> accountFields=new HashSet<String>();
            if(reader.peek()!=JsonToken.BEGIN_OBJECT)throw new IOException("Account identity object required");
            reader.beginObject();while(reader.hasNext()){
                String field=reader.nextName();if(!accountFields.add(field))throw new IOException("Duplicate account field");
                if(reader.peek()!=JsonToken.STRING)throw new IOException("Account identity string required");
                if(field.equals("name"))accountName=reader.nextString();else if(field.equals("type"))accountType=reader.nextString();else throw new IOException("Unknown account field");
            }reader.endObject();
            if(accountName==null||accountType==null||accountName.isEmpty()||accountType.isEmpty()||accountName.length()>1024||accountType.length()>1024||accountName.indexOf(0)>=0||accountType.indexOf(0)>=0||filter!=null&&!filter.equals(accountType)||!names.add(accountType+"\0"+accountName))throw new IOException("Invalid account list identity");
            accounts.add(new android.accounts.Account(accountName,accountType));
        }reader.endArray();return accounts.toArray(new android.accounts.Account[accounts.size()]);
    }
    private static AccountSnapshot readAccountSnapshot(JsonReader reader)throws IOException{
        if(reader.peek()!=JsonToken.BEGIN_OBJECT)throw new IOException("Account snapshot object required");
        String revision=null;android.accounts.Account[] accounts=null;HashSet<String> fields=new HashSet<String>();
        reader.beginObject();while(reader.hasNext()){
            String name=reader.nextName();if(!fields.add(name))throw new IOException("Duplicate account snapshot field");
            if(name.equals("revision")){if(reader.peek()!=JsonToken.STRING)throw new IOException("Account snapshot revision string required");revision=reader.nextString();}
            else if(name.equals("accounts"))accounts=readAccounts(reader,null);
            else throw new IOException("Unknown account snapshot field");
        }reader.endObject();
        if(revision==null||!revision.matches("[0-9a-f]{64}")||accounts==null)throw new IOException("Invalid account snapshot");
        return new AccountSnapshot(revision,accounts);
    }
    public static final class TokenPreparation {
        public final int ownerUid;public final String ownerSha256,revision,token;public final boolean customTokens;public final Long expiresMs;
        private TokenPreparation(int uid,String hash,String revision,boolean custom,String token,Long expiry){ownerUid=uid;ownerSha256=hash;this.revision=revision;customTokens=custom;this.token=token;expiresMs=expiry;}
    }
    public TokenPreparation prepareToken(android.accounts.Account account,String tokenType)throws IOException{
        return (TokenPreparation)accountOperation("prepare_token",account,tokenType,null,null,null);
    }
    public boolean commitToken(android.accounts.Account account,String tokenType,String token,TokenPreparation prepared)throws IOException{
        if(prepared==null)throw new IllegalArgumentException("Token preparation required");
        return (Boolean)accountOperation("commit_token",account,tokenType,token,null,prepared);
    }
    public TokenPreparation prepareCustomToken(android.accounts.Account account,String tokenType)throws IOException{
        return (TokenPreparation)accountOperation("prepare_custom_token",account,tokenType,null,null,null);
    }
    public boolean commitCustomToken(android.accounts.Account account,String tokenType,String token,TokenPreparation prepared,long expiresMs)throws IOException{
        if(prepared==null||!prepared.customTokens||expiresMs<0)throw new IllegalArgumentException("Custom token preparation and expiry required");
        return (Boolean)accountOperation("commit_custom_token",account,tokenType,token,null,prepared,expiresMs);
    }
    private static TokenPreparation readTokenPreparation(JsonReader reader,boolean customPath)throws IOException{
        if(reader.peek()!=JsonToken.BEGIN_OBJECT)throw new IOException("Token preparation object required");
        HashSet<String> fields=new HashSet<String>();Integer uid=null;String hash=null,revision=null,token=null;Boolean custom=null;Long expiry=null;
        reader.beginObject();while(reader.hasNext()){
            String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate token preparation field");
            if(key.equals("owner_uid"))uid=dynamicInteger(reader);
            else if(customPath&&key.equals("expires_ms")){
                if(reader.peek()==JsonToken.NULL){reader.nextNull();continue;}
                if(reader.peek()!=JsonToken.NUMBER)throw new IOException("Custom token expiry number required");
                String value=reader.nextString();if(!value.matches("0|[1-9][0-9]*"))throw new IOException("Invalid custom token expiry");
                try{expiry=Long.valueOf(value);}catch(NumberFormatException failure){throw new IOException("Custom token expiry out of range");}
            }
            else if(key.equals("custom_tokens")){if(reader.peek()!=JsonToken.BOOLEAN)throw new IOException("Token mode boolean required");custom=reader.nextBoolean();}
            else if(key.equals("owner_sha256")||key.equals("revision")||key.equals("token")){
                if(key.equals("token")&&reader.peek()==JsonToken.NULL){reader.nextNull();continue;}
                if(reader.peek()!=JsonToken.STRING)throw new IOException("Token preparation string required");String value=reader.nextString();
                if(key.equals("owner_sha256"))hash=value;else if(key.equals("revision"))revision=value;else token=value;
            }else throw new IOException("Unknown token preparation field");
        }reader.endObject();
        if(fields.size()!=(customPath?6:5)||uid==null||uid<10000||uid>19999||hash==null||!hash.matches("[0-9a-f]{64}")||revision==null||!revision.matches("[0-9a-f]{32}")||custom==null||token!=null&&(token.length()>16384||token.indexOf(0)>=0||(!customPath&&custom)))throw new IOException("Invalid token preparation");
        if(customPath&&((token==null)!=(expiry==null)||token!=null&&(!custom||token.isEmpty())))throw new IOException("Invalid custom token cache result");
        return new TokenPreparation(uid,hash,revision,custom,token,expiry);
    }
    public Object accountOperation(String action,android.accounts.Account account,String key,String value,android.os.Bundle extras)throws IOException{
        return accountOperation(action,account,key,value,extras,null);
    }
    private Object accountOperation(String action,android.accounts.Account account,String key,String value,android.os.Bundle extras,TokenPreparation expected)throws IOException {
        return accountOperation(action,account,key,value,extras,expected,null);
    }
    private synchronized Object accountOperation(String action,android.accounts.Account account,String key,String value,android.os.Bundle extras,TokenPreparation expected,Long expiry)throws IOException {
        if(closed)throw new IOException("Broker closed");
        if(action==null||account==null&&!action.equals("list")&&!action.equals("snapshot"))throw new IllegalArgumentException("Account operation required");
        boolean snapshot=action.equals("snapshot");
        if(snapshot&&(account!=null||key!=null||value!=null||extras!=null))throw new IllegalArgumentException("Account snapshot takes no selectors");
        boolean customPath=action.equals("prepare_custom_token")||action.equals("commit_custom_token");
        boolean prepare=action.equals("prepare_token")||action.equals("prepare_custom_token"),commit=action.equals("commit_token")||action.equals("commit_custom_token"),list=action.equals("list"),invalidate=action.equals("invalidate"),add=action.equals("add"),remove=action.equals("remove"),get=action.equals("get_password")||action.equals("get_data")||action.equals("get_token"),set=action.equals("set_password")||action.equals("set_data")||action.equals("set_token");
        if(!snapshot&&!prepare&&!commit&&!list&&!invalidate&&!add&&!remove&&!get&&!set)throw new IllegalArgumentException("Unknown account operation");
        if(customPath&&commit&&expiry==null)throw new IllegalArgumentException("Custom token expiry required");
        if(commit&&expected==null)throw new IllegalArgumentException("Token preparation required");
        StringWriter text=new StringWriter();JsonWriter writer=new JsonWriter(text);
        writer.beginObject().name("op").value("account_operation").name("request").beginObject().name("action").value(action);
        if(!snapshot)writer.name("type").value(list?key:account.type);
        if(!snapshot&&!list&&!invalidate)writer.name("name").value(account.name);
        if(action.endsWith("_data")||action.endsWith("_token"))writer.name("key").value(key);
        if(add){writer.name("password").value(value).name("extras").beginObject();if(extras!=null)for(String name:extras.keySet()){
            Object item=extras.get(name);if(item!=null&&!(item instanceof String))throw new IllegalArgumentException("Account extras must be strings");writer.name(name).value((String)item);
        }writer.endObject();}else if(set||invalidate||commit)writer.name("value").value(value);
        if(commit)writer.name("expected_uid").value(expected.ownerUid).name("expected_sha256").value(expected.ownerSha256).name("revision").value(expected.revision);
        if(customPath&&commit)writer.name("expires_ms").value(expiry);
        writer.endObject().endObject();writer.close();byte[] bytes=text.toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>65536)throw new IllegalArgumentException("Account request size limit");
        try{
            NativeIpc.send(channel.getFd(),bytes,new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.descriptorCount()!=0||packet.bytes.length>65536)throw new IOException("Account reply framing");
                JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                HashSet<String> fields=new HashSet<String>();Boolean ok=null;String error=null;Object result=null;boolean found=false;
                reader.beginObject();while(reader.hasNext()){
                    String name=reader.nextName();if(!fields.add(name))throw new IOException("Duplicate account reply field");
                    if(name.equals("ok"))ok=reader.nextBoolean();else if(name.equals("error"))error=reader.nextString();else if(name.equals("result")){
                        found=true;JsonToken token=reader.peek();
                        if(prepare)result=readTokenPreparation(reader,customPath);
                        else if(snapshot)result=readAccountSnapshot(reader);
                        else if(list)result=readAccounts(reader,key);
                        else if(add||remove||commit){if(token!=JsonToken.BOOLEAN)throw new IOException("Account boolean result required");result=Boolean.valueOf(reader.nextBoolean());}
                        else if(token==JsonToken.NULL)reader.nextNull();
                        else if(get&&token==JsonToken.STRING){String item=reader.nextString();if(item.length()>16384)throw new IOException("Account value limit");result=item;}
                        else throw new IOException("Invalid account result type");
                    }else throw new IOException("Unknown account reply field");
                }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing account reply");reader.close();
                if(ok==null)throw new IOException("Missing account result");
                if(!ok){if(error==null||found)throw new IOException("Malformed account denial");throw new SecurityException("Account operation denied");}
                if(error!=null||!found)throw new IOException("Malformed account success");return result;
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    public Resolution resolveAction(String pkg,String action)throws IOException{return request(pkg,action,false,false,true).resolution;}
    /** Resolve only the supported package/action form; never mutate the caller's intent. */
    public Intent explicitServiceIntent(Intent original)throws IOException {
        if(original==null)throw new IllegalArgumentException("Service intent required");
        Intent intent=original.shirazBroadcastCopy();
        if(intent.getComponent()!=null){
            if(intent.getPackage()!=null&&!intent.getPackage().equals(intent.getComponent().getPackageName()))throw new IllegalArgumentException("Service package mismatch");
            return intent;
        }
        if(intent.getPackage()==null||intent.getAction()==null)throw new IllegalArgumentException("Service component or package/action required");
        if(intent.getSelector()!=null)throw new UnsupportedOperationException("Service intent selector resolution");
        Resolution result=request(intent.getPackage(),intent.getAction(),false,false,true,intent).resolution;
        intent.setComponent(new ComponentName(result.packageName,result.component));
        return intent;
    }
    /** Opens the authorized target process, then performs its actual onBind handshake. */
    public Connection bind(Intent intent)throws Exception {
        intent=explicitServiceIntent(intent);
        ComponentName name=intent.getComponent();Reply reply=request(name.getPackageName(),name.getClassName(),true);
        BinderTransport transport;
        try(ParcelFileDescriptor endpoint=reply.endpoint){transport=new BinderTransport(endpoint.detachFd(),null,reply.resolution.uid);}
        catch(Exception|Error error){release(reply.lease);throw error;}
        Parcel input=Parcel.obtain(),output=Parcel.obtain();
        try{
            input.writeInterfaceToken("shiraz.service.control");input.writeTypedObject(intent,0);
            if(!transport.remoteRoot().transact(1,input,output,0))throw new IOException("Service bind handshake unsupported");
            output.readException();IBinder binder=output.readStrongBinder();output.enforceNoDataAvail();
            return new Connection(reply.resolution,binder,transport,this,reply.lease);
        }catch(Exception|Error error){transport.close();release(reply.lease);throw error;}
        finally{input.recycle();output.recycle();}
    }
    /** Admission lease protects command delivery; worker started state then owns the lifetime. */
    public ComponentName start(Intent original,boolean foreground)throws Exception {
        Intent intent=explicitServiceIntent(original);ComponentName name=intent.getComponent();
        Reply reply=request(name.getPackageName(),name.getClassName(),true,false,false,null,false,null,null,null,true);
        BinderTransport transport=null;Parcel input=Parcel.obtain(),output=Parcel.obtain();
        try(ParcelFileDescriptor endpoint=reply.endpoint){
            transport=new BinderTransport(endpoint.detachFd(),null,reply.resolution.uid);
            input.writeInterfaceToken("shiraz.service.control");input.writeTypedObject(intent,0);
            if(!transport.remoteRoot().transact(foreground?3:2,input,output,0))throw new IOException("Service start handshake unsupported");
            output.readException();if(output.readInt()!=1)throw new IOException("Service start not accepted");output.enforceNoDataAvail();return name;
        }finally{input.recycle();output.recycle();if(transport!=null)transport.close();release(reply.lease);}
    }
    public boolean stop(Intent original)throws Exception {
        Intent intent=explicitServiceIntent(original);ComponentName name=intent.getComponent();
        Reply reply=request(name.getPackageName(),name.getClassName(),true,true);
        if(reply.endpoint==null)return reply.restartCancelled;
        BinderTransport transport=null;Parcel input=Parcel.obtain(),output=Parcel.obtain();
        try(ParcelFileDescriptor endpoint=reply.endpoint){
            transport=new BinderTransport(endpoint.detachFd(),null,reply.resolution.uid);
            input.writeInterfaceToken("shiraz.service.control");input.writeTypedObject(intent,0);
            if(!transport.remoteRoot().transact(4,input,output,0))throw new IOException("Service stop handshake unsupported");
            output.readException();int result=output.readInt();if(result!=0&&result!=1)throw new IOException("Invalid service stop result");output.enforceNoDataAvail();return result==1;
        }finally{input.recycle();output.recycle();if(transport!=null)transport.close();release(reply.lease);}
    }
    private Reply request(String pkg,String component,boolean open)throws IOException{return request(pkg,component,open,false);}
    private Reply request(String pkg,String component,boolean open,boolean existing)throws IOException{return request(pkg,component,open,existing,false);}
    private Reply request(String pkg,String component,boolean open,boolean existing,boolean action)throws IOException{return request(pkg,component,open,existing,action,null);}
    private Reply request(String pkg,String component,boolean open,boolean existing,boolean action,Intent filterIntent)throws IOException{return request(pkg,component,open,existing,action,filterIntent,false,null);}
    private Reply request(String pkg,String component,boolean open,boolean existing,boolean action,Intent filterIntent,boolean receiver,String recipientPermission)throws IOException{return request(pkg,component,open,existing,action,filterIntent,receiver,recipientPermission,null);}
    public Connection openAuthenticator(AuthenticatorResolution target)throws IOException {
        if(target==null)throw new IllegalArgumentException("Authenticator target required");
        Reply reply=request(target.description.packageName,target.component,true,false,false,null,false,null,target.apkSha256,target);
        BinderTransport transport=null;
        try(ParcelFileDescriptor endpoint=reply.endpoint){
            transport=new BinderTransport(endpoint.detachFd(),null,reply.resolution.uid);
            return new Connection(reply.resolution,transport.remoteRoot(),transport,this,reply.lease);
        }catch(IOException|android.os.RemoteException|RuntimeException|Error failure){
            if(transport!=null)transport.close();release(reply.lease);
            if(failure instanceof IOException)throw (IOException)failure;
            if(failure instanceof RuntimeException)throw (RuntimeException)failure;
            if(failure instanceof Error)throw (Error)failure;
            throw new IOException("Authenticator transport unavailable",failure);
        }
    }
    private Reply request(String pkg,String component,boolean open,boolean existing,boolean action,Intent filterIntent,boolean receiver,String recipientPermission,String expectedHash)throws IOException{
        return request(pkg,component,open,existing,action,filterIntent,receiver,recipientPermission,expectedHash,null);
    }
    private synchronized Reply request(String pkg,String component,boolean open,boolean existing,boolean action,Intent filterIntent,boolean receiver,String recipientPermission,String expectedHash,AuthenticatorResolution authenticator) throws IOException {
        return request(pkg,component,open,existing,action,filterIntent,receiver,recipientPermission,expectedHash,authenticator,false);
    }
    private synchronized Reply request(String pkg,String component,boolean open,boolean existing,boolean action,Intent filterIntent,boolean receiver,String recipientPermission,String expectedHash,AuthenticatorResolution authenticator,boolean start) throws IOException {
        if(closed)throw new IOException("Service broker closed");
        if(pkg==null||component==null||pkg.length()<1||component.length()<1||pkg.length()>1024||component.length()>1024)
            throw new IllegalArgumentException("Service identity length");
        try {
            StringWriter text=new StringWriter();JsonWriter writer=new JsonWriter(text);
            if(authenticator!=null)writer.beginObject().name("op").value(authenticator.addAccountOnly?"open_add_account":"open_authenticator").name("type").value(authenticator.description.type).name("expected_uid").value(authenticator.uid);
            else writer.beginObject().name("op").value(receiver?(open?"open_receiver":"resolve_receiver"):action?"resolve_service_action":existing?"open_service_for_stop":start?"open_service_for_start":open?"open_service":"resolve_service").name("package").value(pkg).name(action?"action":"component").value(component);
            if(recipientPermission!=null)writer.name("recipient_permission").value(recipientPermission);
            if(expectedHash!=null)writer.name("expected_sha256").value(expectedHash);
            if(filterIntent!=null){
                writer.name("has_data").value(filterIntent.getData()!=null);
                if(filterIntent.getScheme()!=null)writer.name("scheme").value(filterIntent.getScheme());
                if(filterIntent.getType()!=null)writer.name("mime").value(filterIntent.getType());
                writer.name("categories").beginArray();
                if(filterIntent.getCategories()!=null)for(String category:filterIntent.getCategories())writer.value(category);
                writer.endArray();
            }
            writer.endObject();writer.close();
            byte[] request=text.toString().getBytes(StandardCharsets.UTF_8);
            if(request.length>65536)throw new IOException("Broker request length");
            NativeIpc.send(channel.getFd(),request,new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
            if(packet.bytes.length<1||packet.bytes.length>65536)throw new IOException("Broker reply length");
            byte[] data=packet.bytes;
            JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(data)).toString()));
            HashSet<String> fields=new HashSet<String>();Boolean ok=null,absent=null,filtered=null,restartCancelled=null,unavailable=null;String error=null,lease=null;Resolution result=null;
            reader.beginObject();while(reader.hasNext()){
                String name=reader.nextName();if(!fields.add(name))throw new IOException("Duplicate broker reply field");
                if(name.equals("ok"))ok=reader.nextBoolean();
                else if(name.equals("error"))error=reader.nextString();
                else if(name.equals(receiver?"receiver":"service"))result=readResolution(reader);
                else if(name.equals("lease"))lease=reader.nextString();
                else if(name.equals("absent"))absent=reader.nextBoolean();
                else if(name.equals("restart_cancelled"))restartCancelled=reader.nextBoolean();
                else if(name.equals("filtered"))filtered=reader.nextBoolean();
                else if(name.equals("unavailable"))unavailable=reader.nextBoolean();
                else throw new IOException("Unknown broker reply field");
            }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing broker reply");reader.close();
            if(ok==null)throw new IOException("Missing broker result");
            if(unavailable!=null&&(ok||!unavailable||(open&&!start&&!existing)||receiver||authenticator!=null||filtered!=null))throw new IOException("Invalid unavailable service result");
            if(filtered!=null&&(!receiver||!open||ok||!filtered))throw new IOException("Invalid receiver filter result");
            if(restartCancelled!=null&&(!ok||!existing||!Boolean.TRUE.equals(absent)||!restartCancelled||lease!=null||packet.descriptorCount()!=0))throw new IOException("Invalid restart cancellation reply");
            if(!ok){
                if(error==null||result!=null||lease!=null||absent!=null||packet.descriptorCount()!=0)throw new IOException("Invalid broker denial");
                if(filtered!=null)throw new ReceiverFilteredException(error);
                if(unavailable!=null)throw new ServiceUnavailableException(error);
                throw new SecurityException("Service broker denied resolution: "+error);
            }
            if(error!=null||result==null||!pkg.equals(result.packageName)||expectedHash!=null&&!expectedHash.equals(result.apkSha256)||(!action&&!component.equals(result.component)))throw new IOException("Broker target mismatch");
            if(authenticator!=null&&(result.uid!=authenticator.uid||!result.process.equals(authenticator.process)))throw new IOException("Authenticator admission identity changed");
            if(absent!=null){if(!existing||!absent||lease!=null||packet.descriptorCount()!=0)throw new IOException("Invalid absent service reply");return new Reply(result,null,null,Boolean.TRUE.equals(restartCancelled));}
            if(packet.descriptorCount()!=(open?1:0))throw new IOException("Broker endpoint count");
            if(open?(lease==null||!lease.matches("[0-9a-f]{32}")):lease!=null)throw new IOException("Invalid broker binding lease");
            return new Reply(result,open?packet.takeDescriptor(0):null,lease);
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    private synchronized void finishReceiver(String lease)throws IOException {
        if(closed)throw new IOException("Receiver broker closed before completion");
        try{
            NativeIpc.send(channel.getFd(),("{\"op\":\"finish_receiver\",\"lease\":\""+lease+"\"}").getBytes(StandardCharsets.UTF_8),new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.descriptorCount()!=0||packet.bytes.length>65536)throw new IOException("Receiver finish framing");
                JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                reader.beginObject();if(!reader.hasNext()||!reader.nextName().equals("ok")||!reader.nextBoolean()||reader.hasNext())throw new IOException("Host did not confirm receiver exit");
                reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing receiver finish reply");reader.close();
            }
        }catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    synchronized ParcelFileDescriptor registerReceiverHost(String process)throws IOException {
        if(closed)throw new IOException("Broker closed");
        try{
            NativeIpc.send(channel.getFd(),"{\"op\":\"register_receiver_host\"}".getBytes(StandardCharsets.UTF_8),new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.descriptorCount()!=1||packet.bytes.length>4096)throw new IOException("Receiver host registration framing");
                JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                HashSet<String> fields=new HashSet<String>();Boolean ok=null;String name=null;
                reader.beginObject();while(reader.hasNext()){
                    String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate receiver host field");
                    if(key.equals("ok"))ok=reader.nextBoolean();else if(key.equals("process"))name=reader.nextString();else throw new IOException("Unknown receiver host field");
                }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing receiver host reply");reader.close();
                if(!Boolean.TRUE.equals(ok)||!process.equals(name))throw new IOException("Receiver host process mismatch");
                return packet.takeDescriptor(0);
            }
        }catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    private void release(String lease){release(lease,false);}
    private void release(String lease,boolean receiver){release(lease,receiver?"release_receiver":"release_service");}
    private synchronized void release(String lease,String operation){
        if(closed)return;
        try{
            StringWriter text=new StringWriter();JsonWriter writer=new JsonWriter(text);
            writer.beginObject().name("op").value(operation).name("lease").value(lease).endObject();writer.close();
            NativeIpc.send(channel.getFd(),text.toString().getBytes(StandardCharsets.UTF_8),new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.descriptorCount()!=0)throw new IOException("Release reply descriptor");
                JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                reader.beginObject();if(!reader.hasNext()||!reader.nextName().equals("ok")||!reader.nextBoolean()||reader.hasNext())throw new IOException("Invalid release reply");
                reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing release reply");reader.close();
            }
        }catch(IOException|RuntimeException error){
            try{close();}catch(IOException ignored){}
            System.err.println("SHIRAZ_BROKER_RELEASE_FAILED "+error.getMessage());
        }
    }
    /** Only a host-issued worker session may change its own started lifetime. */
    public synchronized void serviceStarted(boolean started)throws IOException {
        serviceState("{\"op\":\"service_started\",\"started\":"+started+"}");
    }
    public synchronized void terminateProcess(int pid)throws IOException {
        if(pid<=0)throw new IllegalArgumentException("Invalid process ID");
        if(closed)throw new IOException("Broker closed");
        try{
            NativeIpc.send(channel.getFd(),("{\"op\":\"terminate_process\",\"pid\":"+pid+"}").getBytes(StandardCharsets.UTF_8),new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.descriptorCount()!=0||packet.bytes.length>65536)throw new IOException("Process reply framing");
                JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                HashSet<String> fields=new HashSet<String>();Boolean ok=null;String error=null;
                reader.beginObject();while(reader.hasNext()){
                    String field=reader.nextName();if(!fields.add(field))throw new IOException("Duplicate process reply field");
                    if(field.equals("ok")&&reader.peek()==JsonToken.BOOLEAN)ok=reader.nextBoolean();
                    else if(field.equals("error")&&reader.peek()==JsonToken.STRING)error=reader.nextString();
                    else throw new IOException("Invalid process reply field");
                }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing process reply");reader.close();
                if(ok==null||ok&&error!=null||!ok&&error==null)throw new IOException("Invalid process reply status");
                if(!ok)throw new SecurityException("Process termination denied");
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException failure){try{close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    public synchronized void serviceRestartAllowed()throws IOException {
        serviceState("{\"op\":\"service_restart_allowed\"}");
    }
    public synchronized void serviceStartAccepted(long ticket,int startId)throws IOException {
        if(ticket<=0||startId<=0)throw new IllegalArgumentException("Service start ticket");
        serviceState("{\"op\":\"service_start_accepted\",\"ticket\":"+ticket+",\"start_id\":"+startId+"}");
    }
    public synchronized void serviceStartCompleted(int startId,int restartMode,Intent original)throws IOException {
        if(startId<=0||restartMode<0||restartMode>3)throw new IllegalArgumentException("Service start completion");
        String retained="";
        if(restartMode==android.app.Service.START_REDELIVER_INTENT){
            if(original==null)throw new UnsupportedOperationException("Redelivery requires an original intent");
            Parcel parcel=Parcel.obtain();
            try{
                original.writeToParcel(parcel,0);
                if(parcel.dataSize()>32768)throw new UnsupportedOperationException("Service redelivery snapshot exceeds 32768 bytes");
                retained=",\"intent\":\""+java.util.Base64.getEncoder().encodeToString(parcel.marshall())+"\"";
            }finally{parcel.recycle();}
        }
        serviceState("{\"op\":\"service_start_completed\",\"start_id\":"+startId+",\"restart_mode\":"+restartMode+retained+"}");
    }
    /** stopSelf(id) acknowledges work through id even while a newer start remains. */
    public synchronized void serviceStartFinished(int startId)throws IOException {
        if(startId<=0)throw new IllegalArgumentException("Service finished start ID");
        serviceState("{\"op\":\"service_start_finished\",\"start_id\":"+startId+"}");
    }
    private void serviceState(String request)throws IOException {
        if(closed)throw new IOException("Service broker closed");
        try{
            NativeIpc.send(channel.getFd(),request.getBytes(StandardCharsets.UTF_8),new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.descriptorCount()!=0)throw new IOException("Started-state reply descriptor");
                JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                reader.beginObject();if(!reader.hasNext()||!reader.nextName().equals("ok")||!reader.nextBoolean()||reader.hasNext())throw new IOException("Worker started-state update denied or malformed");
                reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing started-state reply");reader.close();
            }
        }catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    public static final class PackageIdentity {
        public final String packageName;public final int uid;
        private PackageIdentity(String name,int uid){packageName=name;this.uid=uid;}
    }
    public PackageIdentity lookupUid(int uid)throws IOException{return first(lookup(Integer.valueOf(uid),null));}
    public PackageIdentity lookupPackage(String name)throws IOException{
        if(name==null)throw new NullPointerException("packageName");return first(lookup(null,name));
    }
    private static PackageIdentity first(java.util.List<PackageIdentity> entries){return entries.isEmpty()?null:entries.get(0);}
    public java.util.List<PackageIdentity> installedPackages()throws IOException{return lookup(null,null);}
    private synchronized java.util.List<PackageIdentity> lookup(Integer uid,String pkg)throws IOException{
        if(closed)throw new IOException("Service broker closed");
        if(uid!=null&&uid<0||pkg!=null&&(pkg.length()<1||pkg.length()>1024))throw new IllegalArgumentException("Package lookup identity");
        try{
            StringWriter text=new StringWriter();JsonWriter writer=new JsonWriter(text);
            writer.beginObject().name("op").value(uid==null&&pkg==null?"list_packages":"lookup_package");
            if(uid!=null)writer.name("uid").value(uid);else if(pkg!=null)writer.name("package").value(pkg);
            writer.endObject();writer.close();
            NativeIpc.send(channel.getFd(),text.toString().getBytes(StandardCharsets.UTF_8),new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.bytes.length<1||packet.bytes.length>65536||packet.descriptorCount()!=0)throw new IOException("Package lookup reply framing");
                JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                Boolean ok=null;String error=null;boolean packages=false;java.util.ArrayList<PackageIdentity> result=new java.util.ArrayList<PackageIdentity>();HashSet<String> names=new HashSet<String>();HashSet<Integer> uids=new HashSet<Integer>();HashSet<String> fields=new HashSet<String>();
                reader.beginObject();while(reader.hasNext()){
                    String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate package lookup field");
                    if(key.equals("ok"))ok=reader.nextBoolean();
                    else if(key.equals("error"))error=reader.nextString();
                    else if(key.equals("packages")){
                        packages=true;reader.beginArray();
                        while(reader.hasNext()){
                            if(result.size()>=10000||(uid!=null||pkg!=null)&&!result.isEmpty())throw new IOException("Package lookup count");
                            String name=null;Integer foundUid=null;HashSet<String> identity=new HashSet<String>();
                            reader.beginObject();while(reader.hasNext()){
                                String field=reader.nextName();if(!identity.add(field))throw new IOException("Duplicate package identity field");
                                if(field.equals("package"))name=reader.nextString();else if(field.equals("uid"))foundUid=reader.nextInt();else throw new IOException("Unknown package identity field");
                            }reader.endObject();
                            if(name==null||name.length()>1024||!name.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")||foundUid==null||foundUid<10000||foundUid>19999)throw new IOException("Invalid package identity");
                            if(uid!=null&&uid.intValue()!=foundUid.intValue()||pkg!=null&&!pkg.equals(name))throw new IOException("Package lookup target mismatch");
                            if(!names.add(name)||!uids.add(foundUid))throw new IOException("Duplicate package identity");
                            result.add(new PackageIdentity(name,foundUid));
                        }
                        if(reader.hasNext())throw new IOException("Shared package UIDs are not supported");reader.endArray();
                    }else throw new IOException("Unknown package lookup field");
                }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing package lookup reply");reader.close();
                if(ok==null)throw new IOException("Missing package lookup result");
                if(!ok){if(error==null||packages)throw new IOException("Invalid package lookup denial");throw new SecurityException("Package lookup denied: "+error);}
                if(error!=null||!packages)throw new IOException("Invalid package lookup result");return result;
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    public static final class PendingIntentCanceledException extends IOException {
        public PendingIntentCanceledException(){super("PendingIntent is canceled or unavailable");}
    }
    public PendingIntentCapability obtainPendingIntent(int kind,int code,int flags,byte[] identity,byte[] payload)throws IOException{
        PendingIntentCapability result=pendingIntent(PendingIntentWire.request("pending_intent_obtain",null,kind,code,flags,identity,payload));
        if(result==null?(flags&0x20000000)==0:result.kind!=kind||result.flags!=(flags&0x46000000)){
            close();throw new IOException("PendingIntent registration identity mismatch");
        }
        return result;
    }
    public PendingIntentCapability obtainLivePendingIntent(int kind,int code,int flags,byte[] identity,String snapshot)throws IOException{
        if(snapshot==null||!snapshot.matches("[0-9a-f]{64}")||identity==null||identity.length<1||identity.length>8192)throw new IllegalArgumentException("Live PendingIntent snapshot");
        StringWriter text=new StringWriter();JsonWriter out=new JsonWriter(text);
        out.beginObject().name("op").value("pending_intent_live_obtain").name("kind").value(kind).name("request_code").value(code)
            .name("flags").value(flags).name("identity").value(java.util.Base64.getEncoder().encodeToString(identity)).name("snapshot").value(snapshot).endObject();out.close();
        PendingIntentCapability result=pendingIntent(text.toString());
        if(result==null?(flags&0x20000000)==0:result.kind!=kind||result.flags!=(flags&0x46000000)){
            close();throw new IOException("Live PendingIntent registration mismatch");
        }
        return result;
    }
    public synchronized java.util.Set<String> pendingIntentReferences()throws IOException{
        if(closed)throw new IOException("Broker closed");
        try{
            NativeIpc.send(channel.getFd(),"{\"op\":\"pending_intent_references\"}".getBytes(StandardCharsets.UTF_8),new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.descriptorCount()!=0||packet.bytes.length>24000)throw new IOException("PendingIntent references framing");
                JsonReader in=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                Boolean ok=null;String error=null;HashSet<String> fields=new HashSet<String>(),refs=null;
                in.beginObject();while(in.hasNext()){
                    String key=in.nextName();if(!fields.add(key))throw new IOException("Duplicate reference field");
                    if(key.equals("ok"))ok=in.nextBoolean();else if(key.equals("error"))error=in.nextString();
                    else if(key.equals("references")){
                        refs=new HashSet<String>();in.beginArray();while(in.hasNext()){
                            if(in.peek()!=JsonToken.STRING)throw new IOException("Invalid snapshot reference");
                            String ref=in.nextString();if(!ref.matches("[0-9a-f]{64}")||!refs.add(ref)||refs.size()>288)throw new IOException("Invalid snapshot references");
                        }in.endArray();
                    }else throw new IOException("Unknown reference field");
                }in.endObject();if(in.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing references");in.close();
                if(Boolean.FALSE.equals(ok)&&error!=null&&refs==null)throw new SecurityException("PendingIntent references denied");
                if(!Boolean.TRUE.equals(ok)||error!=null||refs==null)throw new IOException("Invalid references response");
                return refs;
            }
        }catch(SecurityException expected){throw expected;}
        catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    public PendingIntentCapability inspectPendingIntent(String token)throws IOException{
        PendingIntentCapability result=pendingIntent(PendingIntentWire.request("pending_intent_inspect",token,0,0,0,null,null));
        if(result==null||!result.token.equals(token)){close();throw new IOException("PendingIntent lookup token mismatch");}
        return result;
    }
    public void cancelPendingIntent(String token)throws IOException{
        if(pendingIntent(PendingIntentWire.request("pending_intent_cancel",token,0,0,0,null,null))!=null){
            close();throw new IOException("Unexpected PendingIntent cancellation facts");
        }
    }
    /** Dedicated callback channel; caller owns and drains it until shutdown. */
    public ParcelFileDescriptor registerPendingIntentHost()throws IOException{
        return pendingIntentChannel("{\"op\":\"pending_intent_host\"}");
    }
    /** Wait outside the control lock so a creator callback can reenter this client. */
    public void sendPendingIntent(String token,byte[] fill)throws IOException{
        if(token==null||!token.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("PendingIntent token");
        if(fill!=null&&(fill.length<1||fill.length>32768))throw new IllegalArgumentException("PendingIntent fill limit");
        String request="{\"op\":\"pending_intent_send\",\"token\":\""+token+"\",\"fill\":"+
            (fill==null?"null":"\""+java.util.Base64.getEncoder().encodeToString(fill)+"\"")+"}";
        try(ParcelFileDescriptor result=pendingIntentChannel(request)){
            try(NativeIpc.Packet packet=NativeIpc.receive(result.getFd())){
                if(packet.descriptorCount()!=0)throw new IOException("Unexpected PendingIntent result descriptor");
                int status=PendingIntentWire.status(packet.bytes,true);
                NativeIpc.send(result.getFd(),"{\"received\":true}".getBytes(StandardCharsets.UTF_8),new int[0]);
                if(status==1)throw new PendingIntentCanceledException();
                if(status!=0)throw new IOException(status==3?"PendingIntent delivery outcome uncertain; do not retry":"PendingIntent delivery rejected");
            }
        }
    }
    private synchronized ParcelFileDescriptor pendingIntentChannel(String request)throws IOException{
        if(closed)throw new IOException("Broker closed");
        try{
            NativeIpc.send(channel.getFd(),request.getBytes(StandardCharsets.UTF_8),new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                int status=PendingIntentWire.status(packet.bytes,false);
                if(status!=0){
                    if(packet.descriptorCount()!=0)throw new IOException("Descriptor on PendingIntent denial");
                    if(status==1)throw new PendingIntentCanceledException();
                    throw new SecurityException("PendingIntent dispatch admission denied");
                }
                if(packet.descriptorCount()!=1)throw new IOException("Missing PendingIntent channel");
                return packet.takeDescriptor(0);
            }
        }catch(SecurityException|PendingIntentCanceledException expected){throw expected;}
        catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    private synchronized PendingIntentCapability pendingIntent(String request)throws IOException{
        if(closed)throw new IOException("Service broker closed");
        try{
            NativeIpc.send(channel.getFd(),request.getBytes(StandardCharsets.UTF_8),new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.descriptorCount()!=0)throw new IOException("Unexpected PendingIntent descriptor");
                return PendingIntentWire.reply(packet.bytes);
            }
        }catch(SecurityException|PendingIntentCanceledException expected){throw expected;}
        catch(IOException|RuntimeException failure){try{close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    public synchronized java.util.Properties packageMetadata(String pkg)throws IOException {
        if(closed)throw new IOException("Service broker closed");
        if(pkg==null||pkg.length()<1||pkg.length()>1024)throw new IllegalArgumentException("Package name length");
        try{
            StringWriter text=new StringWriter();JsonWriter writer=new JsonWriter(text);
            writer.beginObject().name("op").value("package_metadata_stream").name("package").value(pkg).endObject();writer.close();
            NativeIpc.send(channel.getFd(),text.toString().getBytes(StandardCharsets.UTF_8),new int[0]);
            java.util.Properties facts=null;
            int totalBytes=0, pages=0;
            for(;;)try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.bytes.length<1||packet.bytes.length>65536||packet.descriptorCount()!=0)throw new IOException("Package metadata framing");
                totalBytes+=packet.bytes.length;
                if(++pages>512||totalBytes>8*1024*1024)throw new IOException("Package metadata aggregate limit");
                JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                Boolean ok=null,more=null;String error=null;boolean present=false,absent=false;HashSet<String> fields=new HashSet<String>();
                reader.beginObject();while(reader.hasNext()){
                    String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate metadata reply field");
                    if(key.equals("ok"))ok=reader.nextBoolean();
                    else if(key.equals("more"))more=reader.nextBoolean();
                    else if(key.equals("error"))error=reader.nextString();
                    else if(key.equals("metadata")){
                        present=true;
                        if(reader.peek()==JsonToken.NULL){reader.nextNull();absent=true;}
                        else{
                            if(facts==null)facts=new java.util.Properties();reader.beginObject();
                            while(reader.hasNext()){
                                String name=reader.nextName();if(name.length()>4096||facts.containsKey(name)||facts.size()>=65536)throw new IOException("Invalid package metadata key");
                                if(reader.peek()!=JsonToken.STRING)throw new IOException("Invalid package metadata value");
                                String value=reader.nextString();if(value.length()>65536)throw new IOException("Package metadata value limit");facts.setProperty(name,value);
                            }reader.endObject();
                        }
                    }else throw new IOException("Unknown metadata reply field");
                }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing metadata reply");reader.close();
                if(ok==null)throw new IOException("Missing metadata status");
                if(!ok){if(error==null||present||pages!=1)throw new IOException("Invalid metadata denial");throw new SecurityException("Package metadata denied: "+error);}
                if(error!=null||!present||more==null||absent&&(pages!=1||more||facts!=null))throw new IOException("Invalid metadata result");
                if(more){if(facts==null)throw new IOException("Empty metadata continuation");continue;}
                if(facts!=null){
                    int uid=Integer.parseInt(facts.getProperty("uid","-1"));
                    if(!pkg.equals(facts.getProperty("package"))||uid<10000||uid>19999||!facts.getProperty("sha256","").matches("[0-9a-f]{64}"))throw new IOException("Package metadata identity mismatch");
                }
                return facts;
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    public int compareSignatures(String first,String second)throws IOException{
        if(first==null||second==null)throw new NullPointerException("packageName");
        if(first.length()<1||second.length()<1||first.length()>1024||second.length()>1024)throw new IllegalArgumentException("Package name length");
        return compare("package",first,second);
    }
    public int compareSignatures(int first,int second)throws IOException{
        if(first<0||second<0)return -4;return compare("uid",Integer.valueOf(first),Integer.valueOf(second));
    }
    private synchronized int compare(String kind,Object first,Object second)throws IOException{
        if(closed)throw new IOException("Service broker closed");
        try{
            StringWriter text=new StringWriter();JsonWriter writer=new JsonWriter(text);
            writer.beginObject().name("op").value("compare_signatures").name("kind").value(kind).name("first");
            if(first instanceof Integer)writer.value((Integer)first);else writer.value((String)first);
            writer.name("second");if(second instanceof Integer)writer.value((Integer)second);else writer.value((String)second);
            writer.endObject();writer.close();
            NativeIpc.send(channel.getFd(),text.toString().getBytes(StandardCharsets.UTF_8),new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.bytes.length<1||packet.bytes.length>65536||packet.descriptorCount()!=0)throw new IOException("Signature comparison reply framing");
                JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                Boolean ok=null;String error=null;Integer result=null;HashSet<String> fields=new HashSet<String>();
                reader.beginObject();while(reader.hasNext()){
                    String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate signature comparison field");
                    if(key.equals("ok"))ok=reader.nextBoolean();else if(key.equals("error"))error=reader.nextString();
                    else if(key.equals("result"))result=reader.nextInt();else throw new IOException("Unknown signature comparison field");
                }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing signature comparison reply");reader.close();
                if(ok==null)throw new IOException("Missing signature comparison result");
                if(!ok){if(error==null||result!=null)throw new IOException("Invalid signature comparison denial");throw new SecurityException("Signature comparison denied: "+error);}
                if(error!=null||result==null||result!=0&&result!=-3&&result!=-4)throw new IOException("Invalid signature comparison result");
                return result;
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    public synchronized int checkPermission(String permission,String kind,Object subject)throws IOException{
        if(closed)throw new IOException("Service broker closed");
        try{
            StringWriter text=new StringWriter();JsonWriter writer=new JsonWriter(text);
            writer.beginObject().name("op").value("check_permission").name("permission").value(permission).name("kind").value(kind).name("subject");
            if(subject instanceof Integer)writer.value((Integer)subject);else writer.value((String)subject);
            writer.endObject();writer.close();
            NativeIpc.send(channel.getFd(),text.toString().getBytes(StandardCharsets.UTF_8),new int[0]);
            try(NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
                if(packet.bytes.length<1||packet.bytes.length>65536||packet.descriptorCount()!=0)throw new IOException("Permission query reply framing");
                JsonReader reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(packet.bytes)).toString()));
                Boolean ok=null;String error=null;Integer result=null;HashSet<String> fields=new HashSet<String>();
                reader.beginObject();while(reader.hasNext()){
                    String key=reader.nextName();if(!fields.add(key))throw new IOException("Duplicate permission query field");
                    if(key.equals("ok"))ok=reader.nextBoolean();else if(key.equals("error"))error=reader.nextString();
                    else if(key.equals("result")){if(reader.peek()!=JsonToken.NUMBER)throw new IOException("Permission verdict type");String value=reader.nextString();if(!value.equals("0")&&!value.equals("-1"))throw new IOException("Permission verdict value");result=Integer.valueOf(value);}else throw new IOException("Unknown permission query field");
                }reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing permission query reply");reader.close();
                if(ok==null)throw new IOException("Missing permission query result");
                if(!ok){if(error==null||result!=null)throw new IOException("Invalid permission query denial");throw new SecurityException("Permission query denied: "+error);}
                if(error!=null||result==null||result!=0&&result!=-1)throw new IOException("Invalid permission query result");
                return result;
            }
        }catch(SecurityException denied){throw denied;}
        catch(IOException|RuntimeException error){try{close();}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    private static Resolution readResolution(JsonReader reader)throws IOException {
        String pkg=null,component=null,process=null,hash=null;int uid=-1;HashSet<String> fields=new HashSet<String>();
        reader.beginObject();while(reader.hasNext()){
            String name=reader.nextName();if(!fields.add(name))throw new IOException("Duplicate service field");
            if(name.equals("package"))pkg=reader.nextString();
            else if(name.equals("component"))component=reader.nextString();
            else if(name.equals("process"))process=reader.nextString();
            else if(name.equals("apk_sha256"))hash=reader.nextString();
            else if(name.equals("uid"))uid=reader.nextInt();
            else throw new IOException("Unknown service field");
        }reader.endObject();
        if(pkg==null||component==null||process==null||process.length()<1||process.length()>1024||hash==null||!hash.matches("[0-9a-f]{64}")||uid<10000||uid>19999)
            throw new IOException("Invalid broker service identity");
        return new Resolution(pkg,component,process,hash,uid);
    }
    public synchronized void close()throws IOException {
        if(closed)return;closed=true;NativeIpc.shutdown(channel.getFd());channel.close();
    }
}
