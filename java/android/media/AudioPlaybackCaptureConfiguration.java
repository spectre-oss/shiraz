// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.media.projection.MediaProjection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AudioPlaybackCaptureConfiguration {
    private final MediaProjection mProjection;
    private final int[] mMatchingUsages;
    private final int[] mMatchingUids;
    private final int[] mExcludeUsages;
    private final int[] mExcludeUids;

    AudioPlaybackCaptureConfiguration(
            MediaProjection projection,
            int[] matchingUsages,
            int[] matchingUids,
            int[] excludeUsages,
            int[] excludeUids) {
        mProjection = projection;
        mMatchingUsages = matchingUsages;
        mMatchingUids = matchingUids;
        mExcludeUsages = excludeUsages;
        mExcludeUids = excludeUids;
    }

    public MediaProjection getMediaProjection() {
        return mProjection;
    }

    public int[] getMatchingUsages() {
        return mMatchingUsages.clone();
    }

    public int[] getMatchingUids() {
        return mMatchingUids.clone();
    }

    public int[] getExcludeUsages() {
        return mExcludeUsages.clone();
    }

    public int[] getExcludeUids() {
        return mExcludeUids.clone();
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            out[i] = values.get(i).intValue();
        }
        return out;
    }

    public static final class Builder {
        private final MediaProjection mProjection;
        private final List<Integer> mMatchingUsages = new ArrayList<Integer>();
        private final List<Integer> mMatchingUids = new ArrayList<Integer>();
        private final List<Integer> mExcludeUsages = new ArrayList<Integer>();
        private final List<Integer> mExcludeUids = new ArrayList<Integer>();

        public Builder(MediaProjection projection) {
            mProjection = Objects.requireNonNull(projection, "projection");
        }

        public Builder addMatchingUsage(int usage) {
            if (!mExcludeUsages.isEmpty()) {
                throw new IllegalStateException("Cannot combine matching and excluded usages");
            }
            mMatchingUsages.add(Integer.valueOf(usage));
            return this;
        }

        public Builder addMatchingUid(int uid) {
            if (!mExcludeUids.isEmpty()) {
                throw new IllegalStateException("Cannot combine matching and excluded UIDs");
            }
            mMatchingUids.add(Integer.valueOf(uid));
            return this;
        }

        public Builder excludeUsage(int usage) {
            if (!mMatchingUsages.isEmpty()) {
                throw new IllegalStateException("Cannot combine matching and excluded usages");
            }
            mExcludeUsages.add(Integer.valueOf(usage));
            return this;
        }

        public Builder excludeUid(int uid) {
            if (!mMatchingUids.isEmpty()) {
                throw new IllegalStateException("Cannot combine matching and excluded UIDs");
            }
            mExcludeUids.add(Integer.valueOf(uid));
            return this;
        }

        public AudioPlaybackCaptureConfiguration build() {
            if (mMatchingUsages.isEmpty()
                    && mMatchingUids.isEmpty()
                    && mExcludeUsages.isEmpty()
                    && mExcludeUids.isEmpty()) {
                throw new IllegalStateException("Must specify at least one usage or UID rule");
            }
            return new AudioPlaybackCaptureConfiguration(
                    mProjection,
                    toIntArray(mMatchingUsages),
                    toIntArray(mMatchingUids),
                    toIntArray(mExcludeUsages),
                    toIntArray(mExcludeUids));
        }
    }
}
