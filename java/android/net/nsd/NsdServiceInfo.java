// SPDX-License-Identifier: GPL-3.0-only
package android.net.nsd;

import android.net.Network;
import android.os.Parcel;
import android.os.Parcelable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class NsdServiceInfo implements Parcelable {
    public static final Creator<NsdServiceInfo> CREATOR =
            new Creator<NsdServiceInfo>() {
                @Override
                public NsdServiceInfo createFromParcel(Parcel in) {
                    NsdServiceInfo info = new NsdServiceInfo();
                    info.mServiceName = in.readString();
                    info.mServiceType = in.readString();
                    int addrCount = in.readInt();
                    for (int i = 0; i < addrCount; i++) {
                        byte[] raw = in.createByteArray();
                        if (raw != null) {
                            try {
                                info.mHostAddresses.add(InetAddress.getByAddress(raw));
                            } catch (UnknownHostException ignored) {
                            }
                        }
                    }
                    info.mPort = in.readInt();
                    int attrCount = in.readInt();
                    for (int i = 0; i < attrCount; i++) {
                        String key = in.readString();
                        boolean hasVal = in.readInt() != 0;
                        byte[] val = hasVal ? in.createByteArray() : null;
                        info.mTxtRecord.put(key, val);
                    }
                    info.mNetwork = in.readParcelable(Network.class.getClassLoader());
                    int subCount = in.readInt();
                    for (int i = 0; i < subCount; i++) {
                        info.mSubtypes.add(in.readString());
                    }
                    return info;
                }

                @Override
                public NsdServiceInfo[] newArray(int size) {
                    return new NsdServiceInfo[size];
                }
            };

    private String mServiceName;
    private String mServiceType;
    private final Map<String, byte[]> mTxtRecord = new LinkedHashMap<String, byte[]>();
    private final List<InetAddress> mHostAddresses = new ArrayList<InetAddress>();
    private int mPort;
    private Network mNetwork;
    private final Set<String> mSubtypes = new LinkedHashSet<String>();

    public NsdServiceInfo() {}

    public String getServiceName() {
        return mServiceName;
    }

    public void setServiceName(String s) {
        this.mServiceName = s;
    }

    public String getServiceType() {
        return mServiceType;
    }

    public void setServiceType(String s) {
        this.mServiceType = s;
    }

    @Deprecated
    public InetAddress getHost() {
        return mHostAddresses.isEmpty() ? null : mHostAddresses.get(0);
    }

    @Deprecated
    public void setHost(InetAddress s) {
        mHostAddresses.clear();
        if (s != null) {
            mHostAddresses.add(s);
        }
    }

    public int getPort() {
        return mPort;
    }

    public void setPort(int p) {
        this.mPort = p;
    }

    public List<InetAddress> getHostAddresses() {
        return Collections.unmodifiableList(new ArrayList<InetAddress>(mHostAddresses));
    }

    public void setHostAddresses(List<InetAddress> addresses) {
        Objects.requireNonNull(addresses, "addresses cannot be null");
        mHostAddresses.clear();
        mHostAddresses.addAll(addresses);
    }

    public void setAttribute(String key, String value) {
        Objects.requireNonNull(key, "Key cannot be null");
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be empty");
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c < 0x20 || c > 0x7E || c == '=') {
                throw new IllegalArgumentException("Invalid character in key: " + key);
            }
        }
        byte[] valueBytes = value == null ? null : value.getBytes(StandardCharsets.UTF_8);
        if (key.length() + (valueBytes == null ? 0 : valueBytes.length) >= 255) {
            throw new IllegalArgumentException("Key length + value length must be < 255 bytes");
        }
        mTxtRecord.put(key, valueBytes);
    }

    public void removeAttribute(String key) {
        mTxtRecord.remove(key);
    }

    public Map<String, byte[]> getAttributes() {
        Map<String, byte[]> copy = new LinkedHashMap<String, byte[]>(mTxtRecord.size());
        for (Map.Entry<String, byte[]> e : mTxtRecord.entrySet()) {
            byte[] v = e.getValue();
            copy.put(e.getKey(), v == null ? null : v.clone());
        }
        return Collections.unmodifiableMap(copy);
    }

    public Network getNetwork() {
        return mNetwork;
    }

    public void setNetwork(Network network) {
        this.mNetwork = network;
    }

    public void setSubtypes(Set<String> subtypes) {
        Objects.requireNonNull(subtypes, "subtypes cannot be null");
        mSubtypes.clear();
        mSubtypes.addAll(subtypes);
    }

    public Set<String> getSubtypes() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(mSubtypes));
    }

    @Override
    public String toString() {
        return "NsdServiceInfo{name="
                + mServiceName
                + ", type="
                + mServiceType
                + ", hosts="
                + mHostAddresses
                + ", port="
                + mPort
                + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mServiceName);
        dest.writeString(mServiceType);
        dest.writeInt(mHostAddresses.size());
        for (int i = 0; i < mHostAddresses.size(); i++) {
            dest.writeByteArray(mHostAddresses.get(i).getAddress());
        }
        dest.writeInt(mPort);
        dest.writeInt(mTxtRecord.size());
        for (Map.Entry<String, byte[]> e : mTxtRecord.entrySet()) {
            dest.writeString(e.getKey());
            byte[] v = e.getValue();
            if (v != null) {
                dest.writeInt(1);
                dest.writeByteArray(v);
            } else {
                dest.writeInt(0);
            }
        }
        dest.writeParcelable(mNetwork, flags);
        dest.writeInt(mSubtypes.size());
        for (String s : mSubtypes) {
            dest.writeString(s);
        }
    }
}
