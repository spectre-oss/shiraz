// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.topics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Response containing plaintext and encrypted topics from {@link TopicsManager#getTopics}. */
public final class GetTopicsResponse {
    private final List<Topic> mTopics;
    private final List<EncryptedTopic> mEncryptedTopics;

    private GetTopicsResponse(
            List<Topic> topics, List<EncryptedTopic> encryptedTopics) {
        Objects.requireNonNull(topics, "topics");
        Objects.requireNonNull(encryptedTopics, "encryptedTopics");
        this.mTopics = Collections.unmodifiableList(new ArrayList<Topic>(topics));
        this.mEncryptedTopics =
                Collections.unmodifiableList(new ArrayList<EncryptedTopic>(encryptedTopics));
    }

    public List<Topic> getTopics() {
        return mTopics;
    }

    public List<EncryptedTopic> getEncryptedTopics() {
        return mEncryptedTopics;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GetTopicsResponse)) {
            return false;
        }
        GetTopicsResponse that = (GetTopicsResponse) o;
        return Objects.equals(mTopics, that.mTopics)
                && Objects.equals(mEncryptedTopics, that.mEncryptedTopics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTopics, mEncryptedTopics);
    }

    public static final class Builder {
        private final List<Topic> mTopics;
        private final List<EncryptedTopic> mEncryptedTopics;

        @Deprecated
        public Builder(List<Topic> topics) {
            this(topics, Collections.<EncryptedTopic>emptyList());
        }

        public Builder(List<Topic> topics, List<EncryptedTopic> encryptedTopics) {
            Objects.requireNonNull(topics, "topics");
            Objects.requireNonNull(encryptedTopics, "encryptedTopics");
            for (Topic t : topics) {
                Objects.requireNonNull(t, "topic");
            }
            for (EncryptedTopic et : encryptedTopics) {
                Objects.requireNonNull(et, "encryptedTopic");
            }
            this.mTopics = new ArrayList<Topic>(topics);
            this.mEncryptedTopics = new ArrayList<EncryptedTopic>(encryptedTopics);
        }

        public GetTopicsResponse build() {
            return new GetTopicsResponse(mTopics, mEncryptedTopics);
        }
    }
}
