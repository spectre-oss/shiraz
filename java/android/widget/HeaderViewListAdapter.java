// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import java.util.ArrayList;import android.view.*;import android.database.DataSetObserver;
/** Fixed rows occupy adapter positions but never enter the recycled row pools. */
public class HeaderViewListAdapter implements WrapperListAdapter {
 private final ArrayList<ListView.FixedViewInfo> headers,footers;private final ListAdapter adapter;
 public HeaderViewListAdapter(ArrayList<ListView.FixedViewInfo> h,ArrayList<ListView.FixedViewInfo> f,ListAdapter a){headers=h==null?new ArrayList<ListView.FixedViewInfo>():h;footers=f==null?new ArrayList<ListView.FixedViewInfo>():f;adapter=a;}
 public ListAdapter getWrappedAdapter(){return adapter;}public int getHeadersCount(){return headers.size();}public int getFootersCount(){return footers.size();}
 private int dataCount(){return adapter==null?0:adapter.getCount();}public int getCount(){return Math.addExact(Math.addExact(headers.size(),dataCount()),footers.size());}
 private void position(int p){if(p<0||p>=getCount())throw new IndexOutOfBoundsException("Header adapter position");}
 private ListView.FixedViewInfo fixed(int p){position(p);return p<headers.size()?headers.get(p):p>=headers.size()+dataCount()?footers.get(p-headers.size()-dataCount()):null;}
 public Object getItem(int p){ListView.FixedViewInfo info=fixed(p);return info==null?adapter.getItem(p-headers.size()):info.data;}
 public long getItemId(int p){return fixed(p)==null?adapter.getItemId(p-headers.size()):-1;}
 public View getView(int p,View old,ViewGroup parent){ListView.FixedViewInfo info=fixed(p);return info==null?adapter.getView(p-headers.size(),old,parent):info.view;}
 public boolean isEnabled(int p){ListView.FixedViewInfo info=fixed(p);return info==null?adapter.isEnabled(p-headers.size()):info.isSelectable;}
 public boolean areAllItemsEnabled(){for(ListView.FixedViewInfo i:headers)if(!i.isSelectable)return false;for(ListView.FixedViewInfo i:footers)if(!i.isSelectable)return false;return adapter==null||adapter.areAllItemsEnabled();}
 public boolean hasStableIds(){return adapter!=null&&adapter.hasStableIds();}public boolean isEmpty(){return adapter==null||adapter.isEmpty();}
 public int getItemViewType(int p){return fixed(p)==null?adapter.getItemViewType(p-headers.size()):AdapterView.ITEM_VIEW_TYPE_HEADER_OR_FOOTER;}public int getViewTypeCount(){return adapter==null?1:adapter.getViewTypeCount();}
 public void registerDataSetObserver(DataSetObserver o){if(adapter!=null)adapter.registerDataSetObserver(o);}public void unregisterDataSetObserver(DataSetObserver o){if(adapter!=null)adapter.unregisterDataSetObserver(o);}
 private boolean remove(ArrayList<ListView.FixedViewInfo> rows,View view){for(int i=0;i<rows.size();i++)if(rows.get(i).view==view){rows.remove(i);return true;}return false;}
 public boolean removeHeader(View v){return remove(headers,v);}public boolean removeFooter(View v){return remove(footers,v);}
}
