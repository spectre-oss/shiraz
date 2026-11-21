// SPDX-License-Identifier: GPL-3.0-only

package android.provider;

import android.util.Base64;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Deprecated
public final class FontRequest {
    private final String mProviderAuthority;
    private final String mProviderPackage;
    private final String mQuery;
    private final List<List<byte[]>> mCertificates;
    private final String mIdentifier;

    public FontRequest(String providerAuthority, String providerPackage, String query) {
        mProviderAuthority = Objects.requireNonNull(providerAuthority);
        mProviderPackage = Objects.requireNonNull(providerPackage);
        mQuery = Objects.requireNonNull(query);
        mCertificates = Collections.emptyList();
        mIdentifier = mProviderAuthority + "-" + mProviderPackage + "-" + mQuery;
    }

    public FontRequest(
            String providerAuthority,
            String providerPackage,
            String query,
            List<List<byte[]>> certificates) {
        mProviderAuthority = Objects.requireNonNull(providerAuthority);
        mProviderPackage = Objects.requireNonNull(providerPackage);
        mQuery = Objects.requireNonNull(query);
        Objects.requireNonNull(certificates);
        List<List<byte[]>> copy = new ArrayList<List<byte[]>>(certificates.size());
        for (int i = 0; i < certificates.size(); i++) {
            List<byte[]> set = Objects.requireNonNull(certificates.get(i));
            List<byte[]> inner = new ArrayList<byte[]>(set.size());
            for (int j = 0; j < set.size(); j++) {
                byte[] cert = Objects.requireNonNull(set.get(j));
                inner.add(cert.clone());
            }
            copy.add(Collections.unmodifiableList(inner));
        }
        mCertificates = Collections.unmodifiableList(copy);
        mIdentifier = mProviderAuthority + "-" + mProviderPackage + "-" + mQuery;
    }

    public String getProviderAuthority() {
        return mProviderAuthority;
    }

    public String getProviderPackage() {
        return mProviderPackage;
    }

    public String getQuery() {
        return mQuery;
    }

    public List<List<byte[]>> getCertificates() {
        return mCertificates;
    }

    String getIdentifier() {
        return mIdentifier;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("FontRequest {mProviderAuthority: ")
                .append(mProviderAuthority)
                .append(", mProviderPackage: ")
                .append(mProviderPackage)
                .append(", mQuery: ")
                .append(mQuery)
                .append(", mCertificates:");
        for (int i = 0; i < mCertificates.size(); i++) {
            builder.append(" [");
            List<byte[]> set = mCertificates.get(i);
            for (int j = 0; j < set.size(); j++) {
                builder.append(" \"");
                builder.append(Base64.encodeToString(set.get(j), Base64.DEFAULT));
                builder.append("\"");
            }
            builder.append(" ]");
        }
        builder.append("}");
        return builder.toString();
    }
}
