// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

import android.app.Person;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.LocusId;
import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Local shortcut metadata with copied intent containers; launcher persistence and IPC are separate. */
public final class ShortcutInfo implements Parcelable {
    public static final int DISABLED_REASON_NOT_DISABLED = 0;
    public static final int DISABLED_REASON_BY_APP = 1;
    public static final int DISABLED_REASON_APP_CHANGED = 2;
    public static final int DISABLED_REASON_UNKNOWN = 3;
    public static final int DISABLED_REASON_VERSION_LOWER = 100;
    public static final int DISABLED_REASON_BACKUP_NOT_SUPPORTED = 101;
    public static final int DISABLED_REASON_SIGNATURE_MISMATCH = 102;
    public static final int DISABLED_REASON_OTHER_RESTORE_ISSUE = 103;
    public static final String SHORTCUT_CATEGORY_CONVERSATION = "android.shortcut.conversation";
    public static final int SURFACE_LAUNCHER = 1;

    private final long lastChangedTimestamp;
    private final PersistableBundle extras;
    private final String id;
    private final String pkg;
    private final CharSequence label;
    private final CharSequence longLabel;
    private final CharSequence disabledMessage;
    private final ComponentName activity;
    private final Intent[] intents;
    private final Set<String> categories;
    private final Icon icon;
    private final int rank;
    private final boolean dynamic;
    private final LocusId locusId;
    private final boolean longLived;
    private final int excludedSurfaces;
    private final Map<Capability, List<CapabilityParams>> capabilityBindings;

    private ShortcutInfo(Builder b, boolean dynamic) {
        lastChangedTimestamp = System.currentTimeMillis();
        extras = b.extras == null ? null : b.extras.deepCopy();
        id = b.id;
        pkg = b.pkg;
        label = b.label;
        longLabel = b.longLabel;
        disabledMessage = b.disabledMessage;
        activity = b.activity;
        intents = copy(b.intents);
        categories = b.categories == null ? null : Collections.unmodifiableSet(new HashSet<String>(b.categories));
        icon = b.icon;
        rank = b.rank;
        this.dynamic = dynamic;
        locusId = b.locusId;
        longLived = b.longLived;
        excludedSurfaces = b.excludedSurfaces;
        if (b.capabilityBindings == null) {
            capabilityBindings = Collections.emptyMap();
        } else {
            Map<Capability, List<CapabilityParams>> map = new LinkedHashMap<Capability, List<CapabilityParams>>();
            for (Map.Entry<Capability, List<CapabilityParams>> e : b.capabilityBindings.entrySet()) {
                map.put(e.getKey(), Collections.unmodifiableList(new ArrayList<CapabilityParams>(e.getValue())));
            }
            capabilityBindings = Collections.unmodifiableMap(map);
        }
    }

    ShortcutInfo(Parcel in) {
        id = in.readString();
        pkg = in.readString();
        activity = in.readTypedObject(ComponentName.CREATOR);
        icon = in.readTypedObject(Icon.CREATOR);
        label = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        longLabel = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        disabledMessage = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        int catCount = in.readInt();
        if (catCount < 0) {
            categories = null;
        } else {
            Set<String> set = new HashSet<String>(catCount);
            for (int i = 0; i < catCount; i++) {
                set.add(in.readString());
            }
            categories = Collections.unmodifiableSet(set);
        }
        intents = in.createTypedArray(Intent.CREATOR);
        rank = in.readInt();
        extras = in.readTypedObject(PersistableBundle.CREATOR);
        lastChangedTimestamp = in.readLong();
        dynamic = in.readInt() != 0;
        locusId = in.readTypedObject(LocusId.CREATOR);
        longLived = in.readInt() != 0;
        excludedSurfaces = in.readInt();
        capabilityBindings = Collections.emptyMap();
    }

    private static Intent[] copy(Intent[] values) {
        if (values == null) return null;
        Intent[] result = new Intent[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = new Intent(values[i]);
        }
        return result;
    }

    public PersistableBundle getExtras() {
        return extras == null ? null : extras.deepCopy();
    }

    public long getLastChangedTimestamp() {
        return lastChangedTimestamp;
    }

    public UserHandle getUserHandle() {
        return UserHandle.of(UserHandle.myUserId());
    }

    public String getId() {
        return id;
    }

    public LocusId getLocusId() {
        return locusId;
    }

    public String getPackage() {
        return pkg;
    }

    public ComponentName getActivity() {
        return activity;
    }

    public CharSequence getShortLabel() {
        return label;
    }

    public CharSequence getLongLabel() {
        return longLabel;
    }

    public CharSequence getDisabledMessage() {
        return disabledMessage;
    }

    public int getDisabledReason() {
        return DISABLED_REASON_NOT_DISABLED;
    }

    public Intent getIntent() {
        return intents == null || intents.length == 0 ? null : new Intent(intents[intents.length - 1]);
    }

    public Intent[] getIntents() {
        return copy(intents);
    }

    public Set<String> getCategories() {
        return categories;
    }

    public Icon getIcon() {
        return icon;
    }

    public int getRank() {
        return rank;
    }

    public boolean isCached() {
        return longLived;
    }

    public boolean isDynamic() {
        return dynamic;
    }

    public boolean isPinned() {
        return false;
    }

    public boolean isDeclaredInManifest() {
        return false;
    }

    public boolean isImmutable() {
        return false;
    }

    public boolean isEnabled() {
        return true;
    }

    public boolean hasKeyFieldsOnly() {
        return false;
    }

    public boolean isExcludedFromSurfaces(int surface) {
        return (excludedSurfaces & surface) != 0;
    }

    public int getExcludedFromSurfaces() {
        return excludedSurfaces;
    }

    public List<Capability> getCapabilities() {
        return new ArrayList<Capability>(capabilityBindings.keySet());
    }

    public List<CapabilityParams> getCapabilityParams(Capability capability) {
        if (capability == null) throw new NullPointerException("capability");
        List<CapabilityParams> list = capabilityBindings.get(capability);
        return list == null ? Collections.<CapabilityParams>emptyList() : list;
    }

    ShortcutInfo published() {
        return new ShortcutInfo(new Builder(this), true);
    }

    boolean complete() {
        return label != null && !label.toString().isEmpty() && intents != null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(pkg);
        dest.writeTypedObject(activity, flags);
        dest.writeTypedObject(icon, flags);
        TextUtils.writeToParcel(label, dest, flags);
        TextUtils.writeToParcel(longLabel, dest, flags);
        TextUtils.writeToParcel(disabledMessage, dest, flags);
        if (categories == null) {
            dest.writeInt(-1);
        } else {
            dest.writeInt(categories.size());
            for (String c : categories) {
                dest.writeString(c);
            }
        }
        dest.writeTypedArray(intents, flags);
        dest.writeInt(rank);
        dest.writeTypedObject(extras, flags);
        dest.writeLong(lastChangedTimestamp);
        dest.writeInt(dynamic ? 1 : 0);
        dest.writeTypedObject(locusId, flags);
        dest.writeInt(longLived ? 1 : 0);
        dest.writeInt(excludedSurfaces);
    }

    public static final Parcelable.Creator<ShortcutInfo> CREATOR = new Parcelable.Creator<ShortcutInfo>() {
        @Override
        public ShortcutInfo createFromParcel(Parcel source) {
            return new ShortcutInfo(source);
        }

        @Override
        public ShortcutInfo[] newArray(int size) {
            return new ShortcutInfo[size];
        }
    };

    public static class Builder {
        private PersistableBundle extras;
        private final String id;
        private final String pkg;
        private CharSequence label;
        private CharSequence longLabel;
        private CharSequence disabledMessage;
        private ComponentName activity;
        private Intent[] intents;
        private Set<String> categories;
        private Icon icon;
        private int rank;
        private LocusId locusId;
        private boolean longLived;
        private int excludedSurfaces;
        private int startingThemeResId;
        private Person[] persons;
        private Map<Capability, List<CapabilityParams>> capabilityBindings;

        public Builder(Context context, String id) {
            if (context == null) throw new NullPointerException("context");
            if (id == null || id.isEmpty() || id.length() > 1024) throw new IllegalArgumentException("shortcut id");
            this.id = id;
            pkg = context.getPackageName();
        }

        private Builder(ShortcutInfo s) {
            extras = s.extras;
            id = s.id;
            pkg = s.pkg;
            label = s.label;
            longLabel = s.longLabel;
            disabledMessage = s.disabledMessage;
            activity = s.activity;
            intents = s.intents;
            categories = s.categories;
            icon = s.icon;
            rank = s.rank;
            locusId = s.locusId;
            longLived = s.longLived;
            excludedSurfaces = s.excludedSurfaces;
            capabilityBindings = s.capabilityBindings;
        }

        private static String text(CharSequence value) {
            if (value == null || value.length() == 0 || value.length() > 16384) {
                throw new IllegalArgumentException("shortcut label");
            }
            return value.toString();
        }

        public Builder setExtras(PersistableBundle value) {
            extras = value == null ? null : value.deepCopy();
            return this;
        }

        public Builder setLocusId(LocusId locusId) {
            if (locusId == null) throw new NullPointerException("locusId");
            this.locusId = locusId;
            return this;
        }

        public Builder setStartingTheme(int themeResId) {
            this.startingThemeResId = themeResId;
            return this;
        }

        public Builder setShortLabel(CharSequence value) {
            label = text(value);
            return this;
        }

        public Builder setLongLabel(CharSequence value) {
            longLabel = text(value);
            return this;
        }

        public Builder setDisabledMessage(CharSequence value) {
            disabledMessage = text(value);
            return this;
        }

        public Builder setActivity(ComponentName value) {
            if (value == null) throw new NullPointerException("activity");
            if (!pkg.equals(value.getPackageName())) throw new IllegalArgumentException("foreign activity");
            activity = value;
            return this;
        }

        public Builder setIcon(Icon value) {
            if (value == null) throw new NullPointerException("icon");
            icon = value;
            return this;
        }

        public Builder setRank(int value) {
            if (value < 0) throw new IllegalArgumentException("rank");
            rank = value;
            return this;
        }

        public Builder setCategories(Set<String> value) {
            if (value != null && value.size() > 128) throw new IllegalArgumentException("categories");
            categories = value == null ? null : new HashSet<String>(value);
            return this;
        }

        public Builder setIntent(Intent value) {
            return setIntents(new Intent[] { value });
        }

        public Builder setIntents(Intent[] value) {
            if (value == null || value.length == 0 || value.length > 32) {
                throw new IllegalArgumentException("intents");
            }
            for (Intent intent : value) {
                if (intent == null || intent.getAction() == null) {
                    throw new IllegalArgumentException("shortcut intent action");
                }
            }
            intents = copy(value);
            return this;
        }

        public Builder setPerson(Person person) {
            if (person == null) throw new NullPointerException("person");
            return setPersons(new Person[] { person });
        }

        public Builder setPersons(Person[] persons) {
            if (persons == null || persons.length == 0) throw new IllegalArgumentException("persons");
            this.persons = persons.clone();
            return this;
        }

        public Builder setLongLived(boolean longLived) {
            this.longLived = longLived;
            return this;
        }

        public Builder addCapabilityBinding(Capability capability, CapabilityParams capabilityParams) {
            if (capability == null) throw new NullPointerException("capability");
            if (capabilityBindings == null) {
                capabilityBindings = new LinkedHashMap<Capability, List<CapabilityParams>>();
            }
            List<CapabilityParams> list = capabilityBindings.get(capability);
            if (list == null) {
                list = new ArrayList<CapabilityParams>();
                capabilityBindings.put(capability, list);
            }
            if (capabilityParams != null) {
                list.add(capabilityParams);
            }
            return this;
        }

        public Builder setExcludedFromSurfaces(int surface) {
            this.excludedSurfaces = surface;
            return this;
        }

        public ShortcutInfo build() {
            return new ShortcutInfo(this, false);
        }
    }
}
