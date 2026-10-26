// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.os.Handler;
/** Serialized background filtering with results published on the creating Looper. */
public abstract class Filter {
 public interface FilterListener {void onFilterComplete(int count);}
 protected static class FilterResults {public Object values;public int count;public FilterResults(){}}
 private final Handler results=new Handler();
 private final java.util.concurrent.ThreadPoolExecutor worker=new java.util.concurrent.ThreadPoolExecutor(0,1,1,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<Runnable>(1),new java.util.concurrent.ThreadFactory(){public Thread newThread(Runnable r){Thread t=new Thread(r,"ShirazFilter");t.setDaemon(true);return t;}},new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
 private long generation;
 public final void filter(CharSequence constraint){filter(constraint,null);}
 public final synchronized void filter(CharSequence constraint,final FilterListener listener){
  final String query=constraint==null?null:constraint.toString();if(query!=null&&query.length()>16384)throw new IllegalArgumentException("Filter constraint bound");final long ticket=++generation;
  worker.execute(new Runnable(){public void run(){synchronized(Filter.this){if(ticket!=generation)return;}FilterResults found;try{found=performFiltering(query);}catch(Exception failure){found=new FilterResults();}final FilterResults value=found==null?new FilterResults():found;results.post(new Runnable(){public void run(){synchronized(Filter.this){if(ticket!=generation)return;}publishResults(query,value);if(listener!=null)listener.onFilterComplete(value.count);}});}});
 }
 public CharSequence convertResultToString(Object value){return value==null?"":value.toString();}
 protected abstract FilterResults performFiltering(CharSequence constraint);
 protected abstract void publishResults(CharSequence constraint,FilterResults results);
}
