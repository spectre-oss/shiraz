// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.content.res.Resources;
import android.os.Looper;
import android.preference.*;
import android.view.View;
import shiraz.runtime.HostPreferences;
import java.nio.file.Files;
public final class PreferenceScreenSmoke {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static final class Screen extends PreferenceActivity {int clicks;Screen(Context c){attachBaseContext(c);}public boolean onPreferenceTreeClick(PreferenceScreen s,Preference p){clicks++;return true;}}
    static final class Stored extends Preference {Stored(Context c){super(c);}void save(String text){check(persistString(text),"not persisted");}String read(){return getPersistedString("absent");}}
    public static void main(String[] args)throws Exception{
        Looper.prepareMainLooper();
        final HostPreferences storage=new HostPreferences(Files.createTempDirectory("preference-smoke").toFile());
        Context context=new ContextWrapper(null){final Resources.Theme theme=Resources.getSystem().newTheme();public Resources getResources(){return Resources.getSystem();}public Resources.Theme getTheme(){return theme;}public String getPackageName(){return "fixture";}public Object getSystemService(String name){return null;}public SharedPreferences getSharedPreferences(String name,int mode){return storage.get(name,mode);}};
        try{
            Screen activity=new Screen(context);PreferenceScreen root=activity.getPreferenceManager().createPreferenceScreen(activity);
            Stored first=new Stored(activity);first.setKey("value");first.setTitle("Value");first.setSummary("Description");root.addPreference(first);activity.setPreferenceScreen(root);
            first.save("saved");check("saved".equals(first.read()),"stored value");
            View row=activity.getListAdapter().getView(0,null,activity.getListView());
            check("Value".contentEquals(((android.widget.TextView)row.findViewById(0x01020016)).getText()),"title rendering");
            activity.getListView().performItemClick(row,0,0);check(activity.clicks==1,"tree click");
            first.setEnabled(false);activity.getListView().performItemClick(row,0,0);check(activity.clicks==1,"disabled click");first.setEnabled(true);
            first.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener(){public boolean onPreferenceClick(Preference p){return true;}});activity.getListView().performItemClick(row,0,0);check(activity.clicks==1,"consumed click reached tree");
            Preference second=new Preference(activity);second.setOrder(-1);second.setKey("second");root.addPreference(second);check(activity.getListAdapter().getCount()==2&&root.getPreference(0)==second,"live insertion/order");
            check(activity.findPreference("value")==first,"key lookup");root.removePreference(first);check(activity.findPreference("value")==null,"removed key");
            Screen reopened=new Screen(context);PreferenceScreen again=reopened.getPreferenceManager().createPreferenceScreen(reopened);Stored restored=new Stored(reopened);restored.setKey("value");again.addPreference(restored);reopened.setPreferenceScreen(again);check("saved".equals(restored.read()),"screen recreation persistence");
            try{again.addPreference(again);throw new AssertionError("cycle accepted");}catch(IllegalArgumentException expected){}
            try{again.addPreference(second);throw new AssertionError("two parents accepted");}catch(IllegalStateException expected){}
            try{again.addPreference(first);throw new AssertionError("foreign manager accepted");}catch(IllegalStateException expected){}
            check(again.getPreferenceCount()==1,"failed attachment changed hierarchy");
            PreferenceScreen chain=new PreferenceScreen(activity,null);
            for(int i=0;i<32;i++){PreferenceScreen parent=new PreferenceScreen(activity,null);parent.addPreference(chain);chain=parent;}
            try{new PreferenceScreen(activity,null).addPreference(chain);throw new AssertionError("deep subtree accepted");}catch(IllegalStateException expected){}
            try{activity.addPreferencesFromResource(123);throw new AssertionError("XML pretended success");}catch(UnsupportedOperationException expected){}
            System.out.println("PREFERENCE_SCREEN_OK");
        }finally{storage.close();}
    }
}
