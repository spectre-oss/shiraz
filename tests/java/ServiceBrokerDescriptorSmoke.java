// SPDX-License-Identifier: GPL-3.0-only
import android.os.ParcelFileDescriptor;
import java.nio.charset.StandardCharsets;
import shiraz.runtime.NativeIpc;
/** Host/ART SCM_RIGHTS ownership test; no service binding claim. */
public final class ServiceBrokerDescriptorSmoke {
 public static void main(String[] args)throws Exception{
  try(ParcelFileDescriptor channel=ParcelFileDescriptor.adoptFd(Integer.parseInt(args[0]));
      NativeIpc.Packet packet=NativeIpc.receive(channel.getFd())){
   if(packet.descriptorCount()!=1)throw new AssertionError("Missing endpoint");
   try(ParcelFileDescriptor endpoint=packet.takeDescriptor(0)){
    NativeIpc.send(channel.getFd(),"{\"ok\":true}".getBytes(StandardCharsets.UTF_8),new int[]{endpoint.getFd()});
   }
  }
  System.out.println("ART_BROKER_DESCRIPTOR_ROUNDTRIP_OK");
 }
}
