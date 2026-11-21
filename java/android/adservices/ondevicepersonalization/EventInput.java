// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import android.os.PersistableBundle;
import java.util.Objects;

/** Input passed to {@link IsolatedWorker#onEvent}. */
public final class EventInput {
    private final RequestLogRecord mRequestLogRecord;
    private final PersistableBundle mParameters;

    EventInput(RequestLogRecord requestLogRecord, PersistableBundle parameters) {
        this.mRequestLogRecord = requestLogRecord;
        this.mParameters = parameters == null ? PersistableBundle.EMPTY : parameters;
    }

    public RequestLogRecord getRequestLogRecord() {
        return mRequestLogRecord;
    }

    public PersistableBundle getParameters() {
        return mParameters;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EventInput)) {
            return false;
        }
        EventInput that = (EventInput) o;
        return Objects.equals(mRequestLogRecord, that.mRequestLogRecord)
                && Objects.equals(mParameters, that.mParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRequestLogRecord, mParameters);
    }
}
