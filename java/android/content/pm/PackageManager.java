// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;
import android.util.AndroidException;

/** Loaded-APK metadata and host package-query contracts. */
public abstract class PackageManager {
    public static final int SIGNATURE_MATCH=0,SIGNATURE_NEITHER_SIGNED=1,SIGNATURE_FIRST_NOT_SIGNED=-1,SIGNATURE_SECOND_NOT_SIGNED=-2,SIGNATURE_NO_MATCH=-3,SIGNATURE_UNKNOWN_PACKAGE=-4;
    public int checkSignatures(String first,String second){throw new UnsupportedOperationException("Verified package signatures");}
    public int checkSignatures(int first,int second){throw new UnsupportedOperationException("Verified package signatures");}
    public String[] getPackagesForUid(int uid){throw new UnsupportedOperationException("Package UID lookup");}
    public String[] getSystemSharedLibraryNames(){throw new UnsupportedOperationException("Host shared library inventory");}
    public String getNameForUid(int uid){String[] packages=getPackagesForUid(uid);return packages==null||packages.length==0?null:packages[0];}
    public int getPackageUid(String packageName,int flags)throws NameNotFoundException{return getApplicationInfo(packageName,flags).uid;}
    public static final String FEATURE_CAMERA="android.hardware.camera",FEATURE_CAMERA_ANY="android.hardware.camera.any",FEATURE_MICROPHONE="android.hardware.microphone";
    public static final String FEATURE_TOUCHSCREEN="android.hardware.touchscreen";
    public static final String FEATURE_VULKAN_HARDWARE_LEVEL="android.hardware.vulkan.level";
    public static final String FEATURE_VULKAN_HARDWARE_VERSION="android.hardware.vulkan.version";
    public static final String FEATURE_VULKAN_HARDWARE_COMPUTE="android.hardware.vulkan.compute";
    public static final String FEATURE_VULKAN_DEQP_LEVEL="android.software.vulkan.deqp.level";
    private static volatile int[] vulkanCapabilities;
    private static native int[] nativeVulkanCapabilities();
    private static int[] getVulkanCapabilities(){
        int[] cached=vulkanCapabilities;
        if(cached!=null)return cached;
        int[] probed=new int[]{0,-1};
        try{
            System.loadLibrary("shiraz-host");
            int[] raw=nativeVulkanCapabilities();
            if(raw!=null&&raw.length>=2&&raw[0]>0&&raw[1]>=0)probed=new int[]{raw[0],raw[1]};
        }catch(UnsatisfiedLinkError|SecurityException unavailable){}
        vulkanCapabilities=probed;
        return probed;
    }
    public android.content.Intent getLaunchIntentForPackage(String pkg){return null;}
    public java.util.List<ApplicationInfo> getInstalledApplications(int flags){java.util.ArrayList<ApplicationInfo> result=new java.util.ArrayList<>();for(PackageInfo info:getInstalledPackages(flags))result.add(info.applicationInfo);return result;}
    public java.util.List<PackageInfo> getInstalledPackages(int flags){throw new UnsupportedOperationException("Installed package registry");}
    public PackageInstaller getPackageInstaller(){throw new UnsupportedOperationException("No host installer");}
    public boolean canRequestPackageInstalls(){return false;}
    public boolean hasSystemFeature(String name){return hasSystemFeature(name,0);}
    public FeatureInfo[] getSystemAvailableFeatures(){
        java.util.ArrayList<FeatureInfo> features=new java.util.ArrayList<FeatureInfo>();
        for(String name:new String[]{FEATURE_CAMERA,FEATURE_CAMERA_ANY,FEATURE_MICROPHONE,FEATURE_TOUCHSCREEN}){
            if(hasSystemFeature(name)){FeatureInfo info=new FeatureInfo();info.name=name;features.add(info);}
        }
        int[] vk=getVulkanCapabilities();
        if(vk[0]>0&&vk[1]>=0){
            FeatureInfo level=new FeatureInfo();level.name=FEATURE_VULKAN_HARDWARE_LEVEL;level.version=vk[1];features.add(level);
            FeatureInfo ver=new FeatureInfo();ver.name=FEATURE_VULKAN_HARDWARE_VERSION;ver.version=vk[0];features.add(ver);
            FeatureInfo comp=new FeatureInfo();comp.name=FEATURE_VULKAN_HARDWARE_COMPUTE;comp.version=0;features.add(comp);
        }
        return features.toArray(new FeatureInfo[features.size()]);
    }
    /** Shiraz installs complete APK packages; Android instant apps are not hosted. */
    public boolean isInstantApp(){return false;}
    public boolean hasSystemFeature(String name,int version){
        if(name==null)return false;
        if(FEATURE_VULKAN_HARDWARE_LEVEL.equals(name)){int[] vk=getVulkanCapabilities();return vk[0]>0&&vk[1]>=0&&vk[1]>=version;}
        if(FEATURE_VULKAN_HARDWARE_VERSION.equals(name)){int[] vk=getVulkanCapabilities();return vk[0]>0&&vk[1]>=0&&vk[0]>=version;}
        if(FEATURE_VULKAN_HARDWARE_COMPUTE.equals(name)){int[] vk=getVulkanCapabilities();return vk[0]>=0x400000&&vk[1]>=0&&version<=0;}
        if(version>0)return false;
        return (FEATURE_CAMERA.equals(name)||FEATURE_CAMERA_ANY.equals(name))?android.hardware.Camera.getNumberOfCameras()>0:FEATURE_MICROPHONE.equals(name)?new java.io.File("/dev/snd").isDirectory():FEATURE_TOUCHSCREEN.equals(name)&&shiraz.runtime.HostInput.hasTouchscreen();
    }

    public ResolveInfo resolveService(android.content.Intent intent,int flags){java.util.List<ResolveInfo> matches=queryIntentServices(intent,flags);return matches.isEmpty()?null:matches.get(0);}
    public ResolveInfo resolveActivity(android.content.Intent intent,int flags){java.util.List<ResolveInfo> matches=queryIntentActivities(intent,flags);return matches.isEmpty()?null:matches.get(0);}
    public static final int MATCH_DEFAULT_ONLY=0x10000,MATCH_ALL=0x20000,MATCH_SYSTEM_ONLY=0x100000,GET_RESOLVED_FILTER=0x40;
    public java.util.List<ResolveInfo> queryIntentServices(android.content.Intent intent,int flags){if(intent==null)throw new NullPointerException("intent");return new java.util.ArrayList<ResolveInfo>();}
    public java.util.List<ResolveInfo> queryBroadcastReceivers(android.content.Intent intent,int flags){if(intent==null)throw new NullPointerException("intent");return new java.util.ArrayList<ResolveInfo>();}
    public java.util.List<ResolveInfo> queryIntentActivities(android.content.Intent intent,int flags){throw new UnsupportedOperationException("Activity resolution");}
    public static final int PERMISSION_GRANTED=0,PERMISSION_DENIED=-1;
    public int checkPermission(String permission,String packageName){return PERMISSION_DENIED;}
    public static final int GET_ACTIVITIES = 1, GET_RECEIVERS = 2, GET_SERVICES = 4, GET_PROVIDERS = 8, GET_INSTRUMENTATION = 0x10, GET_CONFIGURATIONS = 0x4000, GET_GIDS = 0x100;
    public static final int GET_SIGNING_CERTIFICATES = 0x08000000;
    public static final int GET_SIGNATURES = 0x40, GET_META_DATA = 0x80, GET_PERMISSIONS = 0x1000;
    /** Sentinel selecting the existing user-state matching policy (API 29). */
    public static final int MATCH_UNINSTALLED_PACKAGES=0x2000;
    public static final int MATCH_ANY_USER=0x00400000;
    public static final int MATCH_KNOWN_PACKAGES=MATCH_ANY_USER|MATCH_UNINSTALLED_PACKAGES;
    public static final int MATCH_DIRECT_BOOT_AUTO=0x10000000;
    public static final int GET_DISABLED_COMPONENTS=0x200,MATCH_DISABLED_COMPONENTS=0x200,MATCH_DIRECT_BOOT_UNAWARE=0x40000,MATCH_DIRECT_BOOT_AWARE=0x80000;
    public ActivityInfo getReceiverInfo(android.content.ComponentName component,int flags) throws NameNotFoundException {throw new NameNotFoundException(String.valueOf(component));}
    public java.util.List<ResolveInfo> queryIntentContentProviders(android.content.Intent intent,int flags){throw new UnsupportedOperationException("Provider intent resolution");}
    public abstract ActivityInfo getActivityInfo(android.content.ComponentName component,int flags) throws NameNotFoundException;
    public static final int COMPONENT_ENABLED_STATE_DEFAULT=0,COMPONENT_ENABLED_STATE_ENABLED=1,COMPONENT_ENABLED_STATE_DISABLED=2,DONT_KILL_APP=1;
    /** No application-level overrides are supported; manifest enablement remains authoritative. */
    public int getApplicationEnabledSetting(String packageName){
        java.util.Objects.requireNonNull(packageName,"packageName");
        try{getApplicationInfo(packageName,0);}
        catch(NameNotFoundException absent){throw new IllegalArgumentException("Unknown package: "+packageName,absent);}
        return COMPONENT_ENABLED_STATE_DEFAULT;
    }
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
    public PackageInfo getPackageArchiveInfo(String path,int flags){return shiraz.runtime.ArchivePackageInfo.read(path,flags);}
    public abstract PackageInfo getPackageInfo(String packageName, int flags) throws NameNotFoundException;
    public abstract ApplicationInfo getApplicationInfo(String packageName, int flags) throws NameNotFoundException;
    public android.graphics.drawable.Drawable getApplicationIcon(String packageName) throws NameNotFoundException {
        return getApplicationIcon(getApplicationInfo(packageName,0));
    }
    public android.graphics.drawable.Drawable getApplicationIcon(ApplicationInfo info) {
        if(info==null)throw new NullPointerException("application info");
        if(info.icon!=0)try{return getResourcesForApplication(info).getDrawable(info.icon);}
        catch(NameNotFoundException|android.content.res.Resources.NotFoundException unavailable){}
        return getDefaultActivityIcon();
    }
    public android.graphics.drawable.Drawable getDefaultActivityIcon() {
        // Neutral host placeholder when an application declares no usable icon.
        android.graphics.drawable.GradientDrawable icon=new android.graphics.drawable.GradientDrawable();
        icon.setSize(48,48);icon.setCornerRadius(10);icon.setColor(0xff606770);icon.setStroke(2,0xffaeb4bc);
        return icon;
    }
    public abstract CharSequence getApplicationLabel(ApplicationInfo info);
    public CharSequence getText(String packageName, int resid, ApplicationInfo appInfo) {
        if (packageName == null || resid == 0) return null;
        try {
            android.content.res.Resources res = appInfo != null
                    ? getResourcesForApplication(appInfo)
                    : getResourcesForApplication(packageName);
            return res.getText(resid);
        } catch (NameNotFoundException | android.content.res.Resources.NotFoundException unavailable) {
            return null;
        }
    }
    public static final class ApplicationInfoFlags {
        private final long mValue;
        private ApplicationInfoFlags(long value) { mValue = value; }
        public static ApplicationInfoFlags of(long value) { return new ApplicationInfoFlags(value); }
        public long getValue() { return mValue; }
    }
    public static final class ComponentInfoFlags {
        private final long mValue;
        private ComponentInfoFlags(long value) { mValue = value; }
        public static ComponentInfoFlags of(long value) { return new ComponentInfoFlags(value); }
        public long getValue() { return mValue; }
    }
    public static final class PackageInfoFlags {
        private final long mValue;
        private PackageInfoFlags(long value) { mValue = value; }
        public static PackageInfoFlags of(long value) { return new PackageInfoFlags(value); }
        public long getValue() { return mValue; }
    }
    public static final class ResolveInfoFlags {
        private final long mValue;
        private ResolveInfoFlags(long value) { mValue = value; }
        public static ResolveInfoFlags of(long value) { return new ResolveInfoFlags(value); }
        public long getValue() { return mValue; }
    }
    public ApplicationInfo getApplicationInfo(String packageName, ApplicationInfoFlags flags) throws NameNotFoundException {
        return getApplicationInfo(packageName, (int) (flags == null ? 0L : flags.getValue()));
    }
    public PackageInfo getPackageInfo(String packageName, PackageInfoFlags flags) throws NameNotFoundException {
        return getPackageInfo(packageName, (int) (flags == null ? 0L : flags.getValue()));
    }
    public ActivityInfo getActivityInfo(android.content.ComponentName component, ComponentInfoFlags flags) throws NameNotFoundException {
        return getActivityInfo(component, (int) (flags == null ? 0L : flags.getValue()));
    }
    public ServiceInfo getServiceInfo(android.content.ComponentName component, ComponentInfoFlags flags) throws NameNotFoundException {
        return getServiceInfo(component, (int) (flags == null ? 0L : flags.getValue()));
    }
    public ProviderInfo getProviderInfo(android.content.ComponentName component, ComponentInfoFlags flags) throws NameNotFoundException {
        return getProviderInfo(component, (int) (flags == null ? 0L : flags.getValue()));
    }
    public ResolveInfo resolveActivity(android.content.Intent intent, ResolveInfoFlags flags) {
        return resolveActivity(intent, (int) (flags == null ? 0L : flags.getValue()));
    }
    public ResolveInfo resolveService(android.content.Intent intent, ResolveInfoFlags flags) {
        return resolveService(intent, (int) (flags == null ? 0L : flags.getValue()));
    }
    public java.util.List<ResolveInfo> queryIntentActivities(android.content.Intent intent, ResolveInfoFlags flags) {
        return queryIntentActivities(intent, (int) (flags == null ? 0L : flags.getValue()));
    }
    public java.util.List<ResolveInfo> queryIntentServices(android.content.Intent intent, ResolveInfoFlags flags) {
        return queryIntentServices(intent, (int) (flags == null ? 0L : flags.getValue()));
    }
    public java.util.List<ResolveInfo> queryBroadcastReceivers(android.content.Intent intent, ResolveInfoFlags flags) {
        return queryBroadcastReceivers(intent, (int) (flags == null ? 0L : flags.getValue()));
    }
    public static final class ComponentEnabledSetting implements android.os.Parcelable {
        private final android.content.ComponentName mComponentName;
        private final int mEnabledState;
        private final int mEnabledFlags;
        public ComponentEnabledSetting(android.content.ComponentName componentName, int newState, int flags) {
            mComponentName = java.util.Objects.requireNonNull(componentName);
            mEnabledState = newState;
            mEnabledFlags = flags;
        }
        ComponentEnabledSetting(android.os.Parcel in) {
            mComponentName = android.content.ComponentName.CREATOR.createFromParcel(in);
            mEnabledState = in.readInt();
            mEnabledFlags = in.readInt();
        }
        public android.content.ComponentName getComponentName() { return mComponentName; }
        public int getEnabledState() { return mEnabledState; }
        public int getEnabledFlags() { return mEnabledFlags; }
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            mComponentName.writeToParcel(dest, flags);
            dest.writeInt(mEnabledState);
            dest.writeInt(mEnabledFlags);
        }
        @Override public int describeContents() { return 0; }
        public static final Creator<ComponentEnabledSetting> CREATOR = new Creator<ComponentEnabledSetting>() {
            @Override public ComponentEnabledSetting createFromParcel(android.os.Parcel in) { return new ComponentEnabledSetting(in); }
            @Override public ComponentEnabledSetting[] newArray(int size) { return new ComponentEnabledSetting[size]; }
        };
    }
    public interface OnChecksumsReadyListener {
        void onChecksumsReady(java.util.List<ApkChecksum> checksums);
    }
    public static final class Property implements android.os.Parcelable {
        private static final int TYPE_BOOLEAN = 1;
        private static final int TYPE_FLOAT = 2;
        private static final int TYPE_INTEGER = 3;
        private static final int TYPE_RESOURCE = 4;
        private static final int TYPE_STRING = 5;
        private final String mName;
        private final int mType;
        private final String mPackageName;
        private final String mClassName;
        private boolean mBooleanValue;
        private float mFloatValue;
        private int mIntegerValue;
        private String mStringValue;
        public Property(String name, boolean value, String packageName, String className) {
            mName = java.util.Objects.requireNonNull(name); mType = TYPE_BOOLEAN;
            mPackageName = java.util.Objects.requireNonNull(packageName); mClassName = className; mBooleanValue = value;
        }
        public Property(String name, float value, String packageName, String className) {
            mName = java.util.Objects.requireNonNull(name); mType = TYPE_FLOAT;
            mPackageName = java.util.Objects.requireNonNull(packageName); mClassName = className; mFloatValue = value;
        }
        public Property(String name, int value, boolean isResource, String packageName, String className) {
            mName = java.util.Objects.requireNonNull(name); mType = isResource ? TYPE_RESOURCE : TYPE_INTEGER;
            mPackageName = java.util.Objects.requireNonNull(packageName); mClassName = className; mIntegerValue = value;
        }
        public Property(String name, String value, String packageName, String className) {
            mName = java.util.Objects.requireNonNull(name); mType = TYPE_STRING;
            mPackageName = java.util.Objects.requireNonNull(packageName); mClassName = className; mStringValue = value;
        }
        Property(android.os.Parcel in) {
            mName = in.readString(); mType = in.readInt(); mPackageName = in.readString(); mClassName = in.readString();
            if (mType == TYPE_BOOLEAN) mBooleanValue = in.readInt() != 0;
            else if (mType == TYPE_FLOAT) mFloatValue = in.readFloat();
            else if (mType == TYPE_INTEGER || mType == TYPE_RESOURCE) mIntegerValue = in.readInt();
            else if (mType == TYPE_STRING) mStringValue = in.readString();
        }
        public String getName() { return mName; }
        public String getPackageName() { return mPackageName; }
        public String getClassName() { return mClassName; }
        public boolean getBoolean() { return mBooleanValue; }
        public boolean isBoolean() { return mType == TYPE_BOOLEAN; }
        public float getFloat() { return mFloatValue; }
        public boolean isFloat() { return mType == TYPE_FLOAT; }
        public int getInteger() { return mType == TYPE_INTEGER ? mIntegerValue : 0; }
        public boolean isInteger() { return mType == TYPE_INTEGER; }
        public int getResourceId() { return mType == TYPE_RESOURCE ? mIntegerValue : 0; }
        public boolean isResourceId() { return mType == TYPE_RESOURCE; }
        public String getString() { return mStringValue; }
        public boolean isString() { return mType == TYPE_STRING; }
        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeString(mName); dest.writeInt(mType); dest.writeString(mPackageName); dest.writeString(mClassName);
            if (mType == TYPE_BOOLEAN) dest.writeInt(mBooleanValue ? 1 : 0);
            else if (mType == TYPE_FLOAT) dest.writeFloat(mFloatValue);
            else if (mType == TYPE_INTEGER || mType == TYPE_RESOURCE) dest.writeInt(mIntegerValue);
            else if (mType == TYPE_STRING) dest.writeString(mStringValue);
        }
        public static final Creator<Property> CREATOR = new Creator<Property>() {
            @Override public Property createFromParcel(android.os.Parcel in) { return new Property(in); }
            @Override public Property[] newArray(int size) { return new Property[size]; }
        };
    }
    public static class NameNotFoundException extends AndroidException {
        public NameNotFoundException() { }
        public NameNotFoundException(String name) { super(name); }
    }
}
