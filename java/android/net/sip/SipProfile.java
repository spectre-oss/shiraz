// SPDX-License-Identifier: GPL-3.0-only
package android.net.sip;

import android.os.Parcel;
import android.os.Parcelable;
import java.io.Serializable;
import java.text.ParseException;
import java.util.Locale;

public class SipProfile implements Parcelable, Serializable, Cloneable {
    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_PORT = 5060;
    private static final String UDP = "UDP";
    private static final String TCP = "TCP";

    public static final Creator<SipProfile> CREATOR =
            new Creator<SipProfile>() {
                @Override
                public SipProfile createFromParcel(Parcel in) {
                    return new SipProfile(in);
                }

                @Override
                public SipProfile[] newArray(int size) {
                    return new SipProfile[size];
                }
            };

    private String mProxyAddress;
    private String mPassword;
    private String mDomain;
    private String mProtocol = UDP;
    private String mProfileName;
    private boolean mSendKeepAlive = false;
    private boolean mAutoRegistration = true;
    private transient int mCallingUid = 0;
    private int mPort = DEFAULT_PORT;
    private String mAuthUserName;
    private String mUserName;
    private String mDisplayName;

    private SipProfile() {}

    private SipProfile(Parcel in) {
        this.mProxyAddress = in.readString();
        this.mPassword = in.readString();
        this.mDomain = in.readString();
        this.mProtocol = in.readString();
        this.mProfileName = in.readString();
        this.mSendKeepAlive = in.readInt() != 0;
        this.mAutoRegistration = in.readInt() != 0;
        this.mCallingUid = in.readInt();
        this.mPort = in.readInt();
        this.mAuthUserName = in.readString();
        this.mUserName = in.readString();
        this.mDisplayName = in.readString();
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mProxyAddress);
        out.writeString(mPassword);
        out.writeString(mDomain);
        out.writeString(mProtocol);
        out.writeString(mProfileName);
        out.writeInt(mSendKeepAlive ? 1 : 0);
        out.writeInt(mAutoRegistration ? 1 : 0);
        out.writeInt(mCallingUid);
        out.writeInt(mPort);
        out.writeString(mAuthUserName);
        out.writeString(mUserName);
        out.writeString(mDisplayName);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public String getUriString() {
        String host = (mProxyAddress != null && !mProxyAddress.isEmpty()) ? mProxyAddress : mDomain;
        StringBuilder sb = new StringBuilder("sip:");
        if (mUserName != null && !mUserName.isEmpty()) {
            sb.append(mUserName).append('@');
        }
        sb.append(host);
        if (mPort != DEFAULT_PORT) {
            sb.append(':').append(mPort);
        }
        return sb.toString();
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public String getUserName() {
        return mUserName;
    }

    public String getAuthUserName() {
        return mAuthUserName;
    }

    public String getPassword() {
        return mPassword;
    }

    public String getSipDomain() {
        return mDomain;
    }

    public int getPort() {
        return mPort;
    }

    public String getProtocol() {
        return mProtocol;
    }

    public String getProxyAddress() {
        return mProxyAddress;
    }

    public String getProfileName() {
        return mProfileName;
    }

    public boolean getSendKeepAlive() {
        return mSendKeepAlive;
    }

    public boolean getAutoRegistration() {
        return mAutoRegistration;
    }

    public void setCallingUid(int uid) {
        this.mCallingUid = uid;
    }

    public static class Builder {
        private SipProfile mProfile = new SipProfile();

        public Builder(SipProfile profile) {
            if (profile == null) {
                throw new NullPointerException("profile");
            }
            try {
                this.mProfile = (SipProfile) profile.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException("should not occur", e);
            }
        }

        public Builder(String uriString) throws ParseException {
            if (uriString == null) {
                throw new NullPointerException("uriString cannot be null");
            }
            String s = uriString.trim();
            if (s.regionMatches(true, 0, "sip:", 0, 4)) {
                s = s.substring(4);
            } else if (s.contains("://")) {
                throw new ParseException("Unsupported SIP scheme: " + uriString, 0);
            }
            int semi = s.indexOf(';');
            if (semi >= 0) {
                s = s.substring(0, semi);
            }
            int at = s.indexOf('@');
            if (at <= 0 || at == s.length() - 1) {
                throw new ParseException("Invalid SIP URI (expected user@domain): " + uriString, 0);
            }
            String user = s.substring(0, at);
            String hostPort = s.substring(at + 1);
            String domain = hostPort;
            int port = DEFAULT_PORT;
            int colon = hostPort.lastIndexOf(':');
            if (colon > 0 && hostPort.indexOf(':') == colon) {
                domain = hostPort.substring(0, colon);
                try {
                    port = Integer.parseInt(hostPort.substring(colon + 1));
                } catch (NumberFormatException nfe) {
                    throw new ParseException("Invalid port in SIP URI: " + uriString, colon + 1);
                }
                if (port < 1000 || port > 65535) {
                    throw new ParseException("Port out of range in SIP URI: " + port, colon + 1);
                }
            }
            if (domain.isEmpty()) {
                throw new ParseException("Empty domain in SIP URI: " + uriString, at + 1);
            }
            mProfile.mUserName = user;
            mProfile.mDomain = domain;
            mProfile.mPort = port;
        }

        public Builder(String username, String serverDomain) throws ParseException {
            if (username == null || serverDomain == null) {
                throw new NullPointerException("username and serverDomain cannot be null");
            }
            if (username.isEmpty() || serverDomain.isEmpty()) {
                throw new ParseException("username and serverDomain cannot be empty", 0);
            }
            mProfile.mUserName = username;
            mProfile.mDomain = serverDomain;
        }

        public Builder setAuthUserName(String name) {
            mProfile.mAuthUserName = name;
            return this;
        }

        public Builder setProfileName(String name) {
            mProfile.mProfileName = name;
            return this;
        }

        public Builder setPassword(String password) {
            mProfile.mPassword = password;
            return this;
        }

        public Builder setPort(int port) throws IllegalArgumentException {
            if (port < 1000 || port > 65535) {
                throw new IllegalArgumentException("incorrect port arugment: " + port);
            }
            mProfile.mPort = port;
            return this;
        }

        public Builder setProtocol(String protocol) throws IllegalArgumentException {
            if (protocol == null) {
                throw new NullPointerException("protocol cannot be null");
            }
            String upper = protocol.toUpperCase(Locale.US);
            if (!UDP.equals(upper) && !TCP.equals(upper)) {
                throw new IllegalArgumentException("unsupported protocol: " + protocol);
            }
            mProfile.mProtocol = upper;
            return this;
        }

        public Builder setOutboundProxy(String outboundProxy) {
            mProfile.mProxyAddress = outboundProxy;
            return this;
        }

        public Builder setDisplayName(String displayName) {
            mProfile.mDisplayName = displayName;
            return this;
        }

        public Builder setSendKeepAlive(boolean flag) {
            mProfile.mSendKeepAlive = flag;
            return this;
        }

        public Builder setAutoRegistration(boolean flag) {
            mProfile.mAutoRegistration = flag;
            return this;
        }

        public SipProfile build() {
            return mProfile;
        }
    }
}
