// SPDX-License-Identifier: GPL-3.0-only
public final class AdapterSmoke {
 private static void expandable(final android.content.Context context){
  final android.widget.ExpandableListView list=new android.widget.ExpandableListView(context,null,0);
  class Groups extends android.widget.BaseExpandableListAdapter {
   long[] ids={10,20};int notifications;public int getGroupCount(){return ids.length;}public int getChildrenCount(int g){return 2;}public boolean hasStableIds(){return true;}public long getGroupId(int g){return ids[g];}public long getChildId(int g,int c){return 100+c;}public Object getGroup(int g){return ids[g];}public Object getChild(int g,int c){return c;}
   public boolean isChildSelectable(int g,int c){return c==0;}
   public android.view.View getGroupView(int g,boolean expanded,android.view.View recycled,android.view.ViewGroup parent){android.view.View v=recycled==null?new android.view.View(context):recycled;v.setMinimumHeight(20);return v;}
   public android.view.View getChildView(int g,int c,boolean last,android.view.View recycled,android.view.ViewGroup parent){return getGroupView(g,false,recycled,parent);}
   public void onGroupExpanded(int g){notifications++;}
  }
  Groups groups=new Groups();android.view.View header=new android.view.View(context);header.setMinimumHeight(20);list.addHeaderView(header);list.setAdapter(groups);
  if(list.getCount()!=3||!list.expandGroup(0)||list.expandGroup(0)||list.getCount()!=5||groups.notifications!=1)throw new AssertionError("expand count/idempotence");
  long packed=android.widget.ExpandableListView.getPackedPositionForChild(0,1);if(list.getExpandableListPosition(3)!=packed||list.getFlatListPosition(packed)!=3||list.getExpandableListPosition(0)!=android.widget.ExpandableListView.PACKED_POSITION_VALUE_NULL)throw new AssertionError("header offset packed positions");
  final int[] clicks={0};list.setOnChildClickListener(new android.widget.ExpandableListView.OnChildClickListener(){public boolean onChildClick(android.widget.ExpandableListView parent,android.view.View view,int g,int c,long id){if(g!=0||c!=0||id!=100)throw new AssertionError("child event identity");clicks[0]++;return true;}});
  if(!list.performItemClick(null,2,0)||list.performItemClick(null,3,0)||clicks[0]!=1)throw new AssertionError("child selectable clicks");
  groups.ids=new long[]{20,10};groups.notifyDataSetChanged();if(list.isGroupExpanded(0)||!list.isGroupExpanded(1)||list.getCount()!=5)throw new AssertionError("stable group expansion across reorder");
  if(!list.collapseGroup(1)||list.getCount()!=3||list.setSelectedChild(1,0,false)||!list.setSelectedChild(1,0,true)||list.getSelectedId()!=100)throw new AssertionError("selection expands requested group");
  list.measure(android.view.View.MeasureSpec.makeMeasureSpec(100,1073741824),android.view.View.MeasureSpec.makeMeasureSpec(100,1073741824));list.layout(0,0,100,100);if(list.getChildCount()==0)throw new AssertionError("expandable rows not laid out");
  groups.notifyDataSetInvalidated();if(list.getCount()!=0)throw new AssertionError("invalidated expandable data");list.setAdapter((android.widget.ExpandableListAdapter)null);groups.notifyDataSetChanged();if(list.getCount()!=1)throw new AssertionError("retired adapter observer retained");
  list.setAdapter(groups);android.view.View footer=new android.view.View(context);footer.setMinimumHeight(20);list.addFooterView(footer);list.setDivider(new android.graphics.drawable.ColorDrawable(0xffff0000));list.setDividerHeight(1);list.measure(android.view.View.MeasureSpec.makeMeasureSpec(100,1073741824),android.view.View.MeasureSpec.makeMeasureSpec(100,1073741824));list.layout(0,0,100,100);android.graphics.Bitmap pixels=android.graphics.Bitmap.createBitmap(100,100,android.graphics.Bitmap.Config.ARGB_8888);list.draw(new android.graphics.Canvas(pixels));if(pixels.getPixel(50,20)!=0xffff0000||pixels.getPixel(50,62)!=0xffff0000)throw new AssertionError("fixed row divider pixels");list.setHeaderDividersEnabled(false);list.setFooterDividersEnabled(false);pixels.eraseColor(0);list.draw(new android.graphics.Canvas(pixels));if(pixels.getPixel(50,20)!=0||pixels.getPixel(50,62)!=0||pixels.getPixel(50,41)!=0xffff0000)throw new AssertionError("fixed divider visibility preserves data divider");pixels.recycle();
 }
 public static void main(String[] args){
  android.content.Context context=new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}};
  org.shiraz.fixture.AdapterFixture.check(context);expandable(context);
  android.widget.ListView zeroRows=new android.widget.ListView(context,null,0);zeroRows.addHeaderView(new android.view.View(context));
  zeroRows.setAdapter(new android.widget.BaseAdapter(){public int getCount(){return 3;}public Object getItem(int p){return p;}public long getItemId(int p){return p;}public android.view.View getView(int p,android.view.View old,android.view.ViewGroup parent){android.view.View v=old==null?new android.view.View(context):old;v.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,p==1?0:20));return v;}});
  zeroRows.measure(android.view.View.MeasureSpec.makeMeasureSpec(100,1073741824),android.view.View.MeasureSpec.makeMeasureSpec(100,1073741824));zeroRows.layout(0,0,100,100);
  if(zeroRows.pointToPosition(10,10)!=1||zeroRows.pointToPosition(10,25)!=3||zeroRows.pointToPosition(10,45)!=-1)throw new AssertionError("zero height header/data row positions");
  android.widget.FrameLayout stateParent=new android.widget.FrameLayout(context);
  final int[] stateUpdates={0};android.view.View stateChild=new android.view.View(context){protected void drawableStateChanged(){super.drawableStateChanged();stateUpdates[0]++;}};
  stateParent.addView(stateChild);stateChild.setDuplicateParentStateEnabled(true);int before=stateUpdates[0];stateParent.setPressed(true);
  if(!stateChild.isDuplicateParentStateEnabled()||stateUpdates[0]<=before||!java.util.Arrays.equals(stateParent.getDrawableState(),stateChild.getDrawableState()))throw new AssertionError("parent state propagation");
  stateChild.setDuplicateParentStateEnabled(false);if(java.util.Arrays.equals(stateParent.getDrawableState(),stateChild.getDrawableState()))throw new AssertionError("independent child state restored");
  class Rows extends android.widget.AdapterView<android.widget.ListAdapter> {
   android.widget.ListAdapter adapter;
   Rows(){super(context);}
   public android.widget.ListAdapter getAdapter(){return adapter;}
   public void setAdapter(android.widget.ListAdapter a){adapter=a;shirazSelect(-1);shirazUpdateEmpty();}
   public void setSelection(int p){shirazSelect(p);}
   public android.view.View getSelectedView(){return getChildAt(getSelectedItemPosition()-firstPosition);}
   void row(android.view.View v){shirazAddRow(v,-1,new android.view.ViewGroup.LayoutParams(20,20));}
   void remove(){shirazRemoveRow(0);}
  }
  final Rows rows=new Rows();android.view.View empty=new android.view.View(context);rows.setEmptyView(empty);
  if(rows.getVisibility()!=8||empty.getVisibility()!=0)throw new AssertionError("Empty view initial state");
  rows.setAdapter(new android.widget.BaseAdapter(){public int getCount(){return 2;}public Object getItem(int p){return "row"+p;}public long getItemId(int p){return 100+p;}public android.view.View getView(int p,android.view.View recycled,android.view.ViewGroup parent){return new android.view.View(context);}});
  if(rows.getVisibility()!=0||empty.getVisibility()!=8)throw new AssertionError("Populated view state");
  android.widget.FrameLayout row=new android.widget.FrameLayout(context);android.view.View descendant=new android.view.View(context);row.addView(descendant);rows.row(row);
  if(rows.getPositionForView(descendant)!=0||rows.getPositionForView(empty)!=-1)throw new AssertionError("Descendant row lookup");
  final int[] selections={0},clicks={0};rows.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int position,long id){if(position!=0||id!=100||v==null)throw new AssertionError("Selection payload");selections[0]++;}public void onNothingSelected(android.widget.AdapterView<?> p){selections[0]++;}});
  rows.setSelection(0);rows.setSelection(0);if(selections[0]!=1||!"row0".equals(rows.getSelectedItem()))throw new AssertionError("Selection deduplication");rows.setSelection(-1);if(selections[0]!=2||rows.getSelectedItemId()!=Long.MIN_VALUE)throw new AssertionError("Selection clear");
  rows.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener(){public void onItemClick(android.widget.AdapterView<?> p,android.view.View v,int position,long id){if(p!=rows||position!=0||id!=100)throw new AssertionError("Click payload");clicks[0]++;}});if(!rows.performItemClick(row,0,100)||clicks[0]!=1)throw new AssertionError("Item click");
  try{rows.addView(new android.view.View(context));throw new AssertionError("External child accepted");}catch(UnsupportedOperationException expected){}
  rows.remove();if(row.getParent()!=null||rows.getLastVisiblePosition()!=-1)throw new AssertionError("Row release");
  System.out.println("ADAPTER_VIEW_SELECTION_OK");
 }
}
