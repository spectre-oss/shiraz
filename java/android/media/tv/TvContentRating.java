// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class TvContentRating {
    public static final TvContentRating UNRATED =
            new TvContentRating("null", "null", "null", null);

    private static final String DELIMITER = "/";

    private final String mDomain;
    private final String mRatingSystem;
    private final String mRating;
    private final String[] mSubRatings;
    private final int mHashCode;

    public static TvContentRating createRating(
            String domain,
            String ratingSystem,
            String rating,
            String... subRatings) {
        if (domain == null || domain.isEmpty()) {
            throw new IllegalArgumentException("domain cannot be empty");
        }
        if (ratingSystem == null || ratingSystem.isEmpty()) {
            throw new IllegalArgumentException("ratingSystem cannot be empty");
        }
        if (rating == null || rating.isEmpty()) {
            throw new IllegalArgumentException("rating cannot be empty");
        }
        return new TvContentRating(domain, ratingSystem, rating, subRatings);
    }

    public static TvContentRating unflattenFromString(String ratingString) {
        if (ratingString == null || ratingString.isEmpty()) {
            throw new IllegalArgumentException("ratingString cannot be empty");
        }
        String[] strs = ratingString.split(DELIMITER, -1);
        if (strs.length < 3) {
            throw new IllegalArgumentException("Invalid rating string: " + ratingString);
        }
        if (strs[0].isEmpty() || strs[1].isEmpty() || strs[2].isEmpty()) {
            throw new IllegalArgumentException("Invalid rating string: " + ratingString);
        }
        if ("null".equals(strs[0]) && "null".equals(strs[1]) && "null".equals(strs[2])
                && strs.length == 3) {
            return UNRATED;
        }
        if (strs.length > 3) {
            String[] subRatings = new String[strs.length - 3];
            System.arraycopy(strs, 3, subRatings, 0, subRatings.length);
            return new TvContentRating(strs[0], strs[1], strs[2], subRatings);
        }
        return new TvContentRating(strs[0], strs[1], strs[2], null);
    }

    private TvContentRating(
            String domain,
            String ratingSystem,
            String rating,
            String[] subRatings) {
        mDomain = domain;
        mRatingSystem = ratingSystem;
        mRating = rating;
        if (subRatings == null || subRatings.length == 0) {
            mSubRatings = null;
        } else {
            List<String> nonEmpty = new ArrayList<String>(subRatings.length);
            for (int i = 0; i < subRatings.length; i++) {
                if (subRatings[i] != null && !subRatings[i].isEmpty()) {
                    nonEmpty.add(subRatings[i]);
                }
            }
            if (nonEmpty.isEmpty()) {
                mSubRatings = null;
            } else {
                mSubRatings = nonEmpty.toArray(new String[0]);
                Arrays.sort(mSubRatings);
            }
        }
        mHashCode = 31 * Objects.hash(mDomain, mRating) + Arrays.hashCode(mSubRatings);
    }

    public String getDomain() {
        return mDomain;
    }

    public String getRatingSystem() {
        return mRatingSystem;
    }

    public String getMainRating() {
        return mRating;
    }

    public List<String> getSubRatings() {
        if (mSubRatings == null) {
            return null;
        }
        return Collections.unmodifiableList(Arrays.asList(mSubRatings));
    }

    public String flattenToString() {
        StringBuilder builder = new StringBuilder();
        builder.append(mDomain);
        builder.append(DELIMITER);
        builder.append(mRatingSystem);
        builder.append(DELIMITER);
        builder.append(mRating);
        if (mSubRatings != null) {
            for (int i = 0; i < mSubRatings.length; i++) {
                builder.append(DELIMITER);
                builder.append(mSubRatings[i]);
            }
        }
        return builder.toString();
    }

    public boolean contains(TvContentRating rating) {
        Objects.requireNonNull(rating, "rating cannot be null");
        if (!rating.getMainRating().equals(mRating)) {
            return false;
        }
        if (!rating.getDomain().equals(mDomain)
                || !rating.getRatingSystem().equals(mRatingSystem)
                || !rating.getMainRating().equals(mRating)) {
            return false;
        }
        List<String> subRatings = getSubRatings();
        List<String> subRatingsOther = rating.getSubRatings();
        if (subRatings == null && subRatingsOther == null) {
            return true;
        } else if (subRatings == null && subRatingsOther != null) {
            return false;
        } else if (subRatings != null && subRatingsOther == null) {
            return true;
        } else {
            return subRatings.containsAll(subRatingsOther);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TvContentRating)) {
            return false;
        }
        TvContentRating other = (TvContentRating) obj;
        if (mHashCode != other.mHashCode) {
            return false;
        }
        if (!Objects.equals(mDomain, other.mDomain)) {
            return false;
        }
        if (!Objects.equals(mRatingSystem, other.mRatingSystem)) {
            return false;
        }
        if (!Objects.equals(mRating, other.mRating)) {
            return false;
        }
        return Arrays.equals(mSubRatings, other.mSubRatings);
    }

    @Override
    public int hashCode() {
        return mHashCode;
    }
}
