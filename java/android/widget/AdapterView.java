// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
/** Common adapter-view selection and item-event contract. Subclasses own row layout. */
public abstract class AdapterView<T extends Adapter> extends ViewGroup {
 public static final int INVALID_POSITION=-1,ITEM_VIEW_TYPE_IGNORE=-1,ITEM_VIEW_TYPE_HEADER_OR_FOOTER=-2;
 public static final long INVALID_ROW_ID=Long.MIN_VALUE;
 private OnItemClickListener click;private OnItemLongClickListener longClick;private OnItemSelectedListener selected;
 private View emptyView;private int selection=INVALID_POSITION;private long selectedId=INVALID_ROW_ID;
 protected int firstPosition;
 public AdapterView(Context c){this(c,null);}public AdapterView(Context c,AttributeSet a){this(c,a,0);}public AdapterView(Context c,AttributeSet a,int s){this(c,a,s,0);}public AdapterView(Context c,AttributeSet a,int s,int r){super(c,a,s,r);}
 public abstract T getAdapter();public abstract void setAdapter(T adapter);public abstract View getSelectedView();public abstract void setSelection(int position);
 public interface OnItemClickListener{void onItemClick(AdapterView<?> parent,View view,int position,long id);}
 public interface OnItemLongClickListener{boolean onItemLongClick(AdapterView<?> parent,View view,int position,long id);}
 public interface OnItemSelectedListener{void onItemSelected(AdapterView<?> parent,View view,int position,long id);void onNothingSelected(AdapterView<?> parent);}
 public void setOnItemClickListener(OnItemClickListener value){click=value;}public final OnItemClickListener getOnItemClickListener(){return click;}
 public void setOnItemLongClickListener(OnItemLongClickListener value){longClick=value;}public final OnItemLongClickListener getOnItemLongClickListener(){return longClick;}
 public void setOnItemSelectedListener(OnItemSelectedListener value){selected=value;}public final OnItemSelectedListener getOnItemSelectedListener(){return selected;}
 public boolean performItemClick(View view,int position,long id){if(click==null)return false;click.onItemClick(this,view,position,id);return true;}
 public int getCount(){T a=getAdapter();return a==null?0:a.getCount();}
 public Object getItemAtPosition(int position){T a=getAdapter();return a==null||position<0||position>=a.getCount()?null:a.getItem(position);}
 public long getItemIdAtPosition(int position){T a=getAdapter();return a==null||position<0||position>=a.getCount()?INVALID_ROW_ID:a.getItemId(position);}
 public int getSelectedItemPosition(){return selection;}public long getSelectedItemId(){return selectedId;}public Object getSelectedItem(){return getItemAtPosition(selection);}
 protected final void shirazSelect(int position){int next=position<0||position>=getCount()?INVALID_POSITION:position;long id=getItemIdAtPosition(next);if(selection==next&&selectedId==id)return;selection=next;selectedId=id;OnItemSelectedListener listener=selected;if(listener!=null){if(next<0)listener.onNothingSelected(this);else listener.onItemSelected(this,getSelectedView(),next,id);}invalidate();}
 public int getFirstVisiblePosition(){return firstPosition;}public int getLastVisiblePosition(){return firstPosition+getChildCount()-1;}
 public int getPositionForView(View view){if(view==null)return INVALID_POSITION;View row=view;for(int depth=0;depth<4096;depth++){ViewParent parent=row.getParent();if(parent==this){int index=indexOfChild(row);return index<0?INVALID_POSITION:firstPosition+index;}if(!(parent instanceof View))return INVALID_POSITION;row=(View)parent;}return INVALID_POSITION;}
 public void setEmptyView(View view){if(view==this)throw new IllegalArgumentException("AdapterView cannot be its own empty view");emptyView=view;if(view!=null&&view.getImportantForAccessibility()==IMPORTANT_FOR_ACCESSIBILITY_AUTO)view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);shirazUpdateEmpty();}
 public View getEmptyView(){return emptyView;}
 protected final void shirazUpdateEmpty(){shirazUpdateEmpty(getAdapter()==null||getAdapter().isEmpty());}
 protected final void shirazUpdateEmpty(boolean empty){if(emptyView!=null){emptyView.setVisibility(empty?VISIBLE:GONE);setVisibility(empty?GONE:VISIBLE);}else setVisibility(VISIBLE);}
 @Override public void addView(View child,int index,ViewGroup.LayoutParams params){throw new UnsupportedOperationException("AdapterView children must come from its adapter");}
 @Override public void removeView(View child){throw new UnsupportedOperationException("AdapterView children must come from its adapter");}
 @Override public void removeViewAt(int index){throw new UnsupportedOperationException("AdapterView children must come from its adapter");}
 @Override public void removeAllViews(){throw new UnsupportedOperationException("AdapterView children must come from its adapter");}
 protected final void shirazAddRow(View row,int index,ViewGroup.LayoutParams params){super.addViewInLayout(row,index,params,true);}
 protected final void shirazRemoveRow(int index){super.removeViewInLayout(getChildAt(index));}
 @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){}
}
