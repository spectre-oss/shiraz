// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.AbstractThreadedSyncAdapter;
import java.util.HashSet;

/** One context owner's adapter lifetime; no scheduling or remote authorization. */
public final class HostSyncAdapters {
    private final HashSet<AbstractThreadedSyncAdapter> adapters=new HashSet<AbstractThreadedSyncAdapter>();
    private boolean closed;
    public synchronized void register(AbstractThreadedSyncAdapter adapter){
        if(closed)throw new IllegalStateException("Sync owner closed");
        if(adapters.size()>=64)throw new IllegalStateException("Sync adapter limit");
        adapters.add(adapter);
    }
    public synchronized void released(AbstractThreadedSyncAdapter adapter){adapters.remove(adapter);}
    public void close(long timeoutMillis){
        if(timeoutMillis<0||timeoutMillis>60000)throw new IllegalArgumentException("Sync shutdown timeout");
        AbstractThreadedSyncAdapter[] snapshot;
        synchronized(this){closed=true;snapshot=adapters.toArray(new AbstractThreadedSyncAdapter[0]);}
        long deadline=System.nanoTime()+timeoutMillis*1000000L;
        for(AbstractThreadedSyncAdapter adapter:snapshot)adapter.shirazClose();
        for(AbstractThreadedSyncAdapter adapter:snapshot)if(!adapter.shirazAwaitTermination(deadline))throw new IllegalStateException("Sync workers did not stop before session shutdown");
    }
}
