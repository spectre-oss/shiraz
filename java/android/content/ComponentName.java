// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.os.*;
import java.util.Objects;
public final class ComponentName implements Parcelable, Comparable<ComponentName>, Cloneable {
    private final String pkg, cls;
    public ComponentName(String pkg, String cls) { this.pkg = Objects.requireNonNull(pkg); this.cls = Objects.requireNonNull(cls); }
    public ComponentName(Context context, String cls) { this(context.getPackageName(), cls); }
    public ComponentName(Context context, Class<?> cls) { this(context.getPackageName(), cls.getName()); }
    public static ComponentName createRelative(String pkg, String cls) { return new ComponentName(pkg, cls.startsWith(".") ? pkg + cls : cls); }
    public String getPackageName() { return pkg; }
    public String getClassName() { return cls; }
    public String getShortClassName() { return cls.startsWith(pkg + ".") ? cls.substring(pkg.length()) : cls; }
    public String flattenToString() { return pkg + "/" + cls; }
    public String flattenToShortString() { return pkg + "/" + getShortClassName(); }
    public static ComponentName unflattenFromString(String value) { int slash = value.indexOf('/'); return slash < 0 || slash + 1 == value.length() ? null : createRelative(value.substring(0, slash), value.substring(slash + 1)); }
    @Override public ComponentName clone() { return new ComponentName(pkg, cls); }
    @Override public boolean equals(Object other) { return other instanceof ComponentName && pkg.equals(((ComponentName)other).pkg) && cls.equals(((ComponentName)other).cls); }
    @Override public int hashCode() { return pkg.hashCode() + cls.hashCode(); }
    public int compareTo(ComponentName other) { int value = pkg.compareTo(other.pkg); return value == 0 ? cls.compareTo(other.cls) : value; }
    @Override public String toString() { return "ComponentInfo{" + flattenToShortString() + "}"; }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { throw new UnsupportedOperationException("ComponentName IPC is not implemented"); }
    public static final Parcelable.Creator<ComponentName> CREATOR = new Parcelable.Creator<ComponentName>() {
        public ComponentName createFromParcel(Parcel in) { throw new UnsupportedOperationException("ComponentName parcel decoding is not implemented"); }
        public ComponentName[] newArray(int size) { return new ComponentName[size]; }
    };
}
