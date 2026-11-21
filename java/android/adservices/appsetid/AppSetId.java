// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.appsetid;

import java.util.Objects;

/** Represents an app-scoped or developer-scoped AppSetId. */
public class AppSetId {
    public static final int SCOPE_APP = 1;
    public static final int SCOPE_DEVELOPER = 2;

    private final String mId;
    private final int mScope;

    public AppSetId(String id, int scope) {
        this.mId = Objects.requireNonNull(id, "id");
        if (scope != SCOPE_APP && scope != SCOPE_DEVELOPER) {
            throw new IllegalArgumentException("Invalid scope: " + scope);
        }
        this.mScope = scope;
    }

    public String getId() {
        return mId;
    }

    public int getScope() {
        return mScope;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AppSetId)) {
            return false;
        }
        AppSetId that = (AppSetId) o;
        return mScope == that.mScope && Objects.equals(mId, that.mId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, Integer.valueOf(mScope));
    }
}
