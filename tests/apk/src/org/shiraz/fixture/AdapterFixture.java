// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.widget.*;
import android.view.*;
import android.database.DataSetObserver;
import android.content.Context;
public final class AdapterFixture {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void check(final Context context){
  final java.util.ArrayList<String> rows=new java.util.ArrayList<String>();rows.add("first");rows.add("second");
  BaseAdapter adapter=new BaseAdapter(){
   public int getCount(){return rows.size();}public String getItem(int position){return rows.get(position);}public long getItemId(int position){return position;}
   public View getView(int position,View recycled,ViewGroup parent){TextView row=recycled==null?new TextView(context):(TextView)recycled;row.setText(getItem(position));return row;}
  };
  check(adapter instanceof ListAdapter&&adapter instanceof SpinnerAdapter,"Adapter contracts");
  check(!adapter.isEmpty()&&!adapter.hasStableIds()&&adapter.getViewTypeCount()==1&&adapter.getItemViewType(1)==0&&adapter.areAllItemsEnabled()&&adapter.isEnabled(1),"Default row semantics");
  View row=adapter.getView(0,null,null);check(((TextView)row).getText().toString().equals("first"),"Initial row binding");
  check(adapter.getDropDownView(1,row,null)==row&&((TextView)row).getText().toString().equals("second"),"Dropdown binds recycled row");
  final int[] changed={0},invalid={0};DataSetObserver observer=new DataSetObserver(){public void onChanged(){changed[0]++;}public void onInvalidated(){invalid[0]++;}};
  adapter.registerDataSetObserver(observer);rows.clear();adapter.notifyDataSetChanged();check(adapter.isEmpty()&&changed[0]==1&&invalid[0]==0,"Dataset change notification");
  adapter.notifyDataSetInvalidated();check(invalid[0]==1,"Invalidation notification");adapter.unregisterDataSetObserver(observer);adapter.notifyDataSetChanged();check(changed[0]==1,"Observer unregister");
  final BaseAdapter target=adapter;DataSetObserver self=new DataSetObserver(){public void onChanged(){target.unregisterDataSetObserver(this);changed[0]++;}};adapter.registerDataSetObserver(self);adapter.notifyDataSetChanged();adapter.notifyDataSetChanged();check(changed[0]==2,"Self-removing observer");
  System.out.println("ADAPTER_BINDING_OBSERVERS_OK");
 }
}
