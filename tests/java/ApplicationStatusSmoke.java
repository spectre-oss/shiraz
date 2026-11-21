// SPDX-License-Identifier: GPL-3.0-only
import java.util.Properties;
import android.content.pm.*;
import shiraz.runtime.HostPackageManager;
public final class ApplicationStatusSmoke {
    public static void main(String[] args)throws Exception{
        Properties facts=new Properties();facts.setProperty("package","fixture");facts.setProperty("sourceDir","/fixture.apk");facts.setProperty("dataDir","/fixture-data");
        for(String enabled:new String[]{"true","false"}){
            facts.setProperty("enabled",enabled);HostPackageManager packages=new HostPackageManager(facts,null);
            ApplicationInfo app=packages.getApplicationInfo("fixture",0);
            if(app.enabled!=Boolean.parseBoolean(enabled)||app.isInstantApp()||packages.getApplicationEnabledSetting("fixture")!=PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)throw new AssertionError("manifest state confused with override");
        }
        for(int version:new int[]{1,2}){
            facts.setProperty("applicationInfo.targetSandboxVersion",Integer.toString(version));
            if(new HostPackageManager(facts,null).getApplicationInfo("fixture",0).targetSandboxVersion!=version)throw new AssertionError("sandbox metadata lost");
        }
        if(new HostPackageManager(facts,null).getSystemSharedLibraryNames()!=null)throw new AssertionError("Invented optional library");
        facts.setProperty("runtime.library.apacheHttpLegacy","true");HostPackageManager libraries=new HostPackageManager(facts,null);
        String[] names=libraries.getSystemSharedLibraryNames();if(names.length!=1||!"org.apache.http.legacy".equals(names[0]))throw new AssertionError("Missing configured library");
        names[0]="mutated";facts.setProperty("runtime.library.apacheHttpLegacy","false");if(!"org.apache.http.legacy".equals(libraries.getSystemSharedLibraryNames()[0]))throw new AssertionError("Mutable library inventory");
        HostPackageManager singleUser=new HostPackageManager(facts,null);
        for(int flags:new int[]{PackageManager.MATCH_ANY_USER,PackageManager.MATCH_KNOWN_PACKAGES}){
            if(!"fixture".equals(singleUser.getPackageInfo("fixture",flags).packageName)||!"fixture".equals(singleUser.getApplicationInfo("fixture",flags).packageName))throw new AssertionError("Single-user package query lost installed package");
        }
        try{singleUser.getPackageInfo("fixture",0x40000000);throw new AssertionError("Unknown package flag accepted");}catch(UnsupportedOperationException expected){}
        System.out.println("APPLICATION_STATUS_OK");
    }
}
