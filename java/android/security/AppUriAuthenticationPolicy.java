// SPDX-License-Identifier: GPL-3.0-only
package android.security;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class AppUriAuthenticationPolicy implements Parcelable {
    private final Map<String, Map<Uri, String>> mAppAndUriMappings;

    private AppUriAuthenticationPolicy(Map<String, Map<Uri, String>> mappings) {
        Map<String, Map<Uri, String>> copy = new HashMap<>();
        for (Map.Entry<String, Map<Uri, String>> entry : mappings.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableMap(new HashMap<>(entry.getValue())));
        }
        mAppAndUriMappings = Collections.unmodifiableMap(copy);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mAppAndUriMappings.size());
        for (Map.Entry<String, Map<Uri, String>> appEntry : mAppAndUriMappings.entrySet()) {
            dest.writeString(appEntry.getKey());
            Map<Uri, String> uriMap = appEntry.getValue();
            dest.writeInt(uriMap.size());
            for (Map.Entry<Uri, String> uriEntry : uriMap.entrySet()) {
                dest.writeString(uriEntry.getKey().toString());
                dest.writeString(uriEntry.getValue());
            }
        }
    }

    public Map<String, Map<Uri, String>> getAppAndUriMappings() {
        return mAppAndUriMappings;
    }

    @Override
    public String toString() {
        return "AppUriAuthenticationPolicy{mPackageNameToUris=" + mAppAndUriMappings + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AppUriAuthenticationPolicy)) return false;
        AppUriAuthenticationPolicy other = (AppUriAuthenticationPolicy) obj;
        return Objects.equals(mAppAndUriMappings, other.mAppAndUriMappings);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mAppAndUriMappings);
    }

    public static final Creator<AppUriAuthenticationPolicy> CREATOR =
            new Creator<AppUriAuthenticationPolicy>() {
                @Override
                public AppUriAuthenticationPolicy createFromParcel(Parcel in) {
                    int appCount = in.readInt();
                    Map<String, Map<Uri, String>> mappings = new HashMap<>();
                    for (int i = 0; i < appCount; i++) {
                        String pkg = in.readString();
                        int uriCount = in.readInt();
                        Map<Uri, String> uriMap = new HashMap<>();
                        for (int j = 0; j < uriCount; j++) {
                            Uri uri = Uri.parse(in.readString());
                            String alias = in.readString();
                            uriMap.put(uri, alias);
                        }
                        mappings.put(pkg, uriMap);
                    }
                    return new AppUriAuthenticationPolicy(mappings);
                }

                @Override
                public AppUriAuthenticationPolicy[] newArray(int size) {
                    return new AppUriAuthenticationPolicy[size];
                }
            };

    public static final class Builder {
        private final Map<String, Map<Uri, String>> mMappings = new HashMap<>();

        public Builder() {
        }

        public Builder addAppAndUriMapping(String appPackageName, Uri uri, String alias) {
            Objects.requireNonNull(appPackageName, "appPackageName must not be null");
            Objects.requireNonNull(uri, "uri must not be null");
            Objects.requireNonNull(alias, "alias must not be null");
            Map<Uri, String> uriMap = mMappings.get(appPackageName);
            if (uriMap == null) {
                uriMap = new HashMap<>();
                mMappings.put(appPackageName, uriMap);
            }
            uriMap.put(uri, alias);
            return this;
        }

        public AppUriAuthenticationPolicy build() {
            return new AppUriAuthenticationPolicy(mMappings);
        }
    }
}
