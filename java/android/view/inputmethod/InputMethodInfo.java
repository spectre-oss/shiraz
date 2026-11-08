// SPDX-License-Identifier: GPL-3.0-only
package android.view.inputmethod;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Parcel;
import android.os.Parcelable;
/** Metadata only. Shiraz's native text-input bridge is not an Android IME service. */
public final class InputMethodInfo implements Parcelable {
    private final String pkg,name,settings;private final CharSequence label;
    public InputMethodInfo(String packageName,String className,CharSequence label,String settingsActivity){if(packageName==null||className==null)throw new NullPointerException("IME component");pkg=packageName;name=className;this.label=label;settings=settingsActivity;}
    public InputMethodInfo(Context context,ResolveInfo service){throw new UnsupportedOperationException("Android IME service descriptors unavailable");}
    public String getId(){return getComponent().flattenToShortString();}
    public String getPackageName(){return pkg;}
    public String getServiceName(){return name;}
    public String getSettingsActivity(){return settings;}
    public ComponentName getComponent(){return new ComponentName(pkg,name);}
    public ServiceInfo getServiceInfo(){ServiceInfo info=new ServiceInfo();info.packageName=pkg;info.name=name;info.nonLocalizedLabel=label;return info;}
    public CharSequence loadLabel(android.content.pm.PackageManager manager){return label==null?name:label;}
    public int getSubtypeCount(){return 0;}
    public InputMethodSubtype getSubtypeAt(int index){throw new IndexOutOfBoundsException("No declared IME subtypes");}
    public boolean isDefault(Context context){return false;}
    public boolean isAuxiliaryIme(){return false;}
    public boolean supportsSwitchingToNextInputMethod(){return false;}
    @Override public boolean equals(Object value){return value instanceof InputMethodInfo&&getId().equals(((InputMethodInfo)value).getId());}
    @Override public int hashCode(){return getId().hashCode();}
    public int describeContents(){return 0;}
    public void writeToParcel(Parcel output,int flags){output.writeString(pkg);output.writeString(name);output.writeString(label==null?null:label.toString());output.writeString(settings);}
    public static final Parcelable.Creator<InputMethodInfo> CREATOR=new Parcelable.Creator<InputMethodInfo>(){public InputMethodInfo createFromParcel(Parcel input){return new InputMethodInfo(input.readString(),input.readString(),input.readString(),input.readString());}public InputMethodInfo[] newArray(int size){return new InputMethodInfo[size];}};
}
