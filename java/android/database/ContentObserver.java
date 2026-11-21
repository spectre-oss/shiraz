// SPDX-License-Identifier: GPL-3.0-only
package android.database;

import android.os.Handler;

/** Observer for content URI changes; delivery is host-mediated. */
public abstract class ContentObserver {
    private final Handler handler;

    public ContentObserver(Handler handler) { this.handler = handler; }

    public boolean deliverSelfNotifications() { return false; }

    public final void dispatchChange(boolean selfChange) { dispatchChange(selfChange, null); }

    public final void dispatchChange(boolean selfChange, android.net.Uri uri) {
        dispatchChange(selfChange,uri,0);
    }
    public final void dispatchChange(final boolean selfChange,final android.net.Uri uri,final int flags) {
        if (handler == null) onChange(selfChange, uri,flags);
        else handler.post(new Runnable() {
            public void run() { onChange(selfChange, uri,flags); }
        });
    }

    public void onChange(boolean selfChange) { }

    public void onChange(boolean selfChange, android.net.Uri uri) { onChange(selfChange); }

    public void onChange(boolean selfChange, android.net.Uri uri, int flags) { onChange(selfChange, uri); }

    /** Owner guard prevents queued local notifications after registration teardown. */
    public final void shirazDispatchChange(final boolean selfChange,final android.net.Uri uri,final java.util.concurrent.Callable<Boolean> active){
        shirazDispatchChange(selfChange,uri,0,active);
    }
    public final void shirazDispatchChange(final boolean selfChange,final android.net.Uri uri,final int flags,final java.util.concurrent.Callable<Boolean> active){
        Runnable delivery=new Runnable(){public void run(){
            final boolean deliver;
            try{deliver=active.call();}catch(Exception failure){throw new IllegalStateException("Content observer lifetime check",failure);}
            if(deliver)onChange(selfChange,uri,flags);
        }};
        if(handler==null)delivery.run();
        else if(!handler.post(delivery))throw new IllegalStateException("Content observer handler stopped");
    }
}
