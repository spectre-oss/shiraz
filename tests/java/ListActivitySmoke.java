// SPDX-License-Identifier: GPL-3.0-only
import android.app.ListActivity;
import android.content.*;
import android.content.res.Resources;
import android.os.Looper;
import android.view.*;
import android.widget.*;
public final class ListActivitySmoke {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static final class Screen extends ListActivity {
        int clicks,last=-1;
        Screen(Context context){attachBaseContext(context);}
        protected void onListItemClick(ListView list,View view,int position,long id){clicks++;last=position;}
    }
    public static void main(String[] args){
        Looper.prepareMainLooper();
        Context context=new ContextWrapper(null){final Resources.Theme theme=Resources.getSystem().newTheme();public Resources getResources(){return Resources.getSystem();}public Resources.Theme getTheme(){return theme;}public String getPackageName(){return "fixture";}public Object getSystemService(String name){return null;}};
        Screen screen=new Screen(context);
        ListView initial=screen.getListView();
        check(screen.getListView()==initial,"lazy list replaced");
        BaseAdapter rows=new BaseAdapter(){public int getCount(){return 3;}public Object getItem(int position){return position;}public long getItemId(int position){return 10+position;}public View getView(int position,View recycled,ViewGroup parent){TextView row=new TextView(parent.getContext());row.setText("Row "+position);return row;}};
        screen.setListAdapter(rows);screen.setSelection(1);
        check(screen.getSelectedItemPosition()==1&&screen.getSelectedItemId()==11,"selection delegation");
        initial.performItemClick(new View(context),2,12);check(screen.clicks==1&&screen.last==2,"item callback");
        FrameLayout content=new FrameLayout(context);ListView replacement=new ListView(context);replacement.setId(0x0102000a);content.addView(replacement);TextView empty=new TextView(context);empty.setId(0x01020004);content.addView(empty);
        screen.setContentView(content);check(screen.getListView()==replacement&&replacement.getAdapter()==rows,"custom content did not retain adapter");
        initial.performItemClick(new View(context),0,10);check(screen.clicks==1,"old list retained activity callback");
        replacement.performItemClick(new View(context),0,10);check(screen.clicks==2,"replacement callback");
        screen.setListAdapter(null);check(screen.getListAdapter()==null&&replacement.getVisibility()==View.GONE&&empty.getVisibility()==View.VISIBLE,"empty view state");
        try{screen.setContentView(new TextView(context));throw new AssertionError("invalid content accepted");}catch(RuntimeException expected){}
        System.out.println("LIST_ACTIVITY_OK");
    }
}
