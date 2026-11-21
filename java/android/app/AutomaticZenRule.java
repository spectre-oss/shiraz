// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.ComponentName;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.notification.ZenDeviceEffects;
import android.service.notification.ZenPolicy;
import java.util.Objects;

public final class AutomaticZenRule implements Parcelable {
    public static final int TYPE_UNKNOWN = -1;
    public static final int TYPE_OTHER = 0;
    public static final int TYPE_SCHEDULE_TIME = 1;
    public static final int TYPE_SCHEDULE_CALENDAR = 2;
    public static final int TYPE_BEDTIME = 3;
    public static final int TYPE_DRIVING = 4;
    public static final int TYPE_IMMERSIVE = 5;
    public static final int TYPE_THEATER = 6;
    public static final int TYPE_MANAGED = 7;

    private boolean mEnabled;
    private String mName;
    private int mInterruptionFilter;
    private Uri mConditionId;
    private ComponentName mOwner;
    private ComponentName mConfigurationActivity;
    private final long mCreationTime;
    private ZenPolicy mZenPolicy;
    private ZenDeviceEffects mDeviceEffects;
    private int mType = TYPE_UNKNOWN;
    private String mTriggerDescription;
    private int mIconResId;
    private boolean mAllowManualInvocation;

    @Deprecated
    public AutomaticZenRule(String name, ComponentName owner, Uri conditionId,
            int interruptionFilter, boolean enabled) {
        this(name, owner, null, conditionId, null, interruptionFilter, enabled);
    }

    public AutomaticZenRule(String name, ComponentName owner, ComponentName configurationActivity,
            Uri conditionId, ZenPolicy policy, int interruptionFilter, boolean enabled) {
        mName = name;
        mOwner = owner;
        mConfigurationActivity = configurationActivity;
        mConditionId = conditionId;
        mZenPolicy = policy;
        mInterruptionFilter = interruptionFilter;
        mEnabled = enabled;
        mCreationTime = System.currentTimeMillis();
    }

    public AutomaticZenRule(Parcel source) {
        mEnabled = source.readInt() == 1;
        mName = source.readInt() == 1 ? source.readString() : null;
        mInterruptionFilter = source.readInt();
        String uriStr = source.readString();
        mConditionId = uriStr != null ? Uri.parse(uriStr) : null;
        mOwner = source.readInt() != 0 ? new ComponentName(source.readString(), source.readString()) : null;
        mConfigurationActivity = source.readInt() != 0
                ? new ComponentName(source.readString(), source.readString())
                : null;
        mCreationTime = source.readLong();
        mZenPolicy = source.readInt() == 1 ? ZenPolicy.CREATOR.createFromParcel(source) : null;
        mDeviceEffects = source.readInt() == 1 ? ZenDeviceEffects.CREATOR.createFromParcel(source) : null;
        mType = source.readInt();
        mTriggerDescription = source.readString();
        mIconResId = source.readInt();
        mAllowManualInvocation = source.readInt() == 1;
    }

    public ComponentName getOwner() {
        return mOwner;
    }

    public ComponentName getConfigurationActivity() {
        return mConfigurationActivity;
    }

    public Uri getConditionId() {
        return mConditionId;
    }

    public int getInterruptionFilter() {
        return mInterruptionFilter;
    }

    public String getName() {
        return mName;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public ZenPolicy getZenPolicy() {
        return mZenPolicy;
    }

    public ZenDeviceEffects getDeviceEffects() {
        return mDeviceEffects;
    }

    public long getCreationTime() {
        return mCreationTime;
    }

    public void setConditionId(Uri conditionId) {
        mConditionId = conditionId;
    }

    public void setInterruptionFilter(int interruptionFilter) {
        mInterruptionFilter = interruptionFilter;
    }

    public void setName(String name) {
        mName = name;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    public void setZenPolicy(ZenPolicy zenPolicy) {
        mZenPolicy = zenPolicy;
    }

    public void setDeviceEffects(ZenDeviceEffects deviceEffects) {
        mDeviceEffects = deviceEffects;
    }

    public void setConfigurationActivity(ComponentName componentName) {
        mConfigurationActivity = componentName;
    }

    public int getType() {
        return mType;
    }

    public String getTriggerDescription() {
        return mTriggerDescription;
    }

    public int getIconResId() {
        return mIconResId;
    }

    public boolean isManualInvocationAllowed() {
        return mAllowManualInvocation;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mEnabled ? 1 : 0);
        if (mName != null) {
            dest.writeInt(1);
            dest.writeString(mName);
        } else {
            dest.writeInt(0);
        }
        dest.writeInt(mInterruptionFilter);
        dest.writeString(mConditionId != null ? mConditionId.toString() : null);
        if (mOwner != null) {
            dest.writeInt(1);
            dest.writeString(mOwner.getPackageName());
            dest.writeString(mOwner.getClassName());
        } else {
            dest.writeInt(0);
        }
        if (mConfigurationActivity != null) {
            dest.writeInt(1);
            dest.writeString(mConfigurationActivity.getPackageName());
            dest.writeString(mConfigurationActivity.getClassName());
        } else {
            dest.writeInt(0);
        }
        dest.writeLong(mCreationTime);
        if (mZenPolicy != null) {
            dest.writeInt(1);
            mZenPolicy.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        if (mDeviceEffects != null) {
            dest.writeInt(1);
            mDeviceEffects.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        dest.writeInt(mType);
        dest.writeString(mTriggerDescription);
        dest.writeInt(mIconResId);
        dest.writeInt(mAllowManualInvocation ? 1 : 0);
    }

    @Override
    public String toString() {
        return "AutomaticZenRule[enabled=" + mEnabled + ",name=" + mName
                + ",interruptionFilter=" + mInterruptionFilter
                + ",conditionId=" + mConditionId + ",type=" + mType + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AutomaticZenRule)) {
            return false;
        }
        AutomaticZenRule other = (AutomaticZenRule) o;
        return mEnabled == other.mEnabled
                && mInterruptionFilter == other.mInterruptionFilter
                && mCreationTime == other.mCreationTime
                && mType == other.mType
                && mIconResId == other.mIconResId
                && mAllowManualInvocation == other.mAllowManualInvocation
                && Objects.equals(mName, other.mName)
                && Objects.equals(mConditionId, other.mConditionId)
                && Objects.equals(mOwner, other.mOwner)
                && Objects.equals(mConfigurationActivity, other.mConfigurationActivity)
                && Objects.equals(mZenPolicy, other.mZenPolicy)
                && Objects.equals(mDeviceEffects, other.mDeviceEffects)
                && Objects.equals(mTriggerDescription, other.mTriggerDescription);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Boolean.valueOf(mEnabled), mName, Integer.valueOf(mInterruptionFilter),
                mConditionId, mOwner, mConfigurationActivity, Long.valueOf(mCreationTime),
                mZenPolicy, mDeviceEffects, Integer.valueOf(mType), mTriggerDescription,
                Integer.valueOf(mIconResId), Boolean.valueOf(mAllowManualInvocation));
    }

    public static final Parcelable.Creator<AutomaticZenRule> CREATOR =
            new Parcelable.Creator<AutomaticZenRule>() {
                @Override
                public AutomaticZenRule createFromParcel(Parcel source) {
                    return new AutomaticZenRule(source);
                }

                @Override
                public AutomaticZenRule[] newArray(int size) {
                    return new AutomaticZenRule[size];
                }
            };

    public static final class Builder {
        private String mName;
        private ComponentName mOwner;
        private Uri mConditionId;
        private int mInterruptionFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
        private boolean mEnabled = true;
        private ComponentName mConfigurationActivity;
        private ZenPolicy mPolicy;
        private ZenDeviceEffects mDeviceEffects;
        private int mType = TYPE_UNKNOWN;
        private String mDescription;
        private int mIconResId;
        private boolean mAllowManualInvocation;

        public Builder(AutomaticZenRule rule) {
            Objects.requireNonNull(rule, "rule");
            mName = rule.getName();
            mOwner = rule.getOwner();
            mConditionId = rule.getConditionId();
            mInterruptionFilter = rule.getInterruptionFilter();
            mEnabled = rule.isEnabled();
            mConfigurationActivity = rule.getConfigurationActivity();
            mPolicy = rule.getZenPolicy();
            mDeviceEffects = rule.getDeviceEffects();
            mType = rule.getType();
            mDescription = rule.getTriggerDescription();
            mIconResId = rule.getIconResId();
            mAllowManualInvocation = rule.isManualInvocationAllowed();
        }

        public Builder(String name, Uri conditionId) {
            mName = Objects.requireNonNull(name, "name");
            mConditionId = Objects.requireNonNull(conditionId, "conditionId");
        }

        public Builder setName(String name) {
            mName = name;
            return this;
        }

        public Builder setOwner(ComponentName owner) {
            mOwner = owner;
            return this;
        }

        public Builder setConditionId(Uri conditionId) {
            mConditionId = conditionId;
            return this;
        }

        public Builder setInterruptionFilter(int interruptionFilter) {
            mInterruptionFilter = interruptionFilter;
            return this;
        }

        public Builder setEnabled(boolean enabled) {
            mEnabled = enabled;
            return this;
        }

        public Builder setConfigurationActivity(ComponentName configurationActivity) {
            mConfigurationActivity = configurationActivity;
            return this;
        }

        public Builder setZenPolicy(ZenPolicy policy) {
            mPolicy = policy;
            return this;
        }

        public Builder setDeviceEffects(ZenDeviceEffects deviceEffects) {
            mDeviceEffects = deviceEffects;
            return this;
        }

        public Builder setType(int type) {
            mType = type;
            return this;
        }

        public Builder setTriggerDescription(String description) {
            mDescription = description;
            return this;
        }

        public Builder setIconResId(int iconResId) {
            mIconResId = iconResId;
            return this;
        }

        public Builder setManualInvocationAllowed(boolean allowManualInvocation) {
            mAllowManualInvocation = allowManualInvocation;
            return this;
        }

        public AutomaticZenRule build() {
            AutomaticZenRule rule = new AutomaticZenRule(mName, mOwner, mConfigurationActivity,
                    mConditionId, mPolicy, mInterruptionFilter, mEnabled);
            rule.mDeviceEffects = mDeviceEffects;
            rule.mType = mType;
            rule.mTriggerDescription = mDescription;
            rule.mIconResId = mIconResId;
            rule.mAllowManualInvocation = mAllowManualInvocation;
            return rule;
        }
    }
}
