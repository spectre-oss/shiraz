// SPDX-License-Identifier: GPL-3.0-only
package android.app;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** Activity-owned ListView with lazy default content and normal item callbacks. */
public class ListActivity extends Activity {
    private static final int LIST_ID=0x0102000a, EMPTY_ID=0x01020004;
    private ListView list;
    private ListAdapter adapter;
    protected void onListItemClick(ListView list,View view,int position,long id){}
    @Override public void onContentChanged(){
        super.onContentChanged();
        View candidate=findViewById(LIST_ID);
        if(!(candidate instanceof ListView))throw new RuntimeException("ListActivity content must contain a ListView with android.R.id.list");
        ListView replacement=(ListView)candidate;
        if(list!=null&&list!=replacement)list.setOnItemClickListener(null);
        list=replacement;
        list.setEmptyView(findViewById(EMPTY_ID));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            public void onItemClick(AdapterView<?> parent,View view,int position,long id){onListItemClick((ListView)parent,view,position,id);}
        });
        if(adapter!=null)list.setAdapter(adapter);
    }
    private void ensureList(){
        if(list!=null)return;
        FrameLayout root=new FrameLayout(this);
        ListView view=new ListView(this);view.setId(LIST_ID);
        root.addView(view,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        TextView empty=new TextView(this);empty.setId(EMPTY_ID);empty.setVisibility(View.GONE);
        root.addView(empty,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }
    public ListView getListView(){ensureList();return list;}
    public ListAdapter getListAdapter(){return adapter;}
    public void setListAdapter(ListAdapter value){ensureList();adapter=value;list.setAdapter(value);}
    public void setSelection(int position){getListView().setSelection(position);}
    public int getSelectedItemPosition(){return getListView().getSelectedItemPosition();}
    public long getSelectedItemId(){return getListView().getSelectedItemId();}
    @Override protected void onRestoreInstanceState(Bundle state){ensureList();super.onRestoreInstanceState(state);}
}
