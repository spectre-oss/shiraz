// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.topics;

import java.util.Objects;

/** Represents a taxonomy topic returned by the Topics API. */
public final class Topic {
    private final long mTaxonomyVersion;
    private final long mModelVersion;
    private final int mTopicId;

    public Topic(long mTaxonomyVersion, long mModelVersion, int mTopicId) {
        this.mTaxonomyVersion = mTaxonomyVersion;
        this.mModelVersion = mModelVersion;
        this.mTopicId = mTopicId;
    }

    public long getModelVersion() {
        return mModelVersion;
    }

    public long getTaxonomyVersion() {
        return mTaxonomyVersion;
    }

    public int getTopicId() {
        return mTopicId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Topic)) {
            return false;
        }
        Topic topic = (Topic) object;
        return mTaxonomyVersion == topic.mTaxonomyVersion
                && mModelVersion == topic.mModelVersion
                && mTopicId == topic.mTopicId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Long.valueOf(mTaxonomyVersion),
                Long.valueOf(mModelVersion),
                Integer.valueOf(mTopicId));
    }

    @Override
    public String toString() {
        return "Topic{mTaxonomyVersion="
                + mTaxonomyVersion
                + ", mModelVersion="
                + mModelVersion
                + ", mTopicCode="
                + mTopicId
                + "}";
    }
}
