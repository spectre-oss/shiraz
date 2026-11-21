// SPDX-License-Identifier: GPL-3.0-only
import android.os.ParcelFileDescriptor;
import shiraz.runtime.NativeIpc;
import shiraz.runtime.ServiceBrokerClient;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
public final class PackageMetadataStreamSmoke {
 static String identity="\"package\":\"test.target\",\"uid\":\"10000\",\"sha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"";
 static String reply(String facts,boolean more){return "{\"ok\":true,\"metadata\":{"+facts+"},\"more\":"+more+"}";}
 static void probe(final String[] replies,boolean valid,boolean absent)throws Exception{
  final int[] pair=NativeIpc.pair();final Throwable[] error={null};final java.util.concurrent.CountDownLatch consumed=new java.util.concurrent.CountDownLatch(1);
  Thread server=new Thread(new Runnable(){public void run(){try(ParcelFileDescriptor fd=ParcelFileDescriptor.adoptFd(pair[1]);NativeIpc.Packet request=NativeIpc.receive(fd.getFd())){
   if(!new String(request.bytes,StandardCharsets.UTF_8).contains("package_metadata_stream"))throw new AssertionError("Wrong operation");
   for(String reply:replies)NativeIpc.send(fd.getFd(),reply.getBytes(StandardCharsets.UTF_8),new int[0]);
   if(!(replies.length==1&&replies[0].endsWith("true}")))consumed.await(15,java.util.concurrent.TimeUnit.SECONDS);
  }catch(Throwable failure){error[0]=failure;}}});server.start();
  try(ServiceBrokerClient client=new ServiceBrokerClient(pair[0])){
   try{Properties facts=client.packageMetadata("test.target");if(!valid)throw new AssertionError("Invalid stream accepted");
    if(absent?facts!=null:facts==null||!"last".equals(facts.getProperty("tail")))throw new AssertionError("Wrong assembled metadata");
   }catch(java.io.IOException expected){if(valid)throw expected;}
  }catch(Throwable failure){failure.printStackTrace();throw failure;}finally{consumed.countDown();server.join(1000);if(server.isAlive()){NativeIpc.shutdown(pair[1]);server.join(1000);throw new AssertionError("Server did not finish",error[0]);}}
  if(error[0]!=null)throw new AssertionError(error[0]);
 }
 public static void main(String[] args)throws Exception{
  probe(new String[]{reply(identity,true),reply("\"tail\":\"last\"",false)},true,false);
  probe(new String[]{"{\"ok\":true,\"metadata\":null,\"more\":false}"},true,true);
  probe(new String[]{reply(identity,true),reply(identity,false)},false,false);
  probe(new String[]{reply(identity,true),"{\"ok\":true,\"metadata\":null,\"more\":false}"},false,false);
  probe(new String[]{"{\"ok\":true,\"metadata\":{}}"},false,false);
  probe(new String[]{reply(identity.replace("test.target","test.other"),false)},false,false);
  probe(new String[]{reply(identity,true)},false,false);
  System.out.println("PACKAGE_METADATA_STREAM_OK");
 }
}
