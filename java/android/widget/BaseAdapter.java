// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
/** Shared dataset notifications and default homogeneous row behavior. */
public abstract class BaseAdapter implements ListAdapter,SpinnerAdapter {
 private final DataSetObservable observable=new DataSetObservable();
 public void registerDataSetObserver(DataSetObserver observer){observable.registerObserver(observer);}
 public void unregisterDataSetObserver(DataSetObserver observer){observable.unregisterObserver(observer);}
 public void notifyDataSetChanged(){observable.notifyChanged();}
 public void notifyDataSetInvalidated(){observable.notifyInvalidated();}
 public boolean hasStableIds(){return false;}
 public boolean areAllItemsEnabled(){return true;}
 public boolean isEnabled(int position){return true;}
 public int getItemViewType(int position){return 0;}
 public int getViewTypeCount(){return 1;}
 public boolean isEmpty(){return getCount()==0;}
 public View getDropDownView(int position,View convertView,ViewGroup parent){return getView(position,convertView,parent);}
 private CharSequence[] autofillOptions;
 public CharSequence[] getAutofillOptions(){return autofillOptions;}
 public void setAutofillOptions(CharSequence... options){autofillOptions=options;}
}
