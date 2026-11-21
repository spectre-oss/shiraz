// SPDX-License-Identifier: GPL-3.0-only
package android.accessibilityservice;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public class AccessibilityServiceInfo implements Parcelable {
    public static final int CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT = 0x00000001;
    public static final int CAPABILITY_CAN_REQUEST_TOUCH_EXPLORATION = 0x00000002;
    @Deprecated
    public static final int CAPABILITY_CAN_REQUEST_ENHANCED_WEB_ACCESSIBILITY = 0x00000004;
    public static final int CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS = 0x00000008;
    public static final int CAPABILITY_CAN_CONTROL_MAGNIFICATION = 0x00000010;
    public static final int CAPABILITY_CAN_PERFORM_GESTURES = 0x00000020;
    public static final int CAPABILITY_CAN_REQUEST_FINGERPRINT_GESTURES = 0x00000040;
    public static final int CAPABILITY_CAN_TAKE_SCREENSHOT = 0x00000080;

    public static final int FEEDBACK_SPOKEN = 0x0000001;
    public static final int FEEDBACK_HAPTIC = 0x0000002;
    public static final int FEEDBACK_AUDIBLE = 0x0000004;
    public static final int FEEDBACK_VISUAL = 0x0000008;
    public static final int FEEDBACK_GENERIC = 0x0000010;
    public static final int FEEDBACK_BRAILLE = 0x0000020;
    public static final int FEEDBACK_ALL_MASK = 0xFFFFFFFF;

    public static final int DEFAULT = 0x0000001;
    public static final int FLAG_INCLUDE_NOT_IMPORTANT_VIEWS = 0x0000002;
    public static final int FLAG_REQUEST_TOUCH_EXPLORATION_MODE = 0x0000004;
    @Deprecated
    public static final int FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY = 0x00000008;
    public static final int FLAG_REPORT_VIEW_IDS = 0x00000010;
    public static final int FLAG_REQUEST_FILTER_KEY_EVENTS = 0x00000020;
    public static final int FLAG_RETRIEVE_INTERACTIVE_WINDOWS = 0x00000040;
    public static final int FLAG_ENABLE_ACCESSIBILITY_VOLUME = 0x00000080;
    public static final int FLAG_REQUEST_ACCESSIBILITY_BUTTON = 0x00000100;
    public static final int FLAG_REQUEST_FINGERPRINT_GESTURES = 0x00000200;
    public static final int FLAG_REQUEST_SHORTCUT_WARNING_DIALOG_SPOKEN_FEEDBACK = 0x00000400;
    public static final int FLAG_SERVICE_HANDLES_DOUBLE_TAP = 0x00000800;
    public static final int FLAG_REQUEST_MULTI_FINGER_GESTURES = 0x00001000;
    public static final int FLAG_REQUEST_2_FINGER_PASSTHROUGH = 0x00002000;
    public static final int FLAG_SEND_MOTION_EVENTS = 0x00004000;
    public static final int FLAG_INPUT_METHOD_EDITOR = 0x00008000;

    public int eventTypes;
    public String[] packageNames;
    public int feedbackType;
    public long notificationTimeout;
    public int flags;

    private ComponentName mComponentName;
    private ResolveInfo mResolveInfo;
    private String mSettingsActivityName;
    private String mTileServiceName;
    private int mCapabilities;
    private int mMotionEventSources;
    private int mSummaryResId;
    private String mNonLocalizedSummary;
    private int mDescriptionResId;
    private String mNonLocalizedDescription;
    private int mIntroResId;
    private int mNonInteractiveUiTimeout;
    private int mInteractiveUiTimeout;
    private boolean mIsAccessibilityTool;

    public AccessibilityServiceInfo() {
    }

    public String getId() {
        return mComponentName != null ? mComponentName.flattenToShortString() : null;
    }

    void setComponentName(ComponentName component) {
        mComponentName = component;
    }

    public ResolveInfo getResolveInfo() {
        return mResolveInfo;
    }

    void setResolveInfo(ResolveInfo resolveInfo) {
        mResolveInfo = resolveInfo;
        if (resolveInfo != null && resolveInfo.serviceInfo != null) {
            ServiceInfo si = resolveInfo.serviceInfo;
            if (si.packageName != null && si.name != null) {
                mComponentName = new ComponentName(si.packageName, si.name);
            }
        }
    }

    public String getSettingsActivityName() {
        return mSettingsActivityName;
    }

    public String getTileServiceName() {
        return mTileServiceName;
    }

    public boolean getCanRetrieveWindowContent() {
        return (mCapabilities & CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT) != 0;
    }

    public int getCapabilities() {
        return mCapabilities;
    }

    void setCapabilities(int capabilities) {
        mCapabilities = capabilities;
    }

    public int getMotionEventSources() {
        return mMotionEventSources;
    }

    public void setMotionEventSources(int motionEventSources) {
        mMotionEventSources = motionEventSources;
    }

    public CharSequence loadSummary(PackageManager packageManager) {
        if (mSummaryResId == 0) {
            return mNonLocalizedSummary;
        }
        return loadTextFromPackageManager(packageManager, mSummaryResId, mNonLocalizedSummary);
    }

    public CharSequence loadIntro(PackageManager packageManager) {
        if (mIntroResId == 0) {
            return null;
        }
        return loadTextFromPackageManager(packageManager, mIntroResId, null);
    }

    @Deprecated
    public String getDescription() {
        return mNonLocalizedDescription;
    }

    public String loadDescription(PackageManager packageManager) {
        if (mDescriptionResId == 0) {
            return mNonLocalizedDescription;
        }
        CharSequence desc = loadTextFromPackageManager(
                packageManager, mDescriptionResId, mNonLocalizedDescription);
        return desc == null ? null : desc.toString();
    }

    private CharSequence loadTextFromPackageManager(
            PackageManager pm, int resId, String fallback) {
        if (pm != null && mResolveInfo != null && mResolveInfo.serviceInfo != null) {
            ServiceInfo si = mResolveInfo.serviceInfo;
            try {
                if (si.applicationInfo != null) {
                    CharSequence text =
                            pm.getResourcesForApplication(si.applicationInfo).getText(resId);
                    if (text != null) {
                        return text.toString().trim();
                    }
                }
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            }
        }
        return fallback;
    }

    public void setNonInteractiveUiTimeoutMillis(int timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must be >= 0");
        }
        mNonInteractiveUiTimeout = timeout;
    }

    public int getNonInteractiveUiTimeoutMillis() {
        return mNonInteractiveUiTimeout;
    }

    public void setInteractiveUiTimeoutMillis(int timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must be >= 0");
        }
        mInteractiveUiTimeout = timeout;
    }

    public int getInteractiveUiTimeoutMillis() {
        return mInteractiveUiTimeout;
    }

    public boolean isAccessibilityTool() {
        return mIsAccessibilityTool;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flagz) {
        Objects.requireNonNull(parcel, "parcel");
        parcel.writeInt(eventTypes);
        parcel.writeStringArray(packageNames);
        parcel.writeInt(feedbackType);
        parcel.writeLong(notificationTimeout);
        parcel.writeInt(flags);
        parcel.writeInt(mNonInteractiveUiTimeout);
        parcel.writeInt(mInteractiveUiTimeout);
        if (mComponentName != null) {
            parcel.writeInt(1);
            parcel.writeString(mComponentName.getPackageName());
            parcel.writeString(mComponentName.getClassName());
        } else {
            parcel.writeInt(0);
        }
        parcel.writeString(mSettingsActivityName);
        parcel.writeString(mTileServiceName);
        parcel.writeInt(mCapabilities);
        parcel.writeInt(mSummaryResId);
        parcel.writeString(mNonLocalizedSummary);
        parcel.writeInt(mDescriptionResId);
        parcel.writeString(mNonLocalizedDescription);
        parcel.writeInt(mIntroResId);
        parcel.writeInt(mMotionEventSources);
        parcel.writeBoolean(mIsAccessibilityTool);
    }

    private void initFromParcel(Parcel parcel) {
        eventTypes = parcel.readInt();
        packageNames = parcel.createStringArray();
        feedbackType = parcel.readInt();
        notificationTimeout = parcel.readLong();
        flags = parcel.readInt();
        mNonInteractiveUiTimeout = parcel.readInt();
        mInteractiveUiTimeout = parcel.readInt();
        if (parcel.readInt() != 0) {
            String pkg = parcel.readString();
            String cls = parcel.readString();
            mComponentName = (pkg != null && cls != null) ? new ComponentName(pkg, cls) : null;
        } else {
            mComponentName = null;
        }
        mSettingsActivityName = parcel.readString();
        mTileServiceName = parcel.readString();
        mCapabilities = parcel.readInt();
        mSummaryResId = parcel.readInt();
        mNonLocalizedSummary = parcel.readString();
        mDescriptionResId = parcel.readInt();
        mNonLocalizedDescription = parcel.readString();
        mIntroResId = parcel.readInt();
        mMotionEventSources = parcel.readInt();
        mIsAccessibilityTool = parcel.readBoolean();
    }

    @Override
    public int hashCode() {
        return 31 + (mComponentName == null ? 0 : mComponentName.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        AccessibilityServiceInfo other = (AccessibilityServiceInfo) obj;
        return Objects.equals(mComponentName, other.mComponentName);
    }

    @Override
    public String toString() {
        return "AccessibilityServiceInfo[id=" + getId()
                + ", eventTypes=" + eventTypes
                + ", feedbackType=" + feedbackTypeToString(feedbackType)
                + ", flags=" + flags
                + ", capabilities=" + mCapabilities + "]";
    }

    public static String feedbackTypeToString(int feedbackType) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        int remaining = feedbackType;
        while (remaining != 0) {
            int feedbackTypeFlag = 1 << Integer.numberOfTrailingZeros(remaining);
            remaining &= ~feedbackTypeFlag;
            switch (feedbackTypeFlag) {
                case FEEDBACK_AUDIBLE:
                    if (builder.length() > 1) builder.append(", ");
                    builder.append("FEEDBACK_AUDIBLE");
                    break;
                case FEEDBACK_HAPTIC:
                    if (builder.length() > 1) builder.append(", ");
                    builder.append("FEEDBACK_HAPTIC");
                    break;
                case FEEDBACK_GENERIC:
                    if (builder.length() > 1) builder.append(", ");
                    builder.append("FEEDBACK_GENERIC");
                    break;
                case FEEDBACK_SPOKEN:
                    if (builder.length() > 1) builder.append(", ");
                    builder.append("FEEDBACK_SPOKEN");
                    break;
                case FEEDBACK_VISUAL:
                    if (builder.length() > 1) builder.append(", ");
                    builder.append("FEEDBACK_VISUAL");
                    break;
                case FEEDBACK_BRAILLE:
                    if (builder.length() > 1) builder.append(", ");
                    builder.append("FEEDBACK_BRAILLE");
                    break;
                default:
                    break;
            }
        }
        builder.append("]");
        return builder.toString();
    }

    public static String flagToString(int flag) {
        switch (flag) {
            case DEFAULT:
                return "DEFAULT";
            case FLAG_INCLUDE_NOT_IMPORTANT_VIEWS:
                return "FLAG_INCLUDE_NOT_IMPORTANT_VIEWS";
            case FLAG_REQUEST_TOUCH_EXPLORATION_MODE:
                return "FLAG_REQUEST_TOUCH_EXPLORATION_MODE";
            case FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY:
                return "FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY";
            case FLAG_REPORT_VIEW_IDS:
                return "FLAG_REPORT_VIEW_IDS";
            case FLAG_REQUEST_FILTER_KEY_EVENTS:
                return "FLAG_REQUEST_FILTER_KEY_EVENTS";
            case FLAG_RETRIEVE_INTERACTIVE_WINDOWS:
                return "FLAG_RETRIEVE_INTERACTIVE_WINDOWS";
            case FLAG_ENABLE_ACCESSIBILITY_VOLUME:
                return "FLAG_ENABLE_ACCESSIBILITY_VOLUME";
            case FLAG_REQUEST_ACCESSIBILITY_BUTTON:
                return "FLAG_REQUEST_ACCESSIBILITY_BUTTON";
            case FLAG_REQUEST_FINGERPRINT_GESTURES:
                return "FLAG_REQUEST_FINGERPRINT_GESTURES";
            case FLAG_REQUEST_SHORTCUT_WARNING_DIALOG_SPOKEN_FEEDBACK:
                return "FLAG_REQUEST_SHORTCUT_WARNING_DIALOG_SPOKEN_FEEDBACK";
            case FLAG_SERVICE_HANDLES_DOUBLE_TAP:
                return "FLAG_SERVICE_HANDLES_DOUBLE_TAP";
            case FLAG_REQUEST_MULTI_FINGER_GESTURES:
                return "FLAG_REQUEST_MULTI_FINGER_GESTURES";
            case FLAG_REQUEST_2_FINGER_PASSTHROUGH:
                return "FLAG_REQUEST_2_FINGER_PASSTHROUGH";
            case FLAG_SEND_MOTION_EVENTS:
                return "FLAG_SEND_MOTION_EVENTS";
            case FLAG_INPUT_METHOD_EDITOR:
                return "FLAG_INPUT_METHOD_EDITOR";
            default:
                return null;
        }
    }

    public static String capabilityToString(int capability) {
        switch (capability) {
            case CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT:
                return "CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT";
            case CAPABILITY_CAN_REQUEST_TOUCH_EXPLORATION:
                return "CAPABILITY_CAN_REQUEST_TOUCH_EXPLORATION";
            case CAPABILITY_CAN_REQUEST_ENHANCED_WEB_ACCESSIBILITY:
                return "CAPABILITY_CAN_REQUEST_ENHANCED_WEB_ACCESSIBILITY";
            case CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS:
                return "CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS";
            case CAPABILITY_CAN_CONTROL_MAGNIFICATION:
                return "CAPABILITY_CAN_CONTROL_MAGNIFICATION";
            case CAPABILITY_CAN_PERFORM_GESTURES:
                return "CAPABILITY_CAN_PERFORM_GESTURES";
            case CAPABILITY_CAN_REQUEST_FINGERPRINT_GESTURES:
                return "CAPABILITY_CAN_REQUEST_FINGERPRINT_GESTURES";
            case CAPABILITY_CAN_TAKE_SCREENSHOT:
                return "CAPABILITY_CAN_TAKE_SCREENSHOT";
            default:
                return "UNKNOWN";
        }
    }

    public static final Parcelable.Creator<AccessibilityServiceInfo> CREATOR =
            new Parcelable.Creator<AccessibilityServiceInfo>() {
                @Override
                public AccessibilityServiceInfo createFromParcel(Parcel parcel) {
                    AccessibilityServiceInfo info = new AccessibilityServiceInfo();
                    info.initFromParcel(parcel);
                    return info;
                }

                @Override
                public AccessibilityServiceInfo[] newArray(int size) {
                    return new AccessibilityServiceInfo[size];
                }
            };
}
