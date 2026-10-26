// SPDX-License-Identifier: GPL-3.0-only
import android.widget.*;import android.view.*;import android.content.*;import java.util.*;
public final class ArrayAdapterSmoke {
 static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
 static void filter(final ArrayAdapter<String> adapter,String query,int expected) throws Exception {
  final int[] done={-1};final Thread owner=Thread.currentThread();adapter.getFilter().filter(query,new Filter.FilterListener(){public void onFilterComplete(int count){check(Thread.currentThread()==owner,"Filter publication thread");done[0]=count;}});
  long until=System.nanoTime()+2000000000L;while(done[0]<0&&System.nanoTime()<until){android.os.Looper.myLooper().shirazDispatchDue(100);Thread.sleep(2);}check(done[0]==expected,"Filter result count");
 }
 public static void main(String[] args)throws Exception{
  android.os.Looper.prepareMainLooper();final android.content.res.Resources resources=android.content.res.Resources.shirazOpen(args[0],96,160);
  Context context=new ContextWrapper(null){public android.content.res.Resources getResources(){return resources;}public android.content.res.Resources.Theme getTheme(){return resources.newTheme();}public Object getSystemService(String name){return name.equals(Context.LAYOUT_INFLATER_SERVICE)?new shiraz.runtime.HostLayoutInflater(this):null;}};
  try{int layout=resources.getIdentifier("array_adapter_row","layout","org.shiraz.fixture");ArrayAdapter<String> adapter=new ArrayAdapter<String>(context,layout,new ArrayList<String>(Arrays.asList("Vienna","New York","Yorkshire")));
   final int[] changed={0},invalid={0};adapter.registerDataSetObserver(new android.database.DataSetObserver(){public void onChanged(){changed[0]++;}public void onInvalidated(){invalid[0]++;}});
   TextView row=(TextView)adapter.getView(0,null,new FrameLayout(context));check(row.getText().toString().equals("Vienna"),"Inflated text row");check(adapter.getView(1,row,null)==row&&row.getText().toString().equals("New York"),"Reused row rebound");
   adapter.setNotifyOnChange(false);adapter.add("Berlin");adapter.insert("Rome",0);check(changed[0]==0&&adapter.getPosition("Rome")==0,"Batched mutations");adapter.notifyDataSetChanged();adapter.remove("Rome");check(changed[0]==2,"Explicit notification reenables automatic notification");
   filter(adapter,"yor",2);check(adapter.getItem(0).equals("New York")&&adapter.getItem(1).equals("Yorkshire"),"Word prefix matching");adapter.add("York");filter(adapter,"yor",3);filter(adapter,"zzzzz",0);check(invalid[0]==1,"Empty filter invalidates");filter(adapter,null,5);check(adapter.getCount()==5&&adapter.getPosition("Berlin")>=0,"Filter reset preserves original and mutations");
   adapter.clear();filter(adapter,null,0);check(adapter.isEmpty(),"Clear restored list");
   System.out.println("ARRAY_ADAPTER_OK");
  }finally{resources.shirazClose();android.os.Looper.shirazShutdownMain();}
 }
}
