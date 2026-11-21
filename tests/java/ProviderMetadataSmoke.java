// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.net.Uri;
import java.util.Properties;

public class ProviderMetadataSmoke {
    static ProviderInfo attached;
    static final java.util.List<String> created=new java.util.ArrayList<String>();
    public static class Probe extends ContentProvider {
        public void attachInfo(Context context, ProviderInfo info) { attached=info; super.attachInfo(context,info); }
        public boolean onCreate() { return true; }
        public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
        public String getType(Uri u){return null;}
        public Uri insert(Uri u,ContentValues v){return null;}
        public int delete(Uri u,String s,String[] a){return 0;}
        public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
    }
    public static class First extends Probe {public boolean onCreate(){created.add("first");return true;}}
    public static class Second extends Probe {public boolean onCreate(){created.add("second");return true;}}
    public static class Other extends Probe {public boolean onCreate(){created.add("other");return true;}}
    static void processSelection()throws Exception{
        final String pkg="org.shiraz.providerprocess";
        final Properties facts=new Properties();facts.setProperty("package",pkg);
        facts.setProperty("sourceDir","/unused/fixture.apk");facts.setProperty("dataDir","/unused/data");
        facts.setProperty("provider.count","4");
        String[] names={Second.class.getName(),First.class.getName(),Other.class.getName(),Probe.class.getName()};
        for(int i=0;i<names.length;i++)facts.setProperty("provider."+i+".name",names[i]);
        facts.setProperty("provider.0.process",":data");facts.setProperty("provider.0.initOrder","-2147483648");
        facts.setProperty("provider.1.process",pkg+":data");facts.setProperty("provider.1.initOrder","2147483647");
        facts.setProperty("provider.3.process",":data");facts.setProperty("provider.3.enabled","false");
        Context context=new ContextWrapper(null){
            public String getPackageName(){return pkg;}
            public PackageManager getPackageManager(){return new HostPackageManager(facts,this);}
        };
        String previous=android.app.Application.getProcessName();
        try{
            android.app.Application.shirazSetProcessName(pkg+":data");created.clear();
            if(ProviderHost.attach(context,facts).size()!=2||!created.equals(java.util.Arrays.asList("first","second")))throw new AssertionError("private process provider selection/order: "+created);
            android.app.Application.shirazSetProcessName(pkg);created.clear();
            if(ProviderHost.attach(context,facts).size()!=1||!created.equals(java.util.Arrays.asList("other")))throw new AssertionError("main process provider selection: "+created);
            android.app.Application.shirazSetProcessName(pkg+":data");
            for(String invalid:new String[]{"not-an-integer","2147483648"}){
                facts.setProperty("provider.0.initOrder",invalid);created.clear();
                try{ProviderHost.attach(context,facts);throw new AssertionError("malformed provider order accepted");}catch(IllegalArgumentException expected){}
                if(!created.isEmpty())throw new AssertionError("provider created before declaration validation");
            }
        }finally{android.app.Application.shirazSetProcessName(previous==null?pkg:previous);}
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
        facts.setProperty("provider.0.readPermission","fixture.READ");
        facts.setProperty("provider.0.writePermission","fixture.WRITE");
        facts.setProperty("provider.0.initOrder","17");
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
        if(!"fixture.READ".equals(attached.readPermission)||!"fixture.WRITE".equals(attached.writePermission)||attached.initOrder!=17)throw new AssertionError("provider access facts lost during attachment");
        if(attached.metaData==null || attached.metaData.getInt("fixture.paths")!=2131951616)throw new AssertionError("provider metadata");
    }
    static void activityBootMatching() throws Exception {
        Properties facts=new Properties();facts.setProperty("package","org.shiraz.bootfixture");
        facts.setProperty("sourceDir","/unused/fixture.apk");facts.setProperty("dataDir","/unused/data");
        facts.setProperty("activity.count","1");facts.setProperty("activity.0.name","org.shiraz.bootfixture.Screen");
        facts.setProperty("activity.0.directBootAware","true");
        PackageManager pm=new HostPackageManager(facts,new ContextWrapper(null));
        ComponentName name=new ComponentName("org.shiraz.bootfixture","org.shiraz.bootfixture.Screen");
        int auto=PackageManager.MATCH_DIRECT_BOOT_AUTO;
        if(!pm.getActivityInfo(name,auto).directBootAware || !pm.getActivityInfo(name,auto|PackageManager.MATCH_DIRECT_BOOT_AWARE|PackageManager.MATCH_DIRECT_BOOT_UNAWARE).directBootAware)throw new AssertionError("automatic boot matching");
        try{pm.getActivityInfo(name,auto|PackageManager.MATCH_DIRECT_BOOT_UNAWARE);throw new AssertionError("explicit boot filter ignored");}catch(PackageManager.NameNotFoundException expected){}
        facts.setProperty("activity.0.enabled","false");
        pm=new HostPackageManager(facts,new ContextWrapper(null));
        try{pm.getActivityInfo(name,auto);throw new AssertionError("disabled activity matched");}catch(PackageManager.NameNotFoundException expected){}
    }
    public static void main(String[] args)throws Exception {
        activityBootMatching();
        check(36,null,false);check(16,null,true);check(16,"false",false);check(36,"true",true);
        processSelection();
        System.out.println("PROVIDER_METADATA_OK");
    }
}
