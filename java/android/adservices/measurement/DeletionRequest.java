// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.measurement;

import android.net.Uri;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Parameters for deleting Attribution Reporting measurement registrations. */
public class DeletionRequest {
    public static final int DELETION_MODE_ALL = 0;
    public static final int DELETION_MODE_EXCLUDE_INTERNAL_DATA = 1;

    public static final int MATCH_BEHAVIOR_DELETE = 0;
    public static final int MATCH_BEHAVIOR_PRESERVE = 1;

    private final int mDeletionMode;
    private final int mMatchBehavior;
    private final Instant mStart;
    private final Instant mEnd;
    private final List<Uri> mDomainUris;
    private final List<Uri> mOriginUris;

    private DeletionRequest(
            int deletionMode,
            int matchBehavior,
            Instant start,
            Instant end,
            List<Uri> domainUris,
            List<Uri> originUris) {
        if (deletionMode != DELETION_MODE_ALL
                && deletionMode != DELETION_MODE_EXCLUDE_INTERNAL_DATA) {
            throw new IllegalArgumentException("Invalid deletionMode: " + deletionMode);
        }
        if (matchBehavior != MATCH_BEHAVIOR_DELETE
                && matchBehavior != MATCH_BEHAVIOR_PRESERVE) {
            throw new IllegalArgumentException("Invalid matchBehavior: " + matchBehavior);
        }
        Instant resolvedStart = start == null ? Instant.MIN : start;
        Instant resolvedEnd = end == null ? Instant.MAX : end;
        if (resolvedStart.isAfter(resolvedEnd)) {
            throw new IllegalArgumentException("start must not be after end");
        }
        this.mDeletionMode = deletionMode;
        this.mMatchBehavior = matchBehavior;
        this.mStart = resolvedStart;
        this.mEnd = resolvedEnd;
        this.mDomainUris = copyUris(domainUris);
        this.mOriginUris = copyUris(originUris);
    }

    private static List<Uri> copyUris(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            return Collections.emptyList();
        }
        List<Uri> copy = new ArrayList<Uri>(uris.size());
        for (Uri uri : uris) {
            copy.add(Objects.requireNonNull(uri, "uri"));
        }
        return Collections.unmodifiableList(copy);
    }

    public List<Uri> getOriginUris() {
        return mOriginUris;
    }

    public List<Uri> getDomainUris() {
        return mDomainUris;
    }

    public int getDeletionMode() {
        return mDeletionMode;
    }

    public int getMatchBehavior() {
        return mMatchBehavior;
    }

    public Instant getStart() {
        return mStart;
    }

    public Instant getEnd() {
        return mEnd;
    }

    public static final class Builder {
        private int mDeletionMode = DELETION_MODE_ALL;
        private int mMatchBehavior = MATCH_BEHAVIOR_DELETE;
        private Instant mStart = Instant.MIN;
        private Instant mEnd = Instant.MAX;
        private List<Uri> mDomainUris = Collections.emptyList();
        private List<Uri> mOriginUris = Collections.emptyList();

        public Builder() {
        }

        public Builder setOriginUris(List<Uri> originUris) {
            this.mOriginUris =
                    originUris == null
                            ? Collections.<Uri>emptyList()
                            : new ArrayList<Uri>(originUris);
            return this;
        }

        public Builder setDomainUris(List<Uri> domainUris) {
            this.mDomainUris =
                    domainUris == null
                            ? Collections.<Uri>emptyList()
                            : new ArrayList<Uri>(domainUris);
            return this;
        }

        public Builder setMatchBehavior(int matchBehavior) {
            this.mMatchBehavior = matchBehavior;
            return this;
        }

        public Builder setDeletionMode(int deletionMode) {
            this.mDeletionMode = deletionMode;
            return this;
        }

        public Builder setStart(Instant start) {
            this.mStart = Objects.requireNonNull(start, "start");
            return this;
        }

        public Builder setEnd(Instant end) {
            this.mEnd = Objects.requireNonNull(end, "end");
            return this;
        }

        public DeletionRequest build() {
            return new DeletionRequest(
                    mDeletionMode,
                    mMatchBehavior,
                    mStart,
                    mEnd,
                    mDomainUris,
                    mOriginUris);
        }
    }
}
