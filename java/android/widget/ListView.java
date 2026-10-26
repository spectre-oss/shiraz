// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.util.AttributeSet;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
public class ListView extends AbsListView {
 public class FixedViewInfo {public android.view.View view;public Object data;public boolean isSelectable;}
 private final java.util.ArrayList<FixedViewInfo> headers=new java.util.ArrayList<FixedViewInfo>(),footers=new java.util.ArrayList<FixedViewInfo>();
 private ListAdapter sourceAdapter;private HeaderViewListAdapter fixedAdapter;private boolean adapterAssigned;
 @Override public void setAdapter(ListAdapter adapter){sourceAdapter=adapter;adapterAssigned=true;fixedAdapter=headers.isEmpty()&&footers.isEmpty()?null:new HeaderViewListAdapter(headers,footers,adapter);super.setAdapter(fixedAdapter==null?adapter:fixedAdapter);}
 public int getHeaderViewsCount(){return headers.size();}public int getFooterViewsCount(){return footers.size();}
 public void addHeaderView(android.view.View view){addHeaderView(view,null,true);}public void addFooterView(android.view.View view){addFooterView(view,null,true);}
 public void addHeaderView(android.view.View view,Object data,boolean selectable){addFixed(headers,view,data,selectable);}public void addFooterView(android.view.View view,Object data,boolean selectable){addFixed(footers,view,data,selectable);}
 private void addFixed(java.util.ArrayList<FixedViewInfo> rows,android.view.View view,Object data,boolean selectable){if(view==null)throw new NullPointerException("Fixed row");if(view.getParent()!=null)throw new IllegalStateException("Fixed row already parented");for(FixedViewInfo i:headers)if(i.view==view)throw new IllegalArgumentException("Duplicate fixed row");for(FixedViewInfo i:footers)if(i.view==view)throw new IllegalArgumentException("Duplicate fixed row");if(headers.size()+footers.size()>=256)throw new IllegalStateException("Fixed row limit");FixedViewInfo info=new FixedViewInfo();info.view=view;info.data=data;info.isSelectable=selectable;rows.add(info);if(adapterAssigned){if(fixedAdapter==null){fixedAdapter=new HeaderViewListAdapter(headers,footers,sourceAdapter);super.setAdapter(fixedAdapter);}else shirazDataChanged();}requestLayout();}
 private boolean removeFixed(java.util.ArrayList<FixedViewInfo> rows,android.view.View view){for(int i=0;i<rows.size();i++)if(rows.get(i).view==view){rows.remove(i);if(adapterAssigned)shirazDataChanged();requestLayout();return true;}return false;}
 public boolean removeHeaderView(android.view.View view){return removeFixed(headers,view);}public boolean removeFooterView(android.view.View view){return removeFixed(footers,view);}
 private Drawable divider;private int dividerHeight;
 private boolean headerDividers=true,footerDividers=true;
 public void setHeaderDividersEnabled(boolean enabled){headerDividers=enabled;invalidate();}public boolean areHeaderDividersEnabled(){return headerDividers;}
 public void setFooterDividersEnabled(boolean enabled){footerDividers=enabled;invalidate();}public boolean areFooterDividersEnabled(){return footerDividers;}
 private boolean dividerAfter(int position){return position+1<getCount()&&(headerDividers||position>=getHeaderViewsCount())&&(footerDividers||position<getCount()-getFooterViewsCount()-1);}

 public ListView(Context c){this(c,null);}public ListView(Context c,AttributeSet a){this(c,a,0x01010074);}public ListView(Context c,AttributeSet a,int s){this(c,a,s,0);}public ListView(Context c,AttributeSet a,int s,int r){super(c,a,s,r);if(a!=null||s!=0||r!=0){shiraz.runtime.XmlAttributes x=new shiraz.runtime.XmlAttributes(c,a,s,r);if(x.has("divider"))setDivider(x.drawable("divider"));if(x.has("dividerHeight"))setDividerHeight(x.dimension("dividerHeight",0,false));}}
 public void setDivider(Drawable value){divider=value;dividerHeight=value==null?0:Math.max(0,value.getIntrinsicHeight());requestLayout();invalidate();}public Drawable getDivider(){return divider;}
 public void setDividerHeight(int value){if(value<0||value>8192)throw new IllegalArgumentException("Divider height");dividerHeight=value;requestLayout();invalidate();}public int getDividerHeight(){return dividerHeight;}
 @Override protected int rowGap(){return dividerHeight;}
 @Override protected void dispatchDraw(Canvas canvas){super.dispatchDraw(canvas);if(divider!=null&&dividerHeight>0)for(int i=0;i<getChildCount();i++)if(dividerAfter(getFirstVisiblePosition()+i)){int y=getChildAt(i).getBottom();divider.setBounds(getListPaddingLeft(),y,getWidth()-getListPaddingRight(),y+dividerHeight);divider.draw(canvas);}}
}
