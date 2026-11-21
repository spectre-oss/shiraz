// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.view.*;
import android.widget.*;
import android.os.*;
public final class ListSavedStateSmoke {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static Context context=new ContextWrapper(null){public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}};
 static class Rows extends BaseAdapter {
  int count=100,shift;boolean stable;
  Rows(boolean stable){this.stable=stable;}
  public int getCount(){return count;}public Object getItem(int p){return p;}public long getItemId(int p){return p-shift+1000;}public boolean hasStableIds(){return stable;}
  public View getView(int p,View old,ViewGroup parent){View row=old==null?new View(context):old;row.setLayoutParams(new AbsListView.LayoutParams(-1,20));return row;}
 }
 static void layout(ListView list,int width,int height){list.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));list.layout(0,0,width,height);}
 static ListView list(Rows rows){ListView list=new ListView(context,null,0);list.setId(101);list.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);if(rows!=null)list.setAdapter(rows);return list;}
 public static void main(String[] args){
  Looper.prepareMainLooper();Rows rows=new Rows(true);ListView original=list(rows);layout(original,200,100);original.setSelectionFromTop(25,-7);original.setItemChecked(27,true);original.getCheckedItemPositions().put(29,false);
  Parcelable saved=original.onSaveInstanceState();original.setSelectionFromTop(1,0);original.clearChoices();
  Rows changed=new Rows(true);changed.shift=3;ListView restored=list(changed);restored.onRestoreInstanceState(saved);layout(restored,300,80);
  check(restored.getFirstVisiblePosition()==28&&restored.getChildAt(0).getTop()==-7,"stable anchor/pixel offset not restored");
  check(restored.getSelectedItemPosition()==28&&restored.isItemChecked(30)&&!restored.isItemChecked(32),"selection/choice IDs not restored");
  ListView delayed=list(null);delayed.onRestoreInstanceState(saved);Parcelable resaved=delayed.onSaveInstanceState();layout(delayed,200,100);delayed.setAdapter(rows);layout(delayed,200,100);
  check(delayed.getFirstVisiblePosition()==25&&delayed.getChildAt(0).getTop()==-7,"restore before adapter lost");
  Rows empty=new Rows(false);empty.count=0;ListView later=list(empty);later.onRestoreInstanceState(resaved);layout(later,200,100);empty.count=100;empty.notifyDataSetChanged();layout(later,200,100);check(later.getFirstVisiblePosition()==25,"restore before data lost");
  ListView explicit=list(rows);explicit.onRestoreInstanceState(saved);explicit.setSelectionFromTop(4,0);layout(explicit,200,100);check(explicit.getFirstVisiblePosition()==4,"explicit selection overwritten");
  Rows shortRows=new Rows(false);shortRows.count=3;ListView shortList=list(shortRows);shortList.onRestoreInstanceState(saved);layout(shortList,200,100);check(shortList.getFirstVisiblePosition()>=0&&shortList.getLastVisiblePosition()<3,"short data out of bounds");
  android.util.SparseArray<Parcelable> hierarchy=new android.util.SparseArray<Parcelable>();delayed.saveHierarchyState(hierarchy);ListView tree=list(rows);tree.restoreHierarchyState(hierarchy);layout(tree,200,100);check(tree.getFirstVisiblePosition()==25,"hierarchy state missing");
  System.out.println("LIST_SAVED_STATE_OK");
 }
}
