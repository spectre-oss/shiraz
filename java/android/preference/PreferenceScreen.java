// SPDX-License-Identifier: GPL-3.0-only
package android.preference;
import android.content.Context;
import android.util.AttributeSet;
import android.view.*;
import android.widget.*;
public final class PreferenceScreen extends PreferenceGroup {
    private final BaseAdapter adapter=new BaseAdapter(){
        public int getCount(){return getPreferenceCount();}
        public Object getItem(int position){return getPreference(position);}
        public long getItemId(int position){return position;}
        public boolean areAllItemsEnabled(){return false;}
        public boolean isEnabled(int position){Preference p=getPreference(position);return p.isEnabled()&&p.isSelectable();}
        public View getView(int position,View view,ViewGroup parent){return getPreference(position).getView(view,parent);}
    };
    public PreferenceScreen(Context context,AttributeSet attrs){super(context,attrs);}
    public ListAdapter getRootAdapter(){return adapter;}
    void changed(){adapter.notifyDataSetChanged();}
    public void bind(ListView list){list.setAdapter(adapter);list.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?> parent,View view,int position,long id){getPreference(position).performClick(PreferenceScreen.this);}});}
    @Override protected void onClick(){if(getPreferenceCount()>0)throw new UnsupportedOperationException("Nested preference screen dialogs are not implemented");}
}
