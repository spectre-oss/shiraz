// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.view.*;
import java.util.*;
/** Mutable text-row adapter with reusable views and prefix/word-prefix filtering. */
public class ArrayAdapter<T> extends BaseAdapter implements Filterable {
 private final Context context;private final int resource,textId;private int dropResource;private boolean notify=true;
 private List<T> items,original;private Filter filter;private final Object lock=new Object();
 public ArrayAdapter(Context c,int resource){this(c,resource,0,new ArrayList<T>());}
 public ArrayAdapter(Context c,int resource,int textId){this(c,resource,textId,new ArrayList<T>());}
 public ArrayAdapter(Context c,int resource,T[] values){this(c,resource,0,Arrays.asList(values));}
 public ArrayAdapter(Context c,int resource,int textId,T[] values){this(c,resource,textId,Arrays.asList(values));}
 public ArrayAdapter(Context c,int resource,List<T> values){this(c,resource,0,values);}
 public ArrayAdapter(Context c,int resource,int textId,List<T> values){if(c==null||values==null)throw new NullPointerException();if(values.size()>1000000)throw new IllegalArgumentException("Adapter item bound");context=c;this.resource=dropResource=resource;this.textId=textId;items=values;}
 public Context getContext(){return context;}public int getCount(){synchronized(lock){return items.size();}}public T getItem(int p){synchronized(lock){return items.get(p);}}public long getItemId(int p){return p;}public int getPosition(T value){synchronized(lock){return items.indexOf(value);}}
 private List<T> source(){return original==null?items:original;}private void capacity(int added){if(added<0||source().size()>1000000-added)throw new IllegalArgumentException("Adapter item bound");}
 private void changed(){if(notify)notifyDataSetChanged();}
 public void add(T value){synchronized(lock){capacity(1);source().add(value);}changed();}
 public void addAll(Collection<? extends T> values){synchronized(lock){capacity(values.size());source().addAll(values);}changed();}
 public void addAll(T... values){addAll(Arrays.asList(values));}
 public void insert(T value,int index){synchronized(lock){capacity(1);source().add(index,value);}changed();}
 public void remove(T value){synchronized(lock){source().remove(value);}changed();}
 public void clear(){synchronized(lock){source().clear();}changed();}
 public void sort(Comparator<? super T> comparator){synchronized(lock){Collections.sort(source(),comparator);}changed();}
 public void setNotifyOnChange(boolean value){notify=value;}public void notifyDataSetChanged(){super.notifyDataSetChanged();notify=true;}
 public void setDropDownViewResource(int value){dropResource=value;}
 private View row(int position,View reused,ViewGroup parent,int layout){View view=reused==null?LayoutInflater.from(context).inflate(layout,parent,false):reused;View target=textId==0?view:view.findViewById(textId);if(!(target instanceof TextView))throw new IllegalStateException("ArrayAdapter requires a TextView row or text field ID");T item=getItem(position);((TextView)target).setText(item instanceof CharSequence?(CharSequence)item:item==null?"":item.toString());return view;}
 public View getView(int position,View reused,ViewGroup parent){return row(position,reused,parent,resource);}
 public View getDropDownView(int position,View reused,ViewGroup parent){return row(position,reused,parent,dropResource);}
 public Filter getFilter(){if(filter==null)filter=new Filter(){
  protected FilterResults performFiltering(CharSequence constraint){List<T> source;synchronized(lock){if(original==null)original=new ArrayList<T>(items);source=new ArrayList<T>(original);}String prefix=constraint==null?"":constraint.toString().toLowerCase(Locale.getDefault());List<T> selected=new ArrayList<T>();for(T item:source){String text=item==null?"":item.toString().toLowerCase(Locale.getDefault());boolean match=prefix.isEmpty()||text.startsWith(prefix);if(!match)for(String word:text.split(" "))if(word.startsWith(prefix)){match=true;break;}if(match)selected.add(item);}FilterResults result=new FilterResults();result.values=selected;result.count=selected.size();return result;}
  @SuppressWarnings("unchecked") protected void publishResults(CharSequence constraint,FilterResults result){synchronized(lock){items=result.values==null?new ArrayList<T>():(List<T>)result.values;}if(result.count>0)notifyDataSetChanged();else notifyDataSetInvalidated();}
 };return filter;}
}
