// SPDX-License-Identifier: GPL-3.0-only
package android.content;

import android.net.Uri;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Ordered group of UriRelativeFilters associated with an allow or block action. */
public final class UriRelativeFilterGroup {
    public static final int ACTION_ALLOW = 0;
    public static final int ACTION_BLOCK = 1;

    private final int mAction;
    private final Set<UriRelativeFilter> mUriRelativeFilters = new LinkedHashSet<UriRelativeFilter>();

    public UriRelativeFilterGroup(int action) {
        if (action != ACTION_ALLOW && action != ACTION_BLOCK) {
            throw new IllegalArgumentException("Invalid action: " + action);
        }
        mAction = action;
    }

    public int getAction() {
        return mAction;
    }

    public void addUriRelativeFilter(UriRelativeFilter uriRelativeFilter) {
        Objects.requireNonNull(uriRelativeFilter);
        mUriRelativeFilters.add(uriRelativeFilter);
    }

    public Collection<UriRelativeFilter> getUriRelativeFilters() {
        return Collections.unmodifiableCollection(new ArrayList<UriRelativeFilter>(mUriRelativeFilters));
    }

    public boolean matchData(Uri data) {
        if (data == null || mUriRelativeFilters.isEmpty()) {
            return false;
        }
        for (UriRelativeFilter filter : mUriRelativeFilters) {
            if (!filter.matchData(data)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "UriRelativeFilterGroup { action = " + mAction
                + ", uriRelativeFilters = " + mUriRelativeFilters + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UriRelativeFilterGroup)) return false;
        UriRelativeFilterGroup that = (UriRelativeFilterGroup) o;
        return mAction == that.mAction
                && Objects.equals(mUriRelativeFilters, that.mUriRelativeFilters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(mAction), mUriRelativeFilters);
    }
}
