// SPDX-License-Identifier: GPL-3.0-only
package android.content;

import android.net.Uri;
import android.os.PatternMatcher;
import java.util.Objects;

/** Matches the path, query, or fragment component of a Uri using PatternMatcher rules. */
public final class UriRelativeFilter {
    public static final int PATH = 0;
    public static final int QUERY = 1;
    public static final int FRAGMENT = 2;

    private final int mUriPart;
    private final int mPatternType;
    private final String mFilter;
    private final PatternMatcher mMatcher;

    public UriRelativeFilter(int uriPart, int patternType, String filter) {
        if (uriPart != PATH && uriPart != QUERY && uriPart != FRAGMENT) {
            throw new IllegalArgumentException("Invalid uriPart: " + uriPart);
        }
        if (filter == null) {
            throw new NullPointerException("filter must not be null");
        }
        mUriPart = uriPart;
        mPatternType = patternType;
        mFilter = filter;
        mMatcher = new PatternMatcher(filter, patternType);
    }

    public int getUriPart() {
        return mUriPart;
    }

    public int getPatternType() {
        return mPatternType;
    }

    public String getFilter() {
        return mFilter;
    }

    public boolean matchData(Uri data) {
        if (data == null) {
            return false;
        }
        switch (mUriPart) {
            case PATH:
                return mMatcher.match(data.getPath());
            case QUERY:
                return matchQuery(data.getEncodedQuery());
            case FRAGMENT:
                return mMatcher.match(data.getFragment());
            default:
                return false;
        }
    }

    private boolean matchQuery(String query) {
        if (query == null) {
            return false;
        }
        if (mMatcher.match(query)) {
            return true;
        }
        String[] params = query.split("&");
        for (String param : params) {
            if (mMatcher.match(param)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "UriRelativeFilter { uriPart = " + mUriPart
                + ", patternType = " + mPatternType
                + ", filter = " + mFilter + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UriRelativeFilter)) return false;
        UriRelativeFilter that = (UriRelativeFilter) o;
        return mUriPart == that.mUriPart
                && mPatternType == that.mPatternType
                && Objects.equals(mFilter, that.mFilter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(mUriPart), Integer.valueOf(mPatternType), mFilter);
    }
}
