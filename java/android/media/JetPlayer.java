// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.content.res.AssetFileDescriptor;
import android.os.Handler;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayDeque;

public class JetPlayer {
    public interface OnJetEventListener {
        void onJetEvent(JetPlayer player, short segment, byte track, byte channel, byte controller, byte value);
        void onJetUserIdUpdate(JetPlayer player, int userId, int repeatCount);
        void onJetNumQueuedSegmentUpdate(JetPlayer player, int nbSegments);
        void onJetPauseUpdate(JetPlayer player, int paused);
    }

    private static final int MAXTRACKS = 32;
    private static JetPlayer sSingletonRef;

    private static final class Segment {
        final int segmentNum;
        final int repeatCount;
        final byte userID;

        Segment(int segmentNum, int repeatCount, byte userID) {
            this.segmentNum = segmentNum;
            this.repeatCount = repeatCount;
            this.userID = userID;
        }
    }

    private final ArrayDeque<Segment> mQueue = new ArrayDeque<Segment>();
    private boolean mFileLoaded;
    private boolean mPlaying;
    private int mMuteFlags;
    private OnJetEventListener mEventListener;
    private Handler mEventHandler;

    private JetPlayer() {
    }

    public static synchronized JetPlayer getJetPlayer() {
        if (sSingletonRef == null) {
            sSingletonRef = new JetPlayer();
        }
        return sSingletonRef;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    @Override
    protected void finalize() {
        release();
    }

    public synchronized void release() {
        closeJetFile();
        sSingletonRef = null;
    }

    public static int getMaxTracks() {
        return MAXTRACKS;
    }

    public synchronized boolean loadJetFile(String path) {
        if (path == null) {
            return false;
        }
        File f = new File(path);
        if (!f.isFile() || f.length() < 8) {
            return false;
        }
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] hdr = new byte[4];
            if (in.read(hdr) != 4) {
                return false;
            }
            if (hdr[0] != 'J' || hdr[1] != 'E' || hdr[2] != 'T' || hdr[3] != ' ') {
                return false;
            }
            mFileLoaded = true;
            mQueue.clear();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public synchronized boolean loadJetFile(AssetFileDescriptor afd) {
        if (afd == null) {
            return false;
        }
        try (FileInputStream in = afd.createInputStream()) {
            byte[] hdr = new byte[4];
            if (in.read(hdr) != 4) {
                return false;
            }
            if (hdr[0] != 'J' || hdr[1] != 'E' || hdr[2] != 'T' || hdr[3] != ' ') {
                return false;
            }
            mFileLoaded = true;
            mQueue.clear();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public synchronized boolean closeJetFile() {
        if (!mFileLoaded) {
            return false;
        }
        mFileLoaded = false;
        mPlaying = false;
        mQueue.clear();
        return true;
    }

    public synchronized boolean play() {
        if (!mFileLoaded) {
            return false;
        }
        mPlaying = true;
        dispatchPauseUpdate(0);
        return true;
    }

    public synchronized boolean pause() {
        if (!mFileLoaded) {
            return false;
        }
        mPlaying = false;
        dispatchPauseUpdate(1);
        return true;
    }

    public synchronized boolean queueJetSegment(int segmentNum, int libNum, int repeatCount,
            int transpose, int muteFlags, byte userID) {
        if (!mFileLoaded || segmentNum < 0 || repeatCount < -1 || transpose < -12 || transpose > 12) {
            return false;
        }
        mMuteFlags = muteFlags;
        mQueue.addLast(new Segment(segmentNum, repeatCount, userID));
        dispatchQueueUpdate(mQueue.size());
        return true;
    }

    public synchronized boolean queueJetSegmentMuteArray(int segmentNum, int libNum, int repeatCount,
            int transpose, boolean[] muteArray, byte userID) {
        if (muteArray == null || muteArray.length != MAXTRACKS) {
            return false;
        }
        int mask = 0;
        for (int i = 0; i < MAXTRACKS; i++) {
            if (muteArray[i]) {
                mask |= (1 << i);
            }
        }
        return queueJetSegment(segmentNum, libNum, repeatCount, transpose, mask, userID);
    }

    public synchronized boolean setMuteFlags(int muteFlags, boolean sync) {
        if (!mFileLoaded) {
            return false;
        }
        mMuteFlags = muteFlags;
        return true;
    }

    public synchronized boolean setMuteArray(boolean[] muteArray, boolean sync) {
        if (!mFileLoaded || muteArray == null || muteArray.length != MAXTRACKS) {
            return false;
        }
        int mask = 0;
        for (int i = 0; i < MAXTRACKS; i++) {
            if (muteArray[i]) {
                mask |= (1 << i);
            }
        }
        mMuteFlags = mask;
        return true;
    }

    public synchronized boolean setMuteFlag(int trackId, boolean muteFlag, boolean sync) {
        if (!mFileLoaded || trackId < 0 || trackId >= MAXTRACKS) {
            return false;
        }
        if (muteFlag) {
            mMuteFlags |= (1 << trackId);
        } else {
            mMuteFlags &= ~(1 << trackId);
        }
        return true;
    }

    public synchronized boolean triggerClip(int clipId) {
        return mFileLoaded && clipId >= 0 && clipId < 64;
    }

    public synchronized boolean clearQueue() {
        if (!mFileLoaded) {
            return false;
        }
        mQueue.clear();
        dispatchQueueUpdate(0);
        return true;
    }

    public synchronized void setEventListener(OnJetEventListener listener) {
        setEventListener(listener, null);
    }

    public synchronized void setEventListener(OnJetEventListener listener, Handler handler) {
        mEventListener = listener;
        mEventHandler = handler;
    }

    private void dispatchQueueUpdate(final int count) {
        final OnJetEventListener l = mEventListener;
        final Handler h = mEventHandler;
        if (l == null) {
            return;
        }
        if (h != null) {
            h.post(new Runnable() {
                @Override
                public void run() {
                    l.onJetNumQueuedSegmentUpdate(JetPlayer.this, count);
                }
            });
        } else {
            l.onJetNumQueuedSegmentUpdate(this, count);
        }
    }

    private void dispatchPauseUpdate(final int paused) {
        final OnJetEventListener l = mEventListener;
        final Handler h = mEventHandler;
        if (l == null) {
            return;
        }
        if (h != null) {
            h.post(new Runnable() {
                @Override
                public void run() {
                    l.onJetPauseUpdate(JetPlayer.this, paused);
                }
            });
        } else {
            l.onJetPauseUpdate(this, paused);
        }
    }
}
