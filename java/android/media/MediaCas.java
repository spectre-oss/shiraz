// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.content.Context;
import android.os.Handler;
import java.util.Arrays;

public final class MediaCas implements AutoCloseable {
    public static final int PLUGIN_STATUS_PHYSICAL_MODULE_CHANGED = 0;
    public static final int PLUGIN_STATUS_SESSION_NUMBER_CHANGED = 1;

    public static final int SCRAMBLING_MODE_RESERVED = 0;
    public static final int SCRAMBLING_MODE_DVB_CSA1 = 1;
    public static final int SCRAMBLING_MODE_DVB_CSA2 = 2;
    public static final int SCRAMBLING_MODE_DVB_CSA3_STANDARD = 3;
    public static final int SCRAMBLING_MODE_DVB_CSA3_MINIMAL = 4;
    public static final int SCRAMBLING_MODE_DVB_CSA3_ENHANCE = 5;
    public static final int SCRAMBLING_MODE_DVB_CISSA_V1 = 6;
    public static final int SCRAMBLING_MODE_DVB_IDSA = 7;
    public static final int SCRAMBLING_MODE_MULTI2 = 8;
    public static final int SCRAMBLING_MODE_AES128 = 9;
    public static final int SCRAMBLING_MODE_AES_ECB = 10;
    public static final int SCRAMBLING_MODE_AES_SCTE52 = 11;
    public static final int SCRAMBLING_MODE_TDES_ECB = 12;
    public static final int SCRAMBLING_MODE_TDES_SCTE52 = 13;
    public static final int SCRAMBLING_MODE_AES_CBC = 14;

    public static final int SESSION_USAGE_LIVE = 0;
    public static final int SESSION_USAGE_PLAYBACK = 1;
    public static final int SESSION_USAGE_RECORD = 2;
    public static final int SESSION_USAGE_TIMESHIFT = 3;

    public interface EventListener {
        void onEvent(MediaCas mediaCas, int event, int arg, byte[] data);

        default void onSessionEvent(MediaCas mediaCas, Session session, int event, int arg, byte[] data) {
        }

        default void onPluginStatusUpdate(MediaCas mediaCas, int status, int arg) {
        }

        default void onResourceLost(MediaCas mediaCas) {
        }
    }

    public static class PluginDescriptor {
        private final int mCASystemId;
        private final String mName;

        PluginDescriptor(int caSystemId, String name) {
            mCASystemId = caSystemId;
            mName = name;
        }

        public int getSystemId() {
            return mCASystemId;
        }

        public String getName() {
            return mName;
        }

        @Override
        public String toString() {
            return "PluginDescriptor{" + mCASystemId + ", " + mName + "}";
        }
    }

    public final class Session implements AutoCloseable {
        private final byte[] mSessionId;

        Session(byte[] sessionId) {
            mSessionId = sessionId != null ? sessionId.clone() : new byte[0];
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Session) {
                return Arrays.equals(mSessionId, ((Session) obj).mSessionId);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(mSessionId);
        }

        public void setPrivateData(byte[] data) throws MediaCasException {
            throw new MediaCasException.UnsupportedCasException("No hardware CAS session active");
        }

        public void processEcm(byte[] data, int offset, int length) throws MediaCasException {
            throw new MediaCasException.UnsupportedCasException("No hardware CAS session active");
        }

        public void processEcm(byte[] data) throws MediaCasException {
            processEcm(data, 0, data != null ? data.length : 0);
        }

        public void sendSessionEvent(int event, int arg, byte[] data) throws MediaCasException {
            throw new MediaCasException.UnsupportedCasException("No hardware CAS session active");
        }

        public byte[] getSessionId() {
            return mSessionId.clone();
        }

        @Override
        public void close() {
        }
    }

    public MediaCas(int CA_system_id) throws MediaCasException.UnsupportedCasException {
        throw new MediaCasException.UnsupportedCasException("Unsupported CA_system_id " + CA_system_id);
    }

    public MediaCas(Context context, int CA_system_id, String tvInputServiceSessionId, int priorityHint)
            throws MediaCasException.UnsupportedCasException {
        throw new MediaCasException.UnsupportedCasException("Unsupported CA_system_id " + CA_system_id);
    }

    public MediaCas(Context context, int CA_system_id, String tvInputServiceSessionId, int priorityHint,
            Handler handler, EventListener listener) throws MediaCasException.UnsupportedCasException {
        throw new MediaCasException.UnsupportedCasException("Unsupported CA_system_id " + CA_system_id);
    }

    public static boolean isSystemIdSupported(int CA_system_id) {
        return false;
    }

    public static PluginDescriptor[] enumeratePlugins() {
        return new PluginDescriptor[0];
    }

    public void setEventListener(EventListener listener, Handler handler) {
    }

    public void setPrivateData(byte[] data) throws MediaCasException {
        throw new MediaCasException.UnsupportedCasException("MediaCas unavailable on desktop host");
    }

    public Session openSession() throws MediaCasException {
        throw new MediaCasException.UnsupportedCasException("MediaCas unavailable on desktop host");
    }

    public Session openSession(int sessionUsage, int scramblingMode) throws MediaCasException {
        throw new MediaCasException.UnsupportedCasException("MediaCas unavailable on desktop host");
    }

    public void processEmm(byte[] data, int offset, int length) throws MediaCasException {
        throw new MediaCasException.UnsupportedCasException("MediaCas unavailable on desktop host");
    }

    public void processEmm(byte[] data) throws MediaCasException {
        processEmm(data, 0, data != null ? data.length : 0);
    }

    public void sendEvent(int event, int arg, byte[] data) throws MediaCasException {
        throw new MediaCasException.UnsupportedCasException("MediaCas unavailable on desktop host");
    }

    public void provision(String provisionString) throws MediaCasException {
        throw new MediaCasException.UnsupportedCasException("MediaCas unavailable on desktop host");
    }

    public void refreshEntitlements(int refreshType, byte[] refreshData) throws MediaCasException {
        throw new MediaCasException.UnsupportedCasException("MediaCas unavailable on desktop host");
    }

    @Override
    public void close() {
    }

    @Override
    protected void finalize() {
        close();
    }
}
