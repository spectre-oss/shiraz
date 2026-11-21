// SPDX-License-Identifier: GPL-3.0-only
package android.security.identity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PersonalizationData {
    final List<AccessControlProfile> mProfiles;
    final Map<String, Map<String, byte[]>> mNamespaces;

    PersonalizationData(List<AccessControlProfile> profiles,
            Map<String, Map<String, byte[]>> namespaces) {
        mProfiles = profiles;
        mNamespaces = namespaces;
    }

    public static final class Builder {
        private final List<AccessControlProfile> mProfiles = new ArrayList<>();
        private final Map<String, Map<String, byte[]>> mNamespaces = new LinkedHashMap<>();

        public Builder() {
        }

        public Builder putEntry(String namespace, String name,
                Collection<AccessControlProfileId> accessControlProfileIds, byte[] value) {
            Objects.requireNonNull(namespace, "namespace == null");
            Objects.requireNonNull(name, "name == null");
            Objects.requireNonNull(accessControlProfileIds, "accessControlProfileIds == null");
            Objects.requireNonNull(value, "value == null");
            Map<String, byte[]> entries = mNamespaces.get(namespace);
            if (entries == null) {
                entries = new LinkedHashMap<>();
                mNamespaces.put(namespace, entries);
            }
            entries.put(name, value.clone());
            return this;
        }

        public Builder addAccessControlProfile(AccessControlProfile profile) {
            mProfiles.add(Objects.requireNonNull(profile, "profile == null"));
            return this;
        }

        public PersonalizationData build() {
            return new PersonalizationData(new ArrayList<>(mProfiles),
                    new LinkedHashMap<>(mNamespaces));
        }
    }
}
