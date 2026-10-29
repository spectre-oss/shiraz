// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import java.util.Properties;

/** Immutable manifest facts with fresh mutable Android metadata snapshots. */
public final class HostPackageManager extends PackageManager {
    @Override public int checkPermission(String permission,String packageName){
        return name.equals(packageName)&&name.equals(context.getPackageName())
                ? context.checkSelfPermission(permission) : PERMISSION_DENIED;
    }
    @Override public java.util.List<PackageInfo> getInstalledPackages(int flags){java.util.ArrayList<PackageInfo> result=new java.util.ArrayList<>();try{result.add(getPackageInfo(name,flags));for(String pkg:HostAppInstaller.packages())if(!name.equals(pkg))result.add(getPackageInfo(pkg,flags));}catch(NameNotFoundException e){throw new IllegalStateException(e);}return result;}
    @Override public android.content.Intent getLaunchIntentForPackage(String pkg){if(!name.equals(pkg))return HostAppInstaller.launchIntent(pkg);android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER).setPackage(pkg);android.content.pm.ResolveInfo info=resolveActivity(intent,0);if(info!=null)return intent.setComponent(new android.content.ComponentName(pkg,info.activityInfo.name)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        // Launcher aliases are resolved to their declared target by the APK parser.
        int count=integer("launcher.count",0);if(count<0||count>256)throw new IllegalArgumentException("Launcher count");
        for(int i=0;i<count;i++){android.content.ComponentName component=new android.content.ComponentName(pkg,required("launcher."+i+".target"));try{getActivityInfo(component,0);return intent.setComponent(component).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);}catch(NameNotFoundException disabled){}}
        return null;}
    @Override public String getInstallerPackageName(String pkg){Properties facts=HostAppInstaller.metadata(pkg);return facts==null?null:facts.getProperty("installer");}
    private android.content.pm.PackageInstaller installer;
    @Override public synchronized android.content.pm.PackageInstaller getPackageInstaller(){if(installer==null)installer=new android.content.pm.PackageInstaller(context);return installer;}
    @Override public boolean canRequestPackageInstalls(){return true;}
    private final Properties values = new Properties();
    private final String name;
    private final android.content.Context context;
    public HostPackageManager(Properties source, android.content.Context context) {
        this.context = context;
        values.putAll(source);
        name = required("package");
    }
    private String packageString(int resource){
        if(name.equals(context.getPackageName()))return context.getString(resource);
        android.content.res.Resources resources=android.content.res.Resources.shirazOpen(required("sourceDir"),360,640);
        try{return resources.getString(resource);}finally{resources.shirazClose();}
    }
    private String required(String key) {
        String value = values.getProperty(key);
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("Missing package metadata: " + key);
        return value;
    }
    private static final int PACKAGE_QUERY_FLAGS=GET_SIGNING_CERTIFICATES|GET_SIGNATURES|GET_META_DATA|GET_PERMISSIONS|GET_DISABLED_COMPONENTS|MATCH_DISABLED_COMPONENTS|MATCH_DIRECT_BOOT_AWARE|MATCH_DIRECT_BOOT_UNAWARE|0x1|0x2|0x4|0x8|0x10|0x20|0x2000|0x4000|0x8000;
    private void query(String packageName, int flags) throws NameNotFoundException {
        if (!name.equals(packageName)) throw new NameNotFoundException(packageName);
        if ((flags&~PACKAGE_QUERY_FLAGS)!=0) throw new UnsupportedOperationException("Package query flags are not implemented: " + flags);
    }
    private int integer(String key, int fallback) {
        String value = values.getProperty(key);
        return value == null || value.isEmpty() ? fallback : Integer.parseInt(value);
    }
    private int resourceId(String key) {
        long id = Long.parseLong(values.getProperty(key, "0"));
        if (id < 0 || id > 0xffffffffL) throw new IllegalArgumentException("Resource ID range");
        return (int) id;
    }
    private boolean bool(String key, boolean fallback) {
        String value = values.getProperty(key);
        if (value == null || value.isEmpty()) return fallback;
        if ("true".equals(value)) return true;
        if ("false".equals(value)) return false;
        String reference = values.getProperty(key + "Res");
        if (reference != null && !reference.isEmpty()) return context.getResources().getBoolean(resourceId(key + "Res"));
        throw new UnsupportedOperationException("Manifest boolean requires resource resolution: " + key);
    }
    private int metadataResource(String prefix,String kind,String value) {
        if(kind.endsWith("_name")) {
            int id=context.getResources().getIdentifier(value,null,name);
            if(id==0)throw new android.content.res.Resources.NotFoundException("Component metadata resource");return id;
        }
        long id=Long.parseLong(value);if(id<=0||id>0xffffffffL)throw new IllegalArgumentException("Component metadata resource ID");return (int)id;
    }
    private android.os.Bundle componentMetadata(String prefix) {
        int count=integer(prefix+"metadata.count",0);if(count<0||count>128)throw new IllegalArgumentException("Component metadata count");if(count==0)return null;
        android.os.Bundle result=new android.os.Bundle();
        for(int i=0;i<count;i++) {
            String item=prefix+"metadata."+i+".", key=required(item+"name"),kind=required(item+"kind"),value=values.getProperty(item+"value");
            int nameResource=resourceId(item+"name_resource");if(nameResource!=0)key=context.getString(nameResource);
            if(key.isEmpty()||key.length()>4096||value==null||value.length()>65536)throw new IllegalArgumentException("Component metadata value bounds");
            if(kind.equals("resource")||kind.equals("resource_name"))result.putInt(key,metadataResource(item,kind,value));
            else if(kind.equals("reference")||kind.equals("reference_name")) {
                android.util.TypedValue typed=new android.util.TypedValue();context.getResources().getValue(metadataResource(item,kind,value),typed,true);
                if(typed.type==3)result.putString(key,typed.string==null?null:typed.string.toString());
                else if(typed.type==4)result.putFloat(key,typed.getFloat());
                else if(typed.type==18)result.putBoolean(key,typed.data!=0);
                else if(typed.type>=16&&typed.type<=31)result.putInt(key,typed.data);
                else throw new UnsupportedOperationException("Unsupported component metadata resource type "+typed.type);
            }
            else if(kind.equals("string"))result.putString(key,value);
            else if(kind.equals("boolean"))result.putBoolean(key,bool(item+"value",false));
            else if(kind.equals("integer"))result.putInt(key,Integer.parseInt(value));
            else if(kind.equals("float"))result.putFloat(key,Float.parseFloat(value));
            else if(kind.equals("float_bits")){long bits=Long.parseLong(value);if(bits<0||bits>0xffffffffL)throw new IllegalArgumentException("Metadata float bits");result.putFloat(key,Float.intBitsToFloat((int)bits));}
            else throw new UnsupportedOperationException("Unsupported component metadata type "+kind);
        }
        return result;
    }
    @Override public java.util.List<android.content.pm.ResolveInfo> queryIntentActivities(android.content.Intent intent,int flags){
        if(intent==null)throw new NullPointerException("intent");
        int selectorDepth=0;while(intent.getComponent()==null&&intent.getSelector()!=null){if(++selectorDepth>32)throw new IllegalArgumentException("Intent selector depth");intent=intent.getSelector();}
        int allowed=GET_META_DATA|MATCH_DISABLED_COMPONENTS|MATCH_DIRECT_BOOT_AWARE|MATCH_DIRECT_BOOT_UNAWARE|MATCH_DEFAULT_ONLY|GET_RESOLVED_FILTER|MATCH_ALL|MATCH_SYSTEM_ONLY;
        if((flags&~allowed)!=0)throw new UnsupportedOperationException("Activity resolution flags: "+flags);
        java.util.ArrayList<android.content.pm.ResolveInfo> result=new java.util.ArrayList<android.content.pm.ResolveInfo>();
        // Packaged APKs and the in-app camera adapter are not Android system packages.
        if((flags&MATCH_SYSTEM_ONLY)!=0)return result;
        android.content.pm.ResolveInfo capture=HostCameraCapture.resolve(context,intent);if(capture!=null){result.add(capture);return result;}
        if(intent.getPackage()!=null&&!name.equals(intent.getPackage()))return result;
        int count=integer("activity.count",0);if(count<0||count>256)throw new IllegalArgumentException("Activity count");
        for(int i=0;i<count;i++){
            String prefix="activity."+i+".";android.content.ComponentName component=new android.content.ComponentName(name,required(prefix+"name"));
            if(intent.getComponent()!=null&&!component.equals(intent.getComponent()))continue;
            android.content.pm.ActivityInfo info;try{info=getActivityInfo(component,flags&~(MATCH_DEFAULT_ONLY|GET_RESOLVED_FILTER|MATCH_ALL|MATCH_SYSTEM_ONLY));}catch(NameNotFoundException absent){continue;}
            android.content.pm.ResolveInfo best=null;
            if(intent.getComponent()!=null){best=new android.content.pm.ResolveInfo();}
            else {int filters=integer(prefix+"filter.count",0);if(filters<0||filters>128)throw new IllegalArgumentException("Filter count");
                for(int j=0;j<filters;j++){
                    String key=prefix+"filter."+j+".";android.content.IntentFilter filter=new android.content.IntentFilter();
                    for(String kind:new String[]{"action","category","scheme","mime"}){int n=integer(key+kind+".count",0);if(n<0||n>128)throw new IllegalArgumentException("Filter entries");for(int k=0;k<n;k++){String value=required(key+kind+"."+k);if(value.length()>4096)throw new IllegalArgumentException("Filter value");if(kind.equals("action"))filter.addAction(value);else if(kind.equals("category"))filter.addCategory(value);else if(kind.equals("scheme"))filter.addDataScheme(value);else try{filter.addDataType(value);}catch(android.content.IntentFilter.MalformedMimeTypeException bad){throw new IllegalArgumentException(bad);}}}
                    if(intent.getAction()!=null&&!filter.hasAction(intent.getAction()))continue;
                    boolean defaultCategory=filter.hasCategory(android.content.Intent.CATEGORY_DEFAULT);if((flags&MATCH_DEFAULT_ONLY)!=0&&!defaultCategory)continue;
                    int match=filter.match(intent.getAction(),intent.getType(),intent.getScheme(),intent.getData(),intent.getCategories(),"Shiraz");if(match<0)continue;
                    if(bool(key+"unsupported",false))throw new UnsupportedOperationException("Activity authority/path matching");
                    filter.setPriority(integer(key+"priority",0));
                    if(best==null||filter.getPriority()>best.priority){best=new android.content.pm.ResolveInfo();best.priority=filter.getPriority();best.match=match;best.isDefault=defaultCategory;if((flags&GET_RESOLVED_FILTER)!=0)best.filter=filter;}
                }
            }
            if(best!=null){best.activityInfo=info;result.add(best);}
        }
        java.util.Collections.sort(result,new java.util.Comparator<android.content.pm.ResolveInfo>(){public int compare(android.content.pm.ResolveInfo a,android.content.pm.ResolveInfo b){int order=Integer.compare(b.priority,a.priority);return order!=0?order:Integer.compare(b.match,a.match);}});
        return result;
    }
    @Override public java.util.List<android.content.pm.ResolveInfo> queryIntentContentProviders(android.content.Intent intent,int flags){
        if(intent==null)throw new NullPointerException("intent");
        int allowed=GET_META_DATA|MATCH_DISABLED_COMPONENTS|MATCH_DIRECT_BOOT_AWARE|MATCH_DIRECT_BOOT_UNAWARE|MATCH_DEFAULT_ONLY|GET_RESOLVED_FILTER;
        if((flags&~allowed)!=0)throw new UnsupportedOperationException("Provider resolution flags: "+flags);
        java.util.ArrayList<android.content.pm.ResolveInfo> result=new java.util.ArrayList<android.content.pm.ResolveInfo>();
        if(intent.getPackage()!=null&&!name.equals(intent.getPackage()))return result;
        int count=integer("provider.count",0);if(count<0||count>256)throw new IllegalArgumentException("Provider count");
        for(int i=0;i<count;i++){
            String prefix="provider."+i+".";android.content.ComponentName component=new android.content.ComponentName(name,required(prefix+"name"));
            if(intent.getComponent()!=null&&!component.equals(intent.getComponent()))continue;
            android.content.pm.ProviderInfo info;try{info=getProviderInfo(component,flags&~(MATCH_DEFAULT_ONLY|GET_RESOLVED_FILTER));}catch(NameNotFoundException absent){continue;}
            if(!info.isEnabled()&&(flags&MATCH_DISABLED_COMPONENTS)==0)continue;
            android.content.pm.ResolveInfo best=null;
            if(intent.getComponent()!=null){best=new android.content.pm.ResolveInfo();}
            else {int filters=integer(prefix+"filter.count",0);if(filters<0||filters>128)throw new IllegalArgumentException("Filter count");
                for(int j=0;j<filters;j++){
                    String key=prefix+"filter."+j+".";android.content.IntentFilter filter=new android.content.IntentFilter();
                    for(String kind:new String[]{"action","category","scheme","mime"}){int n=integer(key+kind+".count",0);if(n<0||n>128)throw new IllegalArgumentException("Filter entries");for(int k=0;k<n;k++){String value=required(key+kind+"."+k);if(value.length()>4096)throw new IllegalArgumentException("Filter value");if(kind.equals("action"))filter.addAction(value);else if(kind.equals("category"))filter.addCategory(value);else if(kind.equals("scheme"))filter.addDataScheme(value);else try{filter.addDataType(value);}catch(android.content.IntentFilter.MalformedMimeTypeException bad){throw new IllegalArgumentException(bad);}}}
                    if(intent.getAction()!=null&&!filter.hasAction(intent.getAction()))continue;
                    boolean defaultCategory=filter.hasCategory(android.content.Intent.CATEGORY_DEFAULT);if((flags&MATCH_DEFAULT_ONLY)!=0&&!defaultCategory)continue;
                    int match=filter.match(intent.getAction(),intent.getType(),intent.getScheme(),intent.getData(),intent.getCategories(),"Shiraz");if(match<0)continue;
                    if(bool(key+"unsupported",false))throw new UnsupportedOperationException("Provider authority/path matching");
                    filter.setPriority(integer(key+"priority",0));
                    if(best==null||filter.getPriority()>best.priority){best=new android.content.pm.ResolveInfo();best.priority=filter.getPriority();best.match=match;best.isDefault=defaultCategory;if((flags&GET_RESOLVED_FILTER)!=0)best.filter=filter;}
                }
            }
            if(best!=null){best.providerInfo=info;result.add(best);}
        }
        java.util.Collections.sort(result,new java.util.Comparator<android.content.pm.ResolveInfo>(){public int compare(android.content.pm.ResolveInfo a,android.content.pm.ResolveInfo b){int order=Integer.compare(b.priority,a.priority);return order!=0?order:Integer.compare(b.match,a.match);}});
        return result;
    }
    @Override public java.util.List<android.content.pm.ResolveInfo> queryIntentServices(android.content.Intent intent,int flags){
        if(intent==null)throw new NullPointerException("intent");
        int allowed=GET_META_DATA|MATCH_DISABLED_COMPONENTS|MATCH_DIRECT_BOOT_AWARE|MATCH_DIRECT_BOOT_UNAWARE|GET_RESOLVED_FILTER;
        if((flags&~allowed)!=0)throw new UnsupportedOperationException("Service resolution flags: "+flags);
        java.util.ArrayList<android.content.pm.ResolveInfo> result=new java.util.ArrayList<android.content.pm.ResolveInfo>();
        android.content.pm.ResolveInfo capture=HostCameraCapture.resolve(context,intent);if(capture!=null){result.add(capture);return result;}
        if(intent.getPackage()!=null&&!name.equals(intent.getPackage()))return result;
        int count=integer("service.count",0);if(count<0||count>256)throw new IllegalArgumentException("Service count");
        for(int i=0;i<count;i++){
            String prefix="service."+i+".";android.content.ComponentName component=new android.content.ComponentName(name,required(prefix+"name"));
            if(intent.getComponent()!=null&&!component.equals(intent.getComponent()))continue;
            android.content.pm.ServiceInfo info;try{info=getServiceInfo(component,flags&~GET_RESOLVED_FILTER);}catch(NameNotFoundException absent){continue;}
            android.content.pm.ResolveInfo best=null;
            if(intent.getComponent()!=null)best=new android.content.pm.ResolveInfo();
            else{int filters=integer(prefix+"filter.count",0);if(filters<0||filters>128)throw new IllegalArgumentException("Filter count");
                for(int j=0;j<filters;j++){
                    String key=prefix+"filter."+j+".";android.content.IntentFilter filter=new android.content.IntentFilter();
                    int n=integer(key+"action.count",0);if(n<0||n>128)throw new IllegalArgumentException("Filter entries");
                    for(int k=0;k<n;k++)filter.addAction(required(key+"action."+k));
                    if(intent.getAction()==null||!filter.hasAction(intent.getAction()))continue;
                    filter.setPriority(integer(key+"priority",0));
                    if(best==null||filter.getPriority()>best.priority){best=new android.content.pm.ResolveInfo();best.priority=filter.getPriority();if((flags&GET_RESOLVED_FILTER)!=0)best.filter=filter;}
                }
            }
            if(best!=null){best.serviceInfo=info;result.add(best);}
        }
        return result;
    }
    @Override public android.content.pm.ActivityInfo getReceiverInfo(android.content.ComponentName component,int flags) throws NameNotFoundException {
        if(component==null)throw new NullPointerException("component");
        if(!name.equals(component.getPackageName()))throw new NameNotFoundException(component.flattenToString());
        int allowed=GET_META_DATA|MATCH_DISABLED_COMPONENTS|MATCH_DIRECT_BOOT_AWARE|MATCH_DIRECT_BOOT_UNAWARE;
        if((flags&~allowed)!=0)throw new UnsupportedOperationException("Receiver query flags are not implemented: "+flags);
        int count=integer("receiver.count",0);
        if(count<0||count>256)throw new IllegalArgumentException("Receiver declaration count");
        for(int i=0;i<count;i++) {
            String prefix="receiver."+i+".";
            if(!component.getClassName().equals(values.getProperty(prefix+"name")))continue;
            android.content.pm.ActivityInfo info=new android.content.pm.ActivityInfo();
            info.name=component.getClassName();info.packageName=name;info.applicationInfo=getApplicationInfo(name,0);
            info.enabled=bool(prefix+"enabled",true);info.exported=bool(prefix+"exported",false);info.directBootAware=bool(prefix+"directBootAware",false);
            if(!info.isEnabled()&&(flags&MATCH_DISABLED_COMPONENTS)==0)break;
            int boot=flags&(MATCH_DIRECT_BOOT_AWARE|MATCH_DIRECT_BOOT_UNAWARE);
            if(boot!=0&&(boot&(info.directBootAware?MATCH_DIRECT_BOOT_AWARE:MATCH_DIRECT_BOOT_UNAWARE))==0)break;
            info.processName=values.getProperty(prefix+"process",name);
            if(info.processName.startsWith(":"))info.processName=name+info.processName;
            info.permission=values.getProperty(prefix+"permission");if("".equals(info.permission))info.permission=null;
            info.parentActivityName=values.getProperty(prefix+"parentActivityName");
            info.labelRes=resourceId(prefix+"labelRes");if(info.labelRes==0)info.nonLocalizedLabel=values.getProperty(prefix+"label");
            info.launchMode=integer(prefix+"launch_mode",0);
            if(info.launchMode<0||info.launchMode>4)throw new IllegalArgumentException("Activity launch mode range");
            info.theme=integer(prefix+"theme",0);
            info.configChanges=integer(prefix+"config_changes",0);
            info.screenOrientation=integer(prefix+"screen_orientation",-1);
            if(info.screenOrientation< -1||info.screenOrientation>14)throw new IllegalArgumentException("Activity orientation range");
            if((flags&GET_META_DATA)!=0)info.metaData=componentMetadata(prefix);
            return info;
        }
        throw new NameNotFoundException(component.flattenToString());
    }
    @Override public android.content.pm.ActivityInfo getActivityInfo(android.content.ComponentName component,int flags) throws NameNotFoundException {
        if(component==null)throw new NullPointerException("component");
        if(!name.equals(component.getPackageName()))throw new NameNotFoundException(component.flattenToString());
        int allowed=GET_META_DATA|MATCH_DISABLED_COMPONENTS|MATCH_DIRECT_BOOT_AWARE|MATCH_DIRECT_BOOT_UNAWARE;
        if((flags&~allowed)!=0)throw new UnsupportedOperationException("Activity query flags are not implemented: "+flags);
        int count=integer("activity.count",0);
        if(count<0||count>256)throw new IllegalArgumentException("Activity declaration count");
        for(int i=0;i<count;i++) {
            String prefix="activity."+i+".";
            if(!component.getClassName().equals(values.getProperty(prefix+"name")))continue;
            android.content.pm.ActivityInfo info=new android.content.pm.ActivityInfo();
            info.name=component.getClassName();info.packageName=name;info.applicationInfo=getApplicationInfo(name,0);
            info.enabled=bool(prefix+"enabled",true);info.exported=bool(prefix+"exported",false);info.directBootAware=bool(prefix+"directBootAware",false);
            if(!info.isEnabled()&&(flags&MATCH_DISABLED_COMPONENTS)==0)break;
            int boot=flags&(MATCH_DIRECT_BOOT_AWARE|MATCH_DIRECT_BOOT_UNAWARE);
            if(boot!=0&&(boot&(info.directBootAware?MATCH_DIRECT_BOOT_AWARE:MATCH_DIRECT_BOOT_UNAWARE))==0)break;
            info.processName=values.getProperty(prefix+"process",name);
            if(info.processName.startsWith(":"))info.processName=name+info.processName;
            info.permission=values.getProperty(prefix+"permission");if("".equals(info.permission))info.permission=null;
            info.parentActivityName=values.getProperty(prefix+"parentActivityName");
            info.labelRes=resourceId(prefix+"labelRes");if(info.labelRes==0)info.nonLocalizedLabel=values.getProperty(prefix+"label");
            info.launchMode=integer(prefix+"launch_mode",0);
            if(info.launchMode<0||info.launchMode>4)throw new IllegalArgumentException("Activity launch mode range");
            info.theme=integer(prefix+"theme",0);
            info.configChanges=integer(prefix+"config_changes",0);
            info.screenOrientation=integer(prefix+"screen_orientation",-1);
            if(info.screenOrientation< -1||info.screenOrientation>14)throw new IllegalArgumentException("Activity orientation range");
            if((flags&GET_META_DATA)!=0)info.metaData=componentMetadata(prefix);
            return info;
        }
        throw new NameNotFoundException(component.flattenToString());
    }
    @Override public android.content.pm.ServiceInfo getServiceInfo(android.content.ComponentName component,int flags) throws NameNotFoundException {
        if(component==null)throw new NullPointerException("component");if(!name.equals(component.getPackageName()))throw new NameNotFoundException(component.flattenToString());
        int allowed=GET_META_DATA|MATCH_DISABLED_COMPONENTS|MATCH_DIRECT_BOOT_AWARE|MATCH_DIRECT_BOOT_UNAWARE;
        if((flags&~allowed)!=0)throw new UnsupportedOperationException("Service query flags are not implemented: "+flags);
        int count=integer("service.count",0);if(count<0||count>256)throw new IllegalArgumentException("Service declaration count");
        for(int i=0;i<count;i++) {
            String prefix="service."+i+".";if(!component.getClassName().equals(values.getProperty(prefix+"name")))continue;
            android.content.pm.ServiceInfo info=new android.content.pm.ServiceInfo();info.name=component.getClassName();info.packageName=name;info.applicationInfo=getApplicationInfo(name,0);
            info.enabled=bool(prefix+"enabled",true);info.exported=bool(prefix+"exported",false);info.directBootAware=bool(prefix+"directBootAware",false);
            if(!info.isEnabled()&&(flags&MATCH_DISABLED_COMPONENTS)==0)break;
            int boot=flags&(MATCH_DIRECT_BOOT_AWARE|MATCH_DIRECT_BOOT_UNAWARE);
            if(boot!=0&&(boot&(info.directBootAware?MATCH_DIRECT_BOOT_AWARE:MATCH_DIRECT_BOOT_UNAWARE))==0)break;
            info.processName=values.getProperty(prefix+"process",name);if(info.processName.startsWith(":"))info.processName=name+info.processName;
            info.permission=values.getProperty(prefix+"permission");if("".equals(info.permission))info.permission=null;
            String[] attributes={"stopWithTask","isolated","externalService","useAppZygote","allowSharedIsolatedProcess","singleUser"};int[] bits={1,2,4,8,16,0x40000000};
            for(int j=0;j<attributes.length;j++)if(bool(prefix+attributes[j],false))info.flags|=bits[j];
            if((flags&GET_META_DATA)!=0)info.metaData=componentMetadata(prefix);
            return info;
        }
        throw new NameNotFoundException(component.flattenToString());
    }
    private static boolean authorityMatches(String declared,String wanted){
        if(declared==null||wanted==null||wanted.isEmpty())return false;
        if(declared.equals(wanted))return true;
        int start=0;while(start<=declared.length()){
            int end=declared.indexOf(';',start);if(end<0)end=declared.length();
            if(end>start&&declared.regionMatches(start,wanted,0,wanted.length())&&end-start==wanted.length())return true;
            start=end+1;
        }
        return false;
    }
    private android.content.pm.ProviderInfo providerAt(String prefix,int flags) throws NameNotFoundException {
        android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo();info.name=required(prefix+"name");info.packageName=name;info.applicationInfo=getApplicationInfo(name,0);
        info.enabled=bool(prefix+"enabled",true);info.exported=bool(prefix+"exported",info.applicationInfo.targetSdkVersion<17);info.authority=values.getProperty(prefix+"authorities");
        info.grantUriPermissions=bool(prefix+"grantUriPermissions",false);info.multiprocess=bool(prefix+"multiprocess",false);
        info.processName=values.getProperty(prefix+"process",name);if(info.processName.startsWith(":"))info.processName=name+info.processName;
        if((flags&GET_META_DATA)!=0)info.metaData=componentMetadata(prefix);
        return info;
    }
    @Override public android.content.pm.ProviderInfo getProviderInfo(android.content.ComponentName component,int flags) throws NameNotFoundException {
        if(component==null)throw new NullPointerException("component");if(!name.equals(component.getPackageName()))throw new NameNotFoundException(component.flattenToString());
        int count=integer("provider.count",0);if(count<0||count>256)throw new IllegalArgumentException("Provider declaration count");
        for(int i=0;i<count;i++) {
            String prefix="provider."+i+".";if(!component.getClassName().equals(values.getProperty(prefix+"name")))continue;
            return providerAt(prefix,flags);
        }
        throw new NameNotFoundException(component.flattenToString());
    }
    @Override public android.content.pm.ProviderInfo resolveContentProvider(String authority,int flags){
        if(authority==null||authority.isEmpty())return null;
        int allowed=GET_META_DATA|MATCH_DISABLED_COMPONENTS|MATCH_DIRECT_BOOT_AWARE|MATCH_DIRECT_BOOT_UNAWARE;
        if((flags&~allowed)!=0)throw new UnsupportedOperationException("Provider query flags are not implemented: "+flags);
        int count=integer("provider.count",0);if(count<0||count>256)throw new IllegalArgumentException("Provider declaration count");
        for(int i=0;i<count;i++){
            String prefix="provider."+i+".";
            if(!authorityMatches(values.getProperty(prefix+"authorities"),authority))continue;
            try{
                android.content.pm.ProviderInfo info=providerAt(prefix,flags);
                if(!info.isEnabled()&&(flags&MATCH_DISABLED_COMPONENTS)==0)return null;
                return info;
            }catch(NameNotFoundException absent){return null;}
        }
        return null;
    }
    @Override public PackageInfo getPackageInfo(String packageName, int flags) throws NameNotFoundException {
        if(!name.equals(packageName)){Properties facts=HostAppInstaller.metadata(packageName);if(facts==null)throw new NameNotFoundException(packageName);return new HostPackageManager(facts,context).getPackageInfo(packageName,flags);}
        query(packageName, flags);
        PackageInfo info = new PackageInfo();
        info.packageName = name;
        info.sharedUserId=values.getProperty("sharedUserId");
        info.sharedUserLabel=resourceId("sharedUserLabel");
        info.baseRevisionCode=integer("baseRevisionCode",0);
        info.installLocation=integer("installLocation",-1);
        if(info.baseRevisionCode<0||info.installLocation< -1||info.installLocation>2)throw new IllegalArgumentException("Package revision/install location");
        info.firstInstallTime=Long.parseLong(values.getProperty("firstInstallTime","0"));info.lastUpdateTime=Long.parseLong(values.getProperty("lastUpdateTime","0"));
        if(info.firstInstallTime<0||info.lastUpdateTime<info.firstInstallTime)throw new IllegalArgumentException("Package timestamps");
        int versionResource = resourceId("versionNameRes");
        info.versionName = versionResource != 0 ? packageString(versionResource) : values.getProperty("versionName");
        long low = Long.parseLong(values.getProperty("versionCode", "0"));
        long high = Long.parseLong(values.getProperty("versionCodeMajor", "0"));
        if (low < 0 || low > 0xffffffffL || high < 0 || high > 0x7fffffffL) throw new IllegalArgumentException("APK version code range");
        info.setLongVersionCode((high << 32) | low);
        info.applicationInfo = getApplicationInfo(name, flags & GET_META_DATA);
        if((flags&GET_SERVICES)!=0){
            int count=integer("service.count",0);if(count<0||count>256)throw new IllegalArgumentException("Service declaration count");
            java.util.ArrayList<android.content.pm.ServiceInfo> services=new java.util.ArrayList<android.content.pm.ServiceInfo>();
            for(int i=0;i<count;i++)try{services.add(getServiceInfo(new android.content.ComponentName(name,required("service."+i+".name")),flags&(GET_META_DATA|MATCH_DISABLED_COMPONENTS|MATCH_DIRECT_BOOT_AWARE|MATCH_DIRECT_BOOT_UNAWARE)));}catch(NameNotFoundException excluded){}
            info.services=services.isEmpty()?null:services.toArray(new android.content.pm.ServiceInfo[services.size()]);
        }
        if ((flags&GET_SIGNATURES)!=0) info.signatures=ApkCertificates.read(required("sourceDir"));
        if ((flags&GET_SIGNING_CERTIFICATES)!=0) info.signingInfo=new android.content.pm.SigningInfo(ApkCertificates.read(required("sourceDir")));
        return info;
    }
    @Override public ApplicationInfo getApplicationInfo(String packageName, int flags) throws NameNotFoundException {
        if(!name.equals(packageName)){Properties facts=HostAppInstaller.metadata(packageName);if(facts==null)throw new NameNotFoundException(packageName);return new HostPackageManager(facts,context).getApplicationInfo(packageName,flags);}
        query(packageName, flags);
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = name;
        if((flags&GET_META_DATA)!=0)info.metaData=componentMetadata("application.");
        info.className = values.getProperty("applicationClass"); info.name = info.className;
        info.processName = values.getProperty("applicationInfo.processName",name); info.uid = Process.myUid();
        info.backupAgentName=values.getProperty("applicationInfo.backupAgent");
        info.manageSpaceActivityName=values.getProperty("applicationInfo.manageSpaceActivity");
        info.appComponentFactory=values.getProperty("applicationInfo.appComponentFactory");
        info.permission=values.getProperty("applicationInfo.permission");
        info.taskAffinity=values.getProperty("applicationInfo.taskAffinity",name);
        info.descriptionRes=resourceId("applicationInfo.descriptionRes");
        info.uiOptions=integer("applicationInfo.uiOptions",0);info.category=integer("applicationInfo.category",-1);
        info.compileSdkVersion=integer("applicationInfo.compileSdkVersion",0);
        info.compileSdkVersionCodename=values.getProperty("applicationInfo.compileSdkVersionCodename");
        info.requiresSmallestWidthDp=integer("applicationInfo.requiresSmallestWidthDp",0);
        info.compatibleWidthLimitDp=integer("applicationInfo.compatibleWidthLimitDp",0);
        info.largestWidthLimitDp=integer("applicationInfo.largestWidthLimitDp",0);
        info.sourceDir = required("sourceDir"); info.publicSourceDir = info.sourceDir; info.dataDir = required("dataDir"); info.nativeLibraryDir = values.getProperty("nativeLibraryDir");
        // Device-protected Context currently uses the same app storage on Linux.
        info.deviceProtectedDataDir=info.dataDir;
        info.storageUuid=android.os.storage.StorageManager.UUID_DEFAULT;
        info.theme=integer("theme",0);
        info.icon=resourceId("icon");info.logo=resourceId("logo");info.banner=resourceId("banner");
        info.minSdkVersion = integer("minSdk", 1); info.targetSdkVersion = integer("targetSdk", info.minSdkVersion);
        info.enabled = bool("enabled", true);
        info.flags = (bool("debuggable", false) ? ApplicationInfo.FLAG_DEBUGGABLE : 0) | (bool("hasCode", true) ? ApplicationInfo.FLAG_HAS_CODE : 0);
        String label = values.getProperty("label");
        info.labelRes = resourceId("labelRes");
        if (info.labelRes == 0) info.nonLocalizedLabel = label;
        return info;
    }
    @Override public CharSequence getApplicationLabel(ApplicationInfo info) {
        if(info==null)throw new NullPointerException("application");
        if(!name.equals(info.packageName)){Properties facts=HostAppInstaller.metadata(info.packageName);if(facts==null)throw new IllegalArgumentException("Unknown application");return new HostPackageManager(facts,context).getApplicationLabel(info);}
        if (info.nonLocalizedLabel != null) return info.nonLocalizedLabel;
        if (info.labelRes != 0) return packageString(info.labelRes);
        return name;
    }
    @Override public android.content.res.XmlResourceParser getXml(String packageName,int resid,ApplicationInfo appInfo){
        if(resid==0||packageName!=null&&!name.equals(packageName)||appInfo!=null&&appInfo.packageName!=null&&!name.equals(appInfo.packageName))return null;
        try{return context.getResources().getXml(resid);}catch(RuntimeException error){return null;}
    }
    @Override public android.content.res.Resources getResourcesForApplication(ApplicationInfo app) throws NameNotFoundException {
        if(app==null||!name.equals(app.packageName))throw new NameNotFoundException(app==null?"null":app.packageName);
        return context.getResources();
    }
    @Override public android.content.res.Resources getResourcesForApplication(String appPackageName) throws NameNotFoundException {
        if(!name.equals(appPackageName))throw new NameNotFoundException(String.valueOf(appPackageName));
        return context.getResources();
    }
}
