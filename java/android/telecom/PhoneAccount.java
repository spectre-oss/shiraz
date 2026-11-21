// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Representation of a distinct method to place or receive calls via {@link TelecomManager}. */
public final class PhoneAccount implements Parcelable {
    public static final int CAPABILITY_CONNECTION_MANAGER = 0x1;
    public static final int CAPABILITY_CALL_PROVIDER = 0x2;
    public static final int CAPABILITY_SIM_SUBSCRIPTION = 0x4;
    public static final int CAPABILITY_VIDEO_CALLING = 0x8;
    public static final int CAPABILITY_PLACE_EMERGENCY_CALLS = 0x10;
    public static final int CAPABILITY_CALL_SUBJECT = 0x40;
    public static final int CAPABILITY_VIDEO_CALLING_RELIES_ON_PRESENCE = 0x100;
    public static final int CAPABILITY_SUPPORTS_VIDEO_CALLING = 0x400;
    public static final int CAPABILITY_SELF_MANAGED = 0x800;
    public static final int CAPABILITY_RTT = 0x1000;
    public static final int CAPABILITY_ADHOC_CONFERENCE_CALLING = 0x4000;
    public static final int CAPABILITY_CALL_COMPOSER = 0x8000;
    public static final int CAPABILITY_SUPPORTS_VOICE_CALLING_INDICATIONS = 0x10000;
    public static final int CAPABILITY_VOICE_CALLING_AVAILABLE = 0x20000;
    public static final int CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS = 0x40000;
    public static final int CAPABILITY_SUPPORTS_CALL_STREAMING = 0x80000;

    public static final String EXTRA_CALL_SUBJECT_MAX_LENGTH =
            "android.telecom.extra.CALL_SUBJECT_MAX_LENGTH";
    public static final String EXTRA_CALL_SUBJECT_CHARACTER_ENCODING =
            "android.telecom.extra.CALL_SUBJECT_CHARACTER_ENCODING";
    public static final String EXTRA_ALWAYS_USE_VOIP_AUDIO_MODE =
            "android.telecom.extra.ALWAYS_USE_VOIP_AUDIO_MODE";
    public static final String EXTRA_SUPPORTS_HANDOVER_TO =
            "android.telecom.extra.SUPPORTS_HANDOVER_TO";
    public static final String EXTRA_SUPPORTS_HANDOVER_FROM =
            "android.telecom.extra.SUPPORTS_HANDOVER_FROM";
    public static final String EXTRA_LOG_SELF_MANAGED_CALLS =
            "android.telecom.extra.LOG_SELF_MANAGED_CALLS";
    public static final String EXTRA_ADD_SELF_MANAGED_CALLS_TO_INCALLSERVICE =
            "android.telecom.extra.ADD_SELF_MANAGED_CALLS_TO_INCALLSERVICE";

    public static final String SCHEME_TEL = "tel";
    public static final String SCHEME_VOICEMAIL = "voicemail";
    public static final String SCHEME_SIP = "sip";

    public static final int NO_HIGHLIGHT_COLOR = 0;
    public static final int NO_RESOURCE_ID = -1;

    private final PhoneAccountHandle mAccountHandle;
    private final Uri mAddress;
    private final Uri mSubscriptionAddress;
    private final int mCapabilities;
    private final Icon mIcon;
    private final int mHighlightColor;
    private final CharSequence mLabel;
    private final CharSequence mShortDescription;
    private final List<String> mSupportedUriSchemes;
    private final Bundle mExtras;
    private final boolean mIsEnabled;
    private final Set<PhoneAccountHandle> mSimultaneousCallingRestriction;

    private PhoneAccount(Builder b) {
        this.mAccountHandle = b.mAccountHandle;
        this.mAddress = b.mAddress;
        this.mSubscriptionAddress = b.mSubscriptionAddress;
        this.mCapabilities = b.mCapabilities;
        this.mIcon = b.mIcon;
        this.mHighlightColor = b.mHighlightColor;
        this.mLabel = b.mLabel;
        this.mShortDescription = b.mShortDescription;
        this.mSupportedUriSchemes =
                Collections.unmodifiableList(new ArrayList<String>(b.mSupportedUriSchemes));
        this.mExtras = b.mExtras == null ? null : new Bundle(b.mExtras);
        this.mIsEnabled = b.mIsEnabled;
        this.mSimultaneousCallingRestriction =
                b.mSimultaneousCallingRestriction == null
                        ? null
                        : Collections.unmodifiableSet(
                                new LinkedHashSet<PhoneAccountHandle>(
                                        b.mSimultaneousCallingRestriction));
    }

    private PhoneAccount(Parcel in) {
        this.mAccountHandle = in.readTypedObject(PhoneAccountHandle.CREATOR);
        String addr = in.readString();
        this.mAddress = addr == null ? null : Uri.parse(addr);
        String subAddr = in.readString();
        this.mSubscriptionAddress = subAddr == null ? null : Uri.parse(subAddr);
        this.mCapabilities = in.readInt();
        this.mIcon = PhoneAccountHandle.readIconFromParcel(in);
        this.mHighlightColor = in.readInt();
        this.mLabel = in.readString();
        this.mShortDescription = in.readString();
        String[] schemes = in.createStringArray();
        ArrayList<String> schemeList = new ArrayList<String>();
        if (schemes != null) {
            Collections.addAll(schemeList, schemes);
        }
        this.mSupportedUriSchemes = Collections.unmodifiableList(schemeList);
        this.mExtras = in.readBundle(PhoneAccount.class.getClassLoader());
        this.mIsEnabled = in.readBoolean();
        boolean hasRestriction = in.readBoolean();
        if (hasRestriction) {
            ArrayList<PhoneAccountHandle> handles =
                    in.createTypedArrayList(PhoneAccountHandle.CREATOR);
            LinkedHashSet<PhoneAccountHandle> set = new LinkedHashSet<PhoneAccountHandle>();
            if (handles != null) {
                set.addAll(handles);
            }
            this.mSimultaneousCallingRestriction = Collections.unmodifiableSet(set);
        } else {
            this.mSimultaneousCallingRestriction = null;
        }
    }

    public static Builder builder(PhoneAccountHandle accountHandle, CharSequence label) {
        return new Builder(accountHandle, label);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public PhoneAccountHandle getAccountHandle() {
        return mAccountHandle;
    }

    public Uri getAddress() {
        return mAddress;
    }

    public Uri getSubscriptionAddress() {
        return mSubscriptionAddress;
    }

    public int getCapabilities() {
        return mCapabilities;
    }

    public boolean hasCapabilities(int capability) {
        return (mCapabilities & capability) == capability;
    }

    public CharSequence getLabel() {
        return mLabel;
    }

    public CharSequence getShortDescription() {
        return mShortDescription;
    }

    public List<String> getSupportedUriSchemes() {
        return mSupportedUriSchemes;
    }

    public Bundle getExtras() {
        return mExtras;
    }

    public Icon getIcon() {
        return mIcon;
    }

    public boolean isEnabled() {
        return mIsEnabled;
    }

    public boolean supportsUriScheme(String uriScheme) {
        if (mSupportedUriSchemes == null || uriScheme == null) {
            return false;
        }
        for (String scheme : mSupportedUriSchemes) {
            if (scheme != null && scheme.equalsIgnoreCase(uriScheme)) {
                return true;
            }
        }
        return false;
    }

    public int getHighlightColor() {
        return mHighlightColor;
    }

    public Set<PhoneAccountHandle> getSimultaneousCallingRestriction() {
        if (mSimultaneousCallingRestriction == null) {
            throw new IllegalStateException(
                    "Simultaneous calling restriction is not set on this PhoneAccount");
        }
        return mSimultaneousCallingRestriction;
    }

    public boolean hasSimultaneousCallingRestriction() {
        return mSimultaneousCallingRestriction != null;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PhoneAccount)) {
            return false;
        }
        PhoneAccount o = (PhoneAccount) other;
        return Objects.equals(mAccountHandle, o.mAccountHandle)
                && Objects.equals(mAddress, o.mAddress)
                && Objects.equals(mSubscriptionAddress, o.mSubscriptionAddress)
                && mCapabilities == o.mCapabilities
                && mHighlightColor == o.mHighlightColor
                && Objects.equals(
                        mLabel == null ? null : mLabel.toString(),
                        o.mLabel == null ? null : o.mLabel.toString())
                && Objects.equals(
                        mShortDescription == null ? null : mShortDescription.toString(),
                        o.mShortDescription == null ? null : o.mShortDescription.toString())
                && Objects.equals(mSupportedUriSchemes, o.mSupportedUriSchemes)
                && mIsEnabled == o.mIsEnabled
                && Objects.equals(
                        mSimultaneousCallingRestriction, o.mSimultaneousCallingRestriction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mAccountHandle,
                mAddress,
                mSubscriptionAddress,
                Integer.valueOf(mCapabilities),
                Integer.valueOf(mHighlightColor),
                mLabel == null ? null : mLabel.toString(),
                mShortDescription == null ? null : mShortDescription.toString(),
                mSupportedUriSchemes,
                Boolean.valueOf(mIsEnabled),
                mSimultaneousCallingRestriction);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeTypedObject(mAccountHandle, flags);
        out.writeString(mAddress == null ? null : mAddress.toString());
        out.writeString(mSubscriptionAddress == null ? null : mSubscriptionAddress.toString());
        out.writeInt(mCapabilities);
        PhoneAccountHandle.writeIconToParcel(mIcon, out);
        out.writeInt(mHighlightColor);
        out.writeString(mLabel == null ? null : mLabel.toString());
        out.writeString(mShortDescription == null ? null : mShortDescription.toString());
        out.writeStringArray(
                mSupportedUriSchemes.toArray(new String[mSupportedUriSchemes.size()]));
        out.writeBundle(mExtras);
        out.writeBoolean(mIsEnabled);
        if (mSimultaneousCallingRestriction != null) {
            out.writeBoolean(true);
            out.writeTypedList(
                    new ArrayList<PhoneAccountHandle>(mSimultaneousCallingRestriction));
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public String toString() {
        return "PhoneAccount{handle="
                + mAccountHandle
                + ", label="
                + mLabel
                + ", capabilities="
                + mCapabilities
                + ", schemes="
                + mSupportedUriSchemes
                + "}";
    }

    public static class Builder {
        private final PhoneAccountHandle mAccountHandle;
        private final CharSequence mLabel;
        private Uri mAddress;
        private Uri mSubscriptionAddress;
        private int mCapabilities;
        private Icon mIcon;
        private int mHighlightColor = NO_HIGHLIGHT_COLOR;
        private CharSequence mShortDescription;
        private final List<String> mSupportedUriSchemes = new ArrayList<String>();
        private Bundle mExtras;
        private boolean mIsEnabled = false;
        private Set<PhoneAccountHandle> mSimultaneousCallingRestriction = null;

        public Builder(PhoneAccountHandle accountHandle, CharSequence label) {
            this.mAccountHandle = accountHandle;
            this.mLabel = label;
        }

        public Builder(PhoneAccount phoneAccount) {
            Objects.requireNonNull(phoneAccount, "phoneAccount");
            this.mAccountHandle = phoneAccount.getAccountHandle();
            this.mAddress = phoneAccount.getAddress();
            this.mSubscriptionAddress = phoneAccount.getSubscriptionAddress();
            this.mCapabilities = phoneAccount.getCapabilities();
            this.mIcon = phoneAccount.getIcon();
            this.mHighlightColor = phoneAccount.getHighlightColor();
            this.mLabel = phoneAccount.getLabel();
            this.mShortDescription = phoneAccount.getShortDescription();
            this.mSupportedUriSchemes.addAll(phoneAccount.getSupportedUriSchemes());
            this.mExtras =
                    phoneAccount.getExtras() == null
                            ? null
                            : new Bundle(phoneAccount.getExtras());
            this.mIsEnabled = phoneAccount.isEnabled();
            if (phoneAccount.hasSimultaneousCallingRestriction()) {
                this.mSimultaneousCallingRestriction =
                        new LinkedHashSet<PhoneAccountHandle>(
                                phoneAccount.getSimultaneousCallingRestriction());
            }
        }

        public Builder setAddress(Uri value) {
            this.mAddress = value;
            return this;
        }

        public Builder setSubscriptionAddress(Uri value) {
            this.mSubscriptionAddress = value;
            return this;
        }

        public Builder setCapabilities(int value) {
            this.mCapabilities = value;
            return this;
        }

        public Builder setIcon(Icon icon) {
            this.mIcon = icon;
            return this;
        }

        public Builder setHighlightColor(int value) {
            this.mHighlightColor = value;
            return this;
        }

        public Builder setShortDescription(CharSequence value) {
            this.mShortDescription = value;
            return this;
        }

        public Builder addSupportedUriScheme(String uriScheme) {
            if (uriScheme != null
                    && !uriScheme.isEmpty()
                    && !mSupportedUriSchemes.contains(uriScheme)) {
                this.mSupportedUriSchemes.add(uriScheme);
            }
            return this;
        }

        public Builder setSupportedUriSchemes(List<String> uriSchemes) {
            mSupportedUriSchemes.clear();
            if (uriSchemes != null) {
                for (String s : uriSchemes) {
                    addSupportedUriScheme(s);
                }
            }
            return this;
        }

        public Builder setExtras(Bundle extras) {
            this.mExtras = extras;
            return this;
        }

        public Builder setSimultaneousCallingRestriction(Set<PhoneAccountHandle> handles) {
            Objects.requireNonNull(handles, "handles");
            this.mSimultaneousCallingRestriction =
                    new LinkedHashSet<PhoneAccountHandle>(handles);
            return this;
        }

        public Builder clearSimultaneousCallingRestriction() {
            this.mSimultaneousCallingRestriction = null;
            return this;
        }

        public PhoneAccount build() {
            if (mSupportedUriSchemes.isEmpty()) {
                addSupportedUriScheme(SCHEME_TEL);
            }
            return new PhoneAccount(this);
        }
    }

    public static final Parcelable.Creator<PhoneAccount> CREATOR =
            new Parcelable.Creator<PhoneAccount>() {
                @Override
                public PhoneAccount createFromParcel(Parcel in) {
                    return new PhoneAccount(in);
                }

                @Override
                public PhoneAccount[] newArray(int size) {
                    return new PhoneAccount[size];
                }
            };
}
