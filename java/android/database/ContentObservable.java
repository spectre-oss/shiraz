// SPDX-License-Identifier: GPL-3.0-only
package android.database;
import android.net.Uri;

/** Content listeners with snapshot dispatch, keeping app callbacks outside the registry lock. */
public class ContentObservable extends Observable<ContentObserver> {
    @Override public void registerObserver(ContentObserver observer){super.registerObserver(observer);}
    private ContentObserver[] snapshot(){synchronized(mObservers){return mObservers.toArray(new ContentObserver[0]);}}
    @Deprecated public void dispatchChange(boolean selfChange){dispatchChange(selfChange,null);}
    public void dispatchChange(boolean selfChange,Uri uri){
        for(ContentObserver observer:snapshot())if(!selfChange||observer.deliverSelfNotifications())observer.dispatchChange(selfChange,uri);
    }
    /** Legacy entry point invokes callbacks directly, without handler dispatch or self filtering. */
    @Deprecated public void notifyChange(boolean selfChange){for(ContentObserver observer:snapshot())observer.onChange(selfChange);}
}
