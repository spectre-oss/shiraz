// SPDX-License-Identifier: GPL-3.0-only
package android.database;
import android.content.ContentResolver;
import android.net.Uri;
import java.lang.ref.WeakReference;

/** Shared subscription lifecycle for host cursor implementations. */
final class CursorNotification {
    private ContentResolver resolver;private Uri uri;private ContentObserver observer;
    private boolean closed;
    private final Runnable changed;
    CursorNotification(Runnable changed){this.changed=changed;}
    synchronized Uri uri(){return uri;}
    synchronized void set(ContentResolver next,Uri target){
        if(closed)throw new IllegalStateException("Cursor is closed");
        if(next==null||target==null)throw new NullPointerException("Cursor notification resolver/URI");
        ContentObserver added=create();
        // Admission must succeed before replacing an existing working subscription.
        next.registerContentObserver(target,true,added);
        deactivate();resolver=next;uri=target;observer=added;
    }
    private ContentObserver create(){
        final WeakReference<CursorNotification> owner=new WeakReference<CursorNotification>(this);
        return new ContentObserver(null){public void onChange(boolean self,Uri uri){
            CursorNotification value=owner.get();
            if(value!=null){synchronized(value){if(value.closed||value.observer!=this)return;}value.changed.run();}
        }};
    }
    synchronized void deactivate(){if(observer!=null){resolver.unregisterContentObserver(observer);observer=null;}}
    synchronized void requery(){
        if(closed)throw new IllegalStateException("Cursor is closed");
        if(resolver!=null&&observer==null){ContentObserver added=create();resolver.registerContentObserver(uri,true,added);observer=added;}
    }
    synchronized void close(){closed=true;deactivate();}
    void changed(boolean self){
        ContentResolver current;Uri target;ContentObserver source;
        synchronized(this){if(closed)return;current=resolver;target=uri;source=observer;}
        if(self&&current!=null)current.notifyChange(target,source);
    }
}
