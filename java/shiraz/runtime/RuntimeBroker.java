// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import java.io.IOException;
/** Process-owned broker channel. Child service sessions need separate host admission. */
final class RuntimeBroker {
    private static ServiceBrokerClient client;
    private RuntimeBroker() {}
    static synchronized void attach() throws IOException {
        if(client!=null)throw new IllegalStateException("Broker already attached");
        String value=System.getenv("SHIRAZ_BROKER_FD");
        if(value==null)return;
        int fd;
        try{fd=Integer.parseInt(value);}catch(NumberFormatException error){throw new IOException("Invalid broker descriptor",error);}
        if(fd<3)throw new IOException("Broker descriptor overlaps standard streams");
        client=new ServiceBrokerClient(fd);
        System.out.println("SHIRAZ_BROKER_CHANNEL_ATTACHED");
    }
    static void close() throws IOException {
        ServiceBrokerClient old;synchronized(RuntimeBroker.class){old=client;client=null;}
        try{HostPendingIntents.shutdown();HostAccountListeners.shutdown();HostAccountSessions.shutdown();}finally{if(old!=null)old.close();}
    }
    static synchronized ServiceBrokerClient client(){return client;}
}
