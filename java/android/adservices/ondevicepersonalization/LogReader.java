// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Reader for historical request and event logs inside an {@link IsolatedService}. */
public class LogReader {
    LogReader() {
    }

    public List<RequestLogRecord> getRequests(Instant startTime, Instant endTime) {
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        return Collections.emptyList();
    }

    public List<EventLogRecord> getJoinedEvents(Instant startTime, Instant endTime) {
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        return Collections.emptyList();
    }
}
