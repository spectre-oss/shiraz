// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;
import android.util.AndroidException;

/** Loaded-APK metadata and host package-query contracts. */
public abstract class PackageManager {
    public static final String FEATURE_CAMERA="android.hardware.camera",FEATURE_CAMERA_ANY="android.hardware.camera.any",FEATURE_MICROPHONE="android.hardware.microphone";
    public static final String FEATURE_TOUCHSCREEN="android.hardware.touchscreen";
    public android.content.Intent getLaunchIntentForPackage(String pkg){return null;}
    public java.util.List<ApplicationInfo> getInstalledApplications(int flags){java.util.ArrayList<ApplicationInfo> result=new java.util.ArrayList<>();for(PackageInfo info:getInstalledPackages(flags))result.add(info.applicationInfo);return result;}
    public java.util.List<PackageInfo> getInstalledPackages(int flags){throw new UnsupportedOperationException("Installed package registry");}
    public PackageInstaller getPackageInstaller(){throw new UnsupportedOperationException("No host installer");}
    public boolean canRequestPackageInstalls(){return false;}
    public boolean hasSystemFeature(String name){return (FEATURE_CAMERA.equals(name)||FEATURE_CAMERA_ANY.equals(name))?android.hardware.Camera.getNumberOfCameras()>0:FEATURE_MICROPHONE.equals(name)?new java.io.File("/dev/snd").isDirectory():FEATURE_TOUCHSCREEN.equals(name)&&shiraz.runtime.HostInput.hasTouchscreen();}
    public FeatureInfo[] getSystemAvailableFeatures(){java.util.ArrayList<FeatureInfo> features=new java.util.ArrayList<FeatureInfo>();for(String name:new String[]{FEATURE_CAMERA,FEATURE_CAMERA_ANY,FEATURE_MICROPHONE,FEATURE_TOUCHSCREEN})if(hasSystemFeature(name)){FeatureInfo info=new FeatureInfo();info.name=name;features.add(info);}return features.toArray(new FeatureInfo[features.size()]);}
    /** Shiraz installs complete APK packages; Android instant apps are not hosted. */
    public boolean isInstantApp(){return false;}
    public boolean hasSystemFeature(String name,int version){return version<=0&&hasSystemFeature(name);}

    public ResolveInfo resolveActivity(android.content.Intent intent,int flags){java.util.List<ResolveInfo> matches=queryIntentActivities(intent,flags);return matches.isEmpty()?null:matches.get(0);}
    public static final int MATCH_DEFAULT_ONLY=0x10000,MATCH_ALL=0x20000,MATCH_SYSTEM_ONLY=0x100000,GET_RESOLVED_FILTER=0x40;
    public java.util.List<ResolveInfo> queryIntentServices(android.content.Intent intent,int flags){if(intent==null)throw new NullPointerException("intent");return new java.util.ArrayList<ResolveInfo>();}
    public java.util.List<ResolveInfo> queryBroadcastReceivers(android.content.Intent intent,int flags){if(intent==null)throw new NullPointerException("intent");return new java.util.ArrayList<ResolveInfo>();}
    public java.util.List<ResolveInfo> queryIntentActivities(android.content.Intent intent,int flags){throw new UnsupportedOperationException("Activity resolution");}
    public static final int PERMISSION_GRANTED=0,PERMISSION_DENIED=-1;
    public int checkPermission(String permission,String packageName){return PERMISSION_DENIED;}
    public static final int GET_SERVICES = 4;
    public static final int GET_SIGNING_CERTIFICATES = 0x08000000;
    public static final int GET_SIGNATURES = 0x40, GET_META_DATA = 0x80, GET_PERMISSIONS = 0x1000;
    public static final int GET_DISABLED_COMPONENTS=0x200,MATCH_DISABLED_COMPONENTS=0x200,MATCH_DIRECT_BOOT_UNAWARE=0x40000,MATCH_DIRECT_BOOT_AWARE=0x80000;
    public ActivityInfo getReceiverInfo(android.content.ComponentName component,int flags) throws NameNotFoundException {throw new NameNotFoundException(String.valueOf(component));}
    public java.util.List<ResolveInfo> queryIntentContentProviders(android.content.Intent intent,int flags){throw new UnsupportedOperationException("Provider intent resolution");}
    public abstract ActivityInfo getActivityInfo(android.content.ComponentName component,int flags) throws NameNotFoundException;
    public static final int COMPONENT_ENABLED_STATE_DEFAULT=0,COMPONENT_ENABLED_STATE_ENABLED=1,COMPONENT_ENABLED_STATE_DISABLED=2,DONT_KILL_APP=1;
    private final java.util.HashMap<String,Integer> componentStates=new java.util.HashMap<String,Integer>();
    public synchronized int getComponentEnabledSetting(android.content.ComponentName component){Integer state=componentStates.get(component.flattenToString());return state==null?COMPONENT_ENABLED_STATE_DEFAULT:state;}
    public synchronized void setComponentEnabledSetting(android.content.ComponentName component,int state,int flags){componentStates.put(component.flattenToString(),state);}
    public String getInstallerPackageName(String packageName) { return null; }
    public ProviderInfo getProviderInfo(android.content.ComponentName component,int flags) throws NameNotFoundException { throw new NameNotFoundException(String.valueOf(component)); }
    public ProviderInfo resolveContentProvider(String authority,int flags){return null;}
    public android.content.res.XmlResourceParser getXml(String packageName,int resid,ApplicationInfo appInfo){return null;}
    public android.content.res.Resources getResourcesForApplication(ApplicationInfo app) throws NameNotFoundException { throw new NameNotFoundException(String.valueOf(app)); }
    public android.content.res.Resources getResourcesForApplication(String appPackageName) throws NameNotFoundException { throw new NameNotFoundException(String.valueOf(appPackageName)); }
    public abstract ServiceInfo getServiceInfo(android.content.ComponentName component,int flags) throws NameNotFoundException;
    public abstract PackageInfo getPackageInfo(String packageName, int flags) throws NameNotFoundException;
    public abstract ApplicationInfo getApplicationInfo(String packageName, int flags) throws NameNotFoundException;
    public abstract CharSequence getApplicationLabel(ApplicationInfo info);
    public static class NameNotFoundException extends AndroidException {
        public NameNotFoundException() { }
        public NameNotFoundException(String name) { super(name); }
    }
}
