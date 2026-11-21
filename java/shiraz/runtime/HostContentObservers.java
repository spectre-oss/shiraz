// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.database.ContentObserver;
import android.net.Uri;
import java.util.ArrayList;
import java.util.List;

/** Local owner-scoped notifications. No provider IPC or sync scheduling. */
public final class HostContentObservers {
    private final ArrayList<Entry> entries=new ArrayList<Entry>();
    private boolean closed,warnedSync;
    private static final class Entry {
        final Uri uri;final boolean descendants;final ContentObserver observer;
        volatile boolean active=true;
        Entry(Uri uri,boolean descendants,ContentObserver observer){this.uri=uri;this.descendants=descendants;this.observer=observer;}
    }
    private static void validate(Uri uri){
        if(uri==null)throw new NullPointerException("uri");
        if(!"content".equals(uri.getScheme())||uri.getAuthority()==null||uri.getAuthority().isEmpty())throw new IllegalArgumentException("Content observer requires content authority");
        if(uri.toString().length()>8192||uri.getPathSegments().size()>128)throw new IllegalArgumentException("Content observer URI limit");
    }
    public synchronized void register(Uri uri,boolean descendants,ContentObserver observer){
        validate(uri);if(observer==null)throw new NullPointerException("observer");
        if(closed)throw new IllegalStateException("Content observer owner closed");
        if(entries.size()>=1024)throw new IllegalStateException("Content observer registration limit");
        entries.add(new Entry(uri,descendants,observer));
    }
    public synchronized void unregister(ContentObserver observer){
        if(observer==null)throw new NullPointerException("observer");
        for(int i=entries.size()-1;i>=0;i--)if(entries.get(i).observer==observer){entries.remove(i).active=false;}
    }
    private static boolean matches(Entry entry,Uri change,int flags){
        if(!entry.uri.getAuthority().equals(change.getAuthority()))return false;
        List<String> registered=entry.uri.getPathSegments(),changed=change.getPathSegments();
        for(int i=0;i<Math.min(registered.size(),changed.size());i++)if(!registered.get(i).equals(changed.get(i)))return false;
        if(changed.size()<=registered.size())return !entry.descendants||(flags&android.content.ContentResolver.NOTIFY_SKIP_NOTIFY_FOR_DESCENDANTS)==0;
        return entry.descendants;
    }
    public void notifyChange(final Uri uri,ContentObserver source,boolean syncToNetwork){
        notifyChange(uri,source,syncToNetwork?android.content.ContentResolver.NOTIFY_SYNC_TO_NETWORK:0);
    }
    public void notifyChange(final Uri uri,ContentObserver source,final int flags){
        if((flags&~31)!=0)throw new UnsupportedOperationException("Unsupported content notification flags");
        validate(uri);ArrayList<Entry> selected=new ArrayList<Entry>();
        boolean selfAllowed=source!=null&&source.deliverSelfNotifications();
        synchronized(this){
            if(closed)throw new IllegalStateException("Content observer owner closed");
            if((flags&1)!=0&&!warnedSync){warnedSync=true;System.err.println("SHIRAZ_UNSUPPORTED notifyChange network sync scheduling; delivering local observers only");}
            for(Entry entry:entries)if(matches(entry,uri,flags)&&(entry.observer!=source||selfAllowed))selected.add(entry);
        }
        for(final Entry entry:selected){
            final boolean self=entry.observer==source;
            entry.observer.shirazDispatchChange(self,uri,flags,new java.util.concurrent.Callable<Boolean>(){
                public Boolean call(){return entry.active;}
            });
        }
    }
    public synchronized void close(){closed=true;for(Entry entry:entries)entry.active=false;entries.clear();}
}
