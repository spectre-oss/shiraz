// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Service implemented by apps that screen incoming or outgoing calls. */
public abstract class CallScreeningService extends Service {
    public static final String SERVICE_INTERFACE = "android.telecom.CallScreeningService";

    public static class CallResponse {
        public static final int CALL_COMPOSER_ATTACHMENT_PICTURE = 1 << 0;
        public static final int CALL_COMPOSER_ATTACHMENT_LOCATION = 1 << 1;
        public static final int CALL_COMPOSER_ATTACHMENT_SUBJECT = 1 << 2;
        public static final int CALL_COMPOSER_ATTACHMENT_PRIORITY = 1 << 3;

        private static final int ALL_COMPOSER_ATTACHMENTS =
                CALL_COMPOSER_ATTACHMENT_PICTURE
                        | CALL_COMPOSER_ATTACHMENT_LOCATION
                        | CALL_COMPOSER_ATTACHMENT_SUBJECT
                        | CALL_COMPOSER_ATTACHMENT_PRIORITY;

        private final boolean mShouldDisallowCall;
        private final boolean mShouldRejectCall;
        private final boolean mShouldSilenceCall;
        private final boolean mShouldSkipCallLog;
        private final boolean mShouldSkipNotification;
        private final int mCallComposerAttachmentsToShow;

        private CallResponse(
                boolean shouldDisallowCall,
                boolean shouldRejectCall,
                boolean shouldSilenceCall,
                boolean shouldSkipCallLog,
                boolean shouldSkipNotification,
                int callComposerAttachmentsToShow) {
            if (!shouldDisallowCall
                    && (shouldRejectCall || shouldSkipCallLog || shouldSkipNotification)) {
                throw new IllegalStateException(
                        "Invalid response state for allowed call.");
            }
            if (shouldDisallowCall && shouldSilenceCall) {
                throw new IllegalStateException(
                        "Invalid response state for disallowed call.");
            }
            this.mShouldDisallowCall = shouldDisallowCall;
            this.mShouldRejectCall = shouldRejectCall;
            this.mShouldSilenceCall = shouldSilenceCall;
            this.mShouldSkipCallLog = shouldSkipCallLog;
            this.mShouldSkipNotification = shouldSkipNotification;
            this.mCallComposerAttachmentsToShow = callComposerAttachmentsToShow;
        }

        public boolean getDisallowCall() {
            return mShouldDisallowCall;
        }

        public boolean getRejectCall() {
            return mShouldRejectCall;
        }

        public boolean getSilenceCall() {
            return mShouldSilenceCall;
        }

        public boolean getSkipCallLog() {
            return mShouldSkipCallLog;
        }

        public boolean getSkipNotification() {
            return mShouldSkipNotification;
        }

        public int getCallComposerAttachmentsToShow() {
            return mCallComposerAttachmentsToShow;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CallResponse that = (CallResponse) o;
            return mShouldDisallowCall == that.mShouldDisallowCall
                    && mShouldRejectCall == that.mShouldRejectCall
                    && mShouldSilenceCall == that.mShouldSilenceCall
                    && mShouldSkipCallLog == that.mShouldSkipCallLog
                    && mShouldSkipNotification == that.mShouldSkipNotification
                    && mCallComposerAttachmentsToShow == that.mCallComposerAttachmentsToShow;
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    Boolean.valueOf(mShouldDisallowCall),
                    Boolean.valueOf(mShouldRejectCall),
                    Boolean.valueOf(mShouldSilenceCall),
                    Boolean.valueOf(mShouldSkipCallLog),
                    Boolean.valueOf(mShouldSkipNotification),
                    Integer.valueOf(mCallComposerAttachmentsToShow));
        }

        public static class Builder {
            private boolean mShouldDisallowCall;
            private boolean mShouldRejectCall;
            private boolean mShouldSilenceCall;
            private boolean mShouldSkipCallLog;
            private boolean mShouldSkipNotification;
            private int mCallComposerAttachmentsToShow = -1;

            public Builder() {
            }

            public Builder setDisallowCall(boolean shouldDisallowCall) {
                mShouldDisallowCall = shouldDisallowCall;
                return this;
            }

            public Builder setRejectCall(boolean shouldRejectCall) {
                mShouldRejectCall = shouldRejectCall;
                return this;
            }

            public Builder setSilenceCall(boolean shouldSilenceCall) {
                mShouldSilenceCall = shouldSilenceCall;
                return this;
            }

            public Builder setSkipCallLog(boolean shouldSkipCallLog) {
                mShouldSkipCallLog = shouldSkipCallLog;
                return this;
            }

            public Builder setSkipNotification(boolean shouldSkipNotification) {
                mShouldSkipNotification = shouldSkipNotification;
                return this;
            }

            public Builder setCallComposerAttachmentsToShow(int callComposerAttachmentsToShow) {
                if (callComposerAttachmentsToShow < 0
                        || (callComposerAttachmentsToShow & ~ALL_COMPOSER_ATTACHMENTS) != 0) {
                    throw new IllegalArgumentException(
                            "Attachment types must match CallResponse constants");
                }
                mCallComposerAttachmentsToShow = callComposerAttachmentsToShow;
                return this;
            }

            public CallResponse build() {
                return new CallResponse(
                        mShouldDisallowCall,
                        mShouldRejectCall,
                        mShouldSilenceCall,
                        mShouldSkipCallLog,
                        mShouldSkipNotification,
                        mCallComposerAttachmentsToShow);
            }
        }
    }

    private final IBinder mBinder = new Binder();
    private final Map<Call.Details, CallResponse> mResponses =
            new ConcurrentHashMap<Call.Details, CallResponse>();

    public CallScreeningService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }

    public abstract void onScreenCall(Call.Details callDetails);

    public final void respondToCall(Call.Details callDetails, CallResponse response) {
        Objects.requireNonNull(callDetails, "callDetails");
        Objects.requireNonNull(response, "response");
        mResponses.put(callDetails, response);
    }
}
