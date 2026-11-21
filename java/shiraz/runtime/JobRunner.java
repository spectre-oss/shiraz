// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.app.job.JobInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Private host-owned one-job process; the host pins and authorizes its generation. */
public final class JobRunner {
 private JobRunner() {}
 public static void main(String[] args){ServiceRunner.launch(args,false,true);}
 static JobDefinition.Launch read(int channel,String pkg,String component)throws IOException {
  try(NativeIpc.Packet packet=NativeIpc.receive(channel)){
   if(packet.descriptorCount()!=0)throw new IOException("Job payload attachments unsupported");
   return JobDefinition.decodeLaunch(packet.bytes,pkg,component);
  }
 }
 static void complete(int channel,boolean retry,int stopReason)throws IOException {
  // The host must also observe successful process exit before completing its claim.
  NativeIpc.send(channel,("{\"ok\":true,\"retry\":"+retry+",\"stop_reason\":"+stopReason+"}").getBytes(StandardCharsets.UTF_8),new int[0]);
 }
}
