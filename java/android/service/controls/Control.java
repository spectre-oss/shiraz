// SPDX-License-Identifier: GPL-3.0-only
package android.service.controls;

import android.app.PendingIntent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.controls.templates.ControlTemplate;
import android.text.TextUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class Control implements Parcelable {
    public static final int STATUS_UNKNOWN = 0;
    public static final int STATUS_OK = 1;
    public static final int STATUS_NOT_FOUND = 2;
    public static final int STATUS_ERROR = 3;
    public static final int STATUS_DISABLED = 4;

    private static final AtomicInteger NEXT_TEMPLATE_KEY = new AtomicInteger(1);
    private static final Map<Integer, ControlTemplate> TEMPLATE_CACHE =
            new HashMap<Integer, ControlTemplate>();

    public static final Parcelable.Creator<Control> CREATOR =
            new Parcelable.Creator<Control>() {
                @Override
                public Control createFromParcel(Parcel source) {
                    String id = source.readString();
                    int type = source.readInt();
                    CharSequence title = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
                    CharSequence subtitle = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
                    CharSequence structure = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
                    CharSequence zone = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
                    PendingIntent pi = source.readTypedObject(PendingIntent.CREATOR);
                    Icon customIcon = source.readTypedObject(Icon.CREATOR);
                    ColorStateList customColor = source.readTypedObject(ColorStateList.CREATOR);
                    int status = source.readInt();
                    int tplKey = source.readInt();
                    ControlTemplate tpl;
                    synchronized (TEMPLATE_CACHE) {
                        tpl = TEMPLATE_CACHE.get(Integer.valueOf(tplKey));
                    }
                    if (tpl == null) {
                        tpl = ControlTemplate.getNoTemplateObject();
                    }
                    CharSequence statusText = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
                    boolean authReq = source.readByte() != 0;
                    return new Control(
                            id != null ? id : "",
                            type,
                            title != null ? title : "",
                            subtitle != null ? subtitle : "",
                            structure,
                            zone,
                            pi,
                            customIcon,
                            customColor,
                            status,
                            tpl,
                            statusText != null ? statusText : "",
                            authReq);
                }

                @Override
                public Control[] newArray(int size) {
                    return new Control[size];
                }
            };

    private final String mControlId;
    private final int mDeviceType;
    private final CharSequence mTitle;
    private final CharSequence mSubtitle;
    private final CharSequence mStructure;
    private final CharSequence mZone;
    private final PendingIntent mAppIntent;
    private final Icon mCustomIcon;
    private final ColorStateList mCustomColor;
    private final int mStatus;
    private final ControlTemplate mControlTemplate;
    private final CharSequence mStatusText;
    private final boolean mAuthRequired;

    Control(String controlId, int deviceType, CharSequence title, CharSequence subtitle,
            CharSequence structure, CharSequence zone, PendingIntent appIntent, Icon customIcon,
            ColorStateList customColor, int status, ControlTemplate controlTemplate,
            CharSequence statusText, boolean authRequired) {
        mControlId = Objects.requireNonNull(controlId, "controlId must not be null");
        mDeviceType = deviceType;
        mTitle = Objects.requireNonNull(title, "title must not be null");
        mSubtitle = Objects.requireNonNull(subtitle, "subtitle must not be null");
        mStructure = structure;
        mZone = zone;
        mAppIntent = Objects.requireNonNull(appIntent, "appIntent must not be null");
        mCustomIcon = customIcon;
        mCustomColor = customColor;
        mStatus = status;
        mControlTemplate = Objects.requireNonNull(
                controlTemplate, "controlTemplate must not be null");
        mStatusText = Objects.requireNonNull(statusText, "statusText must not be null");
        mAuthRequired = authRequired;
    }

    public String getControlId() { return mControlId; }
    public int getDeviceType() { return mDeviceType; }
    public CharSequence getTitle() { return mTitle; }
    public CharSequence getSubtitle() { return mSubtitle; }
    public CharSequence getStructure() { return mStructure; }
    public CharSequence getZone() { return mZone; }
    public PendingIntent getAppIntent() { return mAppIntent; }
    public Icon getCustomIcon() { return mCustomIcon; }
    public ColorStateList getCustomColor() { return mCustomColor; }
    public int getStatus() { return mStatus; }
    public ControlTemplate getControlTemplate() { return mControlTemplate; }
    public CharSequence getStatusText() { return mStatusText; }
    public boolean isAuthRequired() { return mAuthRequired; }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mControlId);
        dest.writeInt(mDeviceType);
        TextUtils.writeToParcel(mTitle, dest, flags);
        TextUtils.writeToParcel(mSubtitle, dest, flags);
        TextUtils.writeToParcel(mStructure, dest, flags);
        TextUtils.writeToParcel(mZone, dest, flags);
        dest.writeTypedObject(mAppIntent, flags);
        dest.writeTypedObject(mCustomIcon, flags);
        dest.writeTypedObject(mCustomColor, flags);
        dest.writeInt(mStatus);
        int tplKey = NEXT_TEMPLATE_KEY.getAndIncrement();
        synchronized (TEMPLATE_CACHE) {
            TEMPLATE_CACHE.put(Integer.valueOf(tplKey), mControlTemplate);
        }
        dest.writeInt(tplKey);
        TextUtils.writeToParcel(mStatusText, dest, flags);
        dest.writeByte((byte) (mAuthRequired ? 1 : 0));
    }

    public static final class StatelessBuilder {
        private String mControlId;
        private int mDeviceType = DeviceTypes.TYPE_UNKNOWN;
        private CharSequence mTitle = "";
        private CharSequence mSubtitle = "";
        private CharSequence mStructure;
        private CharSequence mZone;
        private PendingIntent mAppIntent;
        private Icon mCustomIcon;
        private ColorStateList mCustomColor;

        public StatelessBuilder(String controlId, PendingIntent appIntent) {
            mControlId = Objects.requireNonNull(controlId, "controlId must not be null");
            mAppIntent = Objects.requireNonNull(appIntent, "appIntent must not be null");
        }

        public StatelessBuilder(Control control) {
            Objects.requireNonNull(control, "control must not be null");
            mControlId = control.mControlId;
            mDeviceType = control.mDeviceType;
            mTitle = control.mTitle;
            mSubtitle = control.mSubtitle;
            mStructure = control.mStructure;
            mZone = control.mZone;
            mAppIntent = control.mAppIntent;
            mCustomIcon = control.mCustomIcon;
            mCustomColor = control.mCustomColor;
        }

        public StatelessBuilder setControlId(String controlId) {
            mControlId = Objects.requireNonNull(controlId);
            return this;
        }

        public StatelessBuilder setDeviceType(int deviceType) {
            mDeviceType = deviceType;
            return this;
        }

        public StatelessBuilder setTitle(CharSequence title) {
            mTitle = Objects.requireNonNull(title);
            return this;
        }

        public StatelessBuilder setSubtitle(CharSequence subtitle) {
            mSubtitle = Objects.requireNonNull(subtitle);
            return this;
        }

        public StatelessBuilder setStructure(CharSequence structure) {
            mStructure = structure;
            return this;
        }

        public StatelessBuilder setZone(CharSequence zone) {
            mZone = zone;
            return this;
        }

        public StatelessBuilder setAppIntent(PendingIntent appIntent) {
            mAppIntent = Objects.requireNonNull(appIntent);
            return this;
        }

        public StatelessBuilder setCustomIcon(Icon customIcon) {
            mCustomIcon = customIcon;
            return this;
        }

        public StatelessBuilder setCustomColor(ColorStateList customColor) {
            mCustomColor = customColor;
            return this;
        }

        public Control build() {
            return new Control(mControlId, mDeviceType, mTitle, mSubtitle, mStructure, mZone,
                    mAppIntent, mCustomIcon, mCustomColor, STATUS_UNKNOWN,
                    ControlTemplate.getNoTemplateObject(), "", true);
        }
    }

    public static final class StatefulBuilder {
        private String mControlId;
        private int mDeviceType = DeviceTypes.TYPE_UNKNOWN;
        private CharSequence mTitle = "";
        private CharSequence mSubtitle = "";
        private CharSequence mStructure;
        private CharSequence mZone;
        private PendingIntent mAppIntent;
        private Icon mCustomIcon;
        private ColorStateList mCustomColor;
        private int mStatus = STATUS_UNKNOWN;
        private ControlTemplate mControlTemplate = ControlTemplate.getNoTemplateObject();
        private CharSequence mStatusText = "";
        private boolean mAuthRequired = true;

        public StatefulBuilder(String controlId, PendingIntent appIntent) {
            mControlId = Objects.requireNonNull(controlId, "controlId must not be null");
            mAppIntent = Objects.requireNonNull(appIntent, "appIntent must not be null");
        }

        public StatefulBuilder(Control control) {
            Objects.requireNonNull(control, "control must not be null");
            mControlId = control.mControlId;
            mDeviceType = control.mDeviceType;
            mTitle = control.mTitle;
            mSubtitle = control.mSubtitle;
            mStructure = control.mStructure;
            mZone = control.mZone;
            mAppIntent = control.mAppIntent;
            mCustomIcon = control.mCustomIcon;
            mCustomColor = control.mCustomColor;
            mStatus = control.mStatus;
            mControlTemplate = control.mControlTemplate;
            mStatusText = control.mStatusText;
            mAuthRequired = control.mAuthRequired;
        }

        public StatefulBuilder setControlId(String controlId) {
            mControlId = Objects.requireNonNull(controlId);
            return this;
        }

        public StatefulBuilder setDeviceType(int deviceType) {
            mDeviceType = deviceType;
            return this;
        }

        public StatefulBuilder setTitle(CharSequence title) {
            mTitle = Objects.requireNonNull(title);
            return this;
        }

        public StatefulBuilder setSubtitle(CharSequence subtitle) {
            mSubtitle = Objects.requireNonNull(subtitle);
            return this;
        }

        public StatefulBuilder setStructure(CharSequence structure) {
            mStructure = structure;
            return this;
        }

        public StatefulBuilder setZone(CharSequence zone) {
            mZone = zone;
            return this;
        }

        public StatefulBuilder setAppIntent(PendingIntent appIntent) {
            mAppIntent = Objects.requireNonNull(appIntent);
            return this;
        }

        public StatefulBuilder setCustomIcon(Icon customIcon) {
            mCustomIcon = customIcon;
            return this;
        }

        public StatefulBuilder setCustomColor(ColorStateList customColor) {
            mCustomColor = customColor;
            return this;
        }

        public StatefulBuilder setStatus(int status) {
            mStatus = status;
            return this;
        }

        public StatefulBuilder setControlTemplate(ControlTemplate controlTemplate) {
            mControlTemplate = Objects.requireNonNull(controlTemplate);
            return this;
        }

        public StatefulBuilder setStatusText(CharSequence statusText) {
            mStatusText = Objects.requireNonNull(statusText);
            return this;
        }

        public StatefulBuilder setAuthRequired(boolean authRequired) {
            mAuthRequired = authRequired;
            return this;
        }

        public Control build() {
            return new Control(mControlId, mDeviceType, mTitle, mSubtitle, mStructure, mZone,
                    mAppIntent, mCustomIcon, mCustomColor, mStatus, mControlTemplate,
                    mStatusText, mAuthRequired);
        }
    }
}
