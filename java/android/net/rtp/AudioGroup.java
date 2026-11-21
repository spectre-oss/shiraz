// SPDX-License-Identifier: GPL-3.0-only
package android.net.rtp;

import android.content.Context;
import java.util.LinkedHashSet;
import java.util.Set;

public class AudioGroup {
    public static final int MODE_ON_HOLD = 0;
    public static final int MODE_MUTED = 1;
    public static final int MODE_NORMAL = 2;
    public static final int MODE_ECHO_SUPPRESSION = 3;

    private final Set<AudioStream> mStreams = new LinkedHashSet<AudioStream>();
    private int mMode = MODE_ON_HOLD;

    @Deprecated
    public AudioGroup() {}

    public AudioGroup(Context context) {}

    public synchronized AudioStream[] getStreams() {
        return mStreams.toArray(new AudioStream[0]);
    }

    public synchronized int getMode() {
        return mMode;
    }

    public synchronized void setMode(int mode) {
        if (mode < MODE_ON_HOLD || mode > MODE_ECHO_SUPPRESSION) {
            throw new IllegalArgumentException("Invalid mode");
        }
        this.mMode = mode;
    }

    public synchronized void sendDtmf(int event) {
        if (event < 0 || event > 15) {
            throw new IllegalArgumentException("Invalid event");
        }
    }

    public synchronized void clear() {
        for (AudioStream stream : getStreams()) {
            stream.join(null);
        }
        mStreams.clear();
    }

    synchronized void add(AudioStream stream) {
        mStreams.add(stream);
    }

    synchronized void remove(AudioStream stream) {
        mStreams.remove(stream);
    }

    @Override
    protected void finalize() throws Throwable {
        clear();
        super.finalize();
    }
}
