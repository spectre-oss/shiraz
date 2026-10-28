// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.net.Uri;
import java.util.Properties;

public class ProviderMetadataSmoke {
    static ProviderInfo attached;
    public static class Probe extends ContentProvider {
        public void attachInfo(Context context, ProviderInfo info) { attached=info; super.attachInfo(context,info); }
        public boolean onCreate() { return true; }
        public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
        public String getType(Uri u){return null;}
        public Uri insert(Uri u,ContentValues v){return null;}
        public int delete(Uri u,String s,String[] a){return 0;}
        public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
    }
    static void check(int target,String exported,boolean expected) throws Exception {
        final Properties facts=new Properties();
        facts.setProperty("package","org.shiraz.providerfixture");
        facts.setProperty("sourceDir","/unused/fixture.apk");
        facts.setProperty("dataDir","/unused/data");
        facts.setProperty("targetSdk",Integer.toString(target));
        facts.setProperty("provider.count","1");
        facts.setProperty("provider.0.name",Probe.class.getName());
        facts.setProperty("provider.0.grantUriPermissions","true");
        if(exported!=null)facts.setProperty("provider.0.exported",exported);
        facts.setProperty("provider.0.metadata.count","1");
        facts.setProperty("provider.0.metadata.0.name","fixture.paths");
        facts.setProperty("provider.0.metadata.0.kind","resource");
        facts.setProperty("provider.0.metadata.0.value","2131951616");
        Context context=new ContextWrapper(null){
            public String getPackageName(){return facts.getProperty("package");}
            public PackageManager getPackageManager(){return new HostPackageManager(facts,this);}
        };
        attached=null;
        if(ProviderHost.attach(context,facts).size()!=1 || attached==null)throw new AssertionError("provider not attached");
        if(attached.exported!=expected)throw new AssertionError("export default: "+target);
        if(!attached.grantUriPermissions || attached.applicationInfo.targetSdkVersion!=target)throw new AssertionError("provider facts");
        if(attached.metaData==null || attached.metaData.getInt("fixture.paths")!=2131951616)throw new AssertionError("provider metadata");
    }
    public static void main(String[] args)throws Exception {
        check(36,null,false);check(16,null,true);check(16,"false",false);check(36,"true",true);
        System.out.println("PROVIDER_METADATA_OK");
    }
}
