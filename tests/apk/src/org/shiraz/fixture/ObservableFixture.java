// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.database.*;
public final class ObservableFixture {
 private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 private static class Registry extends Observable<Object>{int size(){synchronized(mObservers){return mObservers.size();}}}
 public static void check(){Registry registry=new Registry();Object item=new Object();registry.registerObserver(item);check(registry.size()==1,"Observer added");try{registry.registerObserver(item);throw new AssertionError("Duplicate observer accepted");}catch(IllegalStateException expected){}registry.unregisterObserver(item);try{registry.unregisterObserver(item);throw new AssertionError("Missing observer removed");}catch(IllegalStateException expected){}try{registry.registerObserver(null);throw new AssertionError("Null observer accepted");}catch(IllegalArgumentException expected){}
 final DataSetObservable data=new DataSetObservable();final StringBuilder events=new StringBuilder();DataSetObserver first=new DataSetObserver(){public void onChanged(){events.append('A');}public void onInvalidated(){events.append('I');}};DataSetObserver second=new DataSetObserver(){public void onChanged(){events.append('B');data.unregisterObserver(this);}};data.registerObserver(first);data.registerObserver(second);data.notifyChanged();check(events.toString().equals("BA"),"Reverse delivery permits observer self-removal");data.notifyInvalidated();check(events.toString().equals("BAI"),"Dataset invalidation");data.unregisterAll();data.notifyChanged();check(events.toString().equals("BAI"),"Unregister all stops delivery");System.out.println("DATASET_OBSERVERS_OK");}
}
