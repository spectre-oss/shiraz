// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class TvInputInfo implements Parcelable {
    public static final int TYPE_TUNER = 0;
    public static final int TYPE_OTHER = 1000;
    public static final int TYPE_COMPOSITE = 1001;
    public static final int TYPE_SVIDEO = 1002;
    public static final int TYPE_SCART = 1003;
    public static final int TYPE_COMPONENT = 1004;
    public static final int TYPE_VGA = 1005;
    public static final int TYPE_DVI = 1006;
    public static final int TYPE_HDMI = 1007;
    public static final int TYPE_DISPLAY_PORT = 1008;

    public static final String EXTRA_INPUT_ID = "android.media.tv.extra.INPUT_ID";

    public static final class Builder {
        private final Context mContext;
        private final ComponentName mComponentName;
        private Integer mTunerCount;
        private Boolean mCanRecord;
        private Boolean mCanPauseRecording;
        private Bundle mExtras;

        public Builder(Context context, ComponentName component) {
            Objects.requireNonNull(context, "context cannot be null.");
            Objects.requireNonNull(component, "component cannot be null.");
            mContext = context;
            mComponentName = component;
        }

        public Builder setTunerCount(int tunerCount) {
            if (tunerCount < 0) {
                throw new IllegalArgumentException("Invalid tuner count: " + tunerCount);
            }
            mTunerCount = tunerCount;
            return this;
        }

        public Builder setCanRecord(boolean canRecord) {
            mCanRecord = canRecord;
            return this;
        }

        public Builder setCanPauseRecording(boolean canPauseRecording) {
            mCanPauseRecording = canPauseRecording;
            return this;
        }

        public Builder setExtras(Bundle extras) {
            mExtras = extras != null ? new Bundle(extras) : null;
            return this;
        }

        public TvInputInfo build() {
            ServiceInfo si = null;
            if (mContext.getPackageManager() != null) {
                try {
                    si = mContext.getPackageManager().getServiceInfo(
                            mComponentName,
                            PackageManager.GET_SERVICES | PackageManager.GET_META_DATA);
                } catch (PackageManager.NameNotFoundException ignored) {
                    si = null;
                }
            }
            if (si == null) {
                si = new ServiceInfo();
                si.packageName = mComponentName.getPackageName();
                si.name = mComponentName.getClassName();
            }
            String id = mComponentName.flattenToShortString();
            int tunerCount = mTunerCount != null ? mTunerCount : 1;
            boolean canRecord = mCanRecord != null && mCanRecord;
            boolean canPause = mCanPauseRecording != null && mCanPauseRecording;
            return new TvInputInfo(
                    si, id, null, TYPE_TUNER, false,
                    null, null, tunerCount, canRecord, canPause, mExtras);
        }
    }

    private final ServiceInfo mService;
    private final String mId;
    private final String mParentId;
    private final int mType;
    private final boolean mIsHardwareInput;
    private final String mSetupActivity;
    private final String mSettingsActivity;
    private final int mTunerCount;
    private final boolean mCanRecord;
    private final boolean mCanPauseRecording;
    private final Bundle mExtras;

    TvInputInfo(
            ServiceInfo service,
            String id,
            String parentId,
            int type,
            boolean isHardwareInput,
            String setupActivity,
            String settingsActivity,
            int tunerCount,
            boolean canRecord,
            boolean canPauseRecording,
            Bundle extras) {
        mService = service;
        mId = id;
        mParentId = parentId;
        mType = type;
        mIsHardwareInput = isHardwareInput;
        mSetupActivity = setupActivity;
        mSettingsActivity = settingsActivity;
        mTunerCount = tunerCount;
        mCanRecord = canRecord;
        mCanPauseRecording = canPauseRecording;
        mExtras = extras != null ? new Bundle(extras) : new Bundle();
    }

    public String getId() {
        return mId;
    }

    public String getParentId() {
        return mParentId;
    }

    public ServiceInfo getServiceInfo() {
        return mService;
    }

    public Intent createSetupIntent() {
        if (mSetupActivity != null && !mSetupActivity.isEmpty() && mService != null) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(mService.packageName, mSetupActivity);
            intent.putExtra(EXTRA_INPUT_ID, getId());
            return intent;
        }
        return null;
    }

    @Deprecated
    public Intent createSettingsIntent() {
        if (mSettingsActivity != null && !mSettingsActivity.isEmpty() && mService != null) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(mService.packageName, mSettingsActivity);
            intent.putExtra(EXTRA_INPUT_ID, getId());
            return intent;
        }
        return null;
    }

    public int getType() {
        return mType;
    }

    public int getTunerCount() {
        return mTunerCount;
    }

    public boolean canRecord() {
        return mCanRecord;
    }

    public boolean canPauseRecording() {
        return mCanPauseRecording;
    }

    public Bundle getExtras() {
        return new Bundle(mExtras);
    }

    public boolean isPassthroughInput() {
        return mType != TYPE_TUNER;
    }

    public boolean isHidden(Context context) {
        return false;
    }

    public CharSequence loadLabel(Context context) {
        if (mService != null && context != null && context.getPackageManager() != null) {
            CharSequence cs = mService.loadLabel(context.getPackageManager());
            if (cs != null) {
                return cs;
            }
        }
        return mId;
    }

    public CharSequence loadCustomLabel(Context context) {
        return null;
    }

    public Drawable loadIcon(Context context) {
        if (mService != null && context != null && context.getPackageManager() != null) {
            return mService.loadIcon(context.getPackageManager());
        }
        return null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mId);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof TvInputInfo)) return false;
        TvInputInfo obj = (TvInputInfo) o;
        return Objects.equals(mId, obj.mId)
                && Objects.equals(mParentId, obj.mParentId)
                && mType == obj.mType
                && mIsHardwareInput == obj.mIsHardwareInput
                && mTunerCount == obj.mTunerCount
                && mCanRecord == obj.mCanRecord
                && mCanPauseRecording == obj.mCanPauseRecording;
    }

    @Override
    public String toString() {
        return "TvInputInfo{id=" + mId
                + ", pkg=" + (mService != null ? mService.packageName : null)
                + ", service=" + (mService != null ? mService.name : null) + "}";
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (mService != null) {
            dest.writeInt(1);
            dest.writeString(mService.packageName);
            dest.writeString(mService.name);
            dest.writeString(mService.permission);
            dest.writeInt(mService.flags);
            dest.writeInt(mService.foregroundServiceType);
        } else {
            dest.writeInt(0);
        }
        dest.writeString(mId);
        dest.writeString(mParentId);
        dest.writeInt(mType);
        dest.writeInt(mIsHardwareInput ? 1 : 0);
        dest.writeString(mSetupActivity);
        dest.writeString(mSettingsActivity);
        dest.writeInt(mTunerCount);
        dest.writeInt(mCanRecord ? 1 : 0);
        dest.writeInt(mCanPauseRecording ? 1 : 0);
        dest.writeBundle(mExtras);
    }

    public static final Parcelable.Creator<TvInputInfo> CREATOR =
            new Parcelable.Creator<TvInputInfo>() {
                @Override
                public TvInputInfo createFromParcel(Parcel in) {
                    ServiceInfo si = null;
                    if (in.readInt() != 0) {
                        si = new ServiceInfo();
                        si.packageName = in.readString();
                        si.name = in.readString();
                        si.permission = in.readString();
                        si.flags = in.readInt();
                        si.foregroundServiceType = in.readInt();
                    }
                    String id = in.readString();
                    String parentId = in.readString();
                    int type = in.readInt();
                    boolean isHardware = in.readInt() != 0;
                    String setupActivity = in.readString();
                    String settingsActivity = in.readString();
                    int tunerCount = in.readInt();
                    boolean canRecord = in.readInt() != 0;
                    boolean canPause = in.readInt() != 0;
                    Bundle extras = in.readBundle();
                    return new TvInputInfo(
                            si, id, parentId, type, isHardware,
                            setupActivity, settingsActivity,
                            tunerCount, canRecord, canPause, extras);
                }

                @Override
                public TvInputInfo[] newArray(int size) {
                    return new TvInputInfo[size];
                }
            };
}
