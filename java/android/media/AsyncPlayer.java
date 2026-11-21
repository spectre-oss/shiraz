// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import java.util.LinkedList;

public class AsyncPlayer {
    private static final int PLAY = 1;
    private static final int STOP = 2;

    private static final class Command {
        int code;
        Context context;
        Uri uri;
        boolean looping;
        AudioAttributes attributes;
    }

    private final LinkedList<Command> mCmdQueue = new LinkedList<Command>();
    private final String mTag;
    private Thread mThread;
    private MediaPlayer mPlayer;
    private int mState = STOP;

    public AsyncPlayer(String tag) {
        mTag = (tag != null) ? tag : "AsyncPlayer";
    }

    public void play(Context context, Uri uri, boolean looping, int stream) {
        if (context == null || uri == null) {
            return;
        }
        try {
            play(context, uri, looping,
                    new AudioAttributes.Builder().setLegacyStreamType(stream).build());
        } catch (IllegalArgumentException e) {
            Log.w(mTag, "Call to play() failed", e);
        }
    }

    public void play(Context context, Uri uri, boolean looping, AudioAttributes attributes)
            throws IllegalArgumentException {
        if (context == null || uri == null || attributes == null) {
            throw new IllegalArgumentException("Illegal null AsyncPlayer.play() argument");
        }
        Command cmd = new Command();
        cmd.code = PLAY;
        cmd.context = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        cmd.uri = uri;
        cmd.looping = looping;
        cmd.attributes = attributes;
        synchronized (mCmdQueue) {
            enqueueLocked(cmd);
            mState = PLAY;
        }
    }

    public void stop() {
        synchronized (mCmdQueue) {
            if (mState != STOP) {
                Command cmd = new Command();
                cmd.code = STOP;
                enqueueLocked(cmd);
                mState = STOP;
            }
        }
    }

    private void enqueueLocked(Command cmd) {
        mCmdQueue.add(cmd);
        if (mThread == null) {
            mThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    processQueue();
                }
            }, "AsyncPlayer-" + mTag);
            mThread.setDaemon(true);
            mThread.start();
        }
    }

    private void processQueue() {
        while (true) {
            Command cmd;
            synchronized (mCmdQueue) {
                cmd = mCmdQueue.removeFirst();
            }
            switch (cmd.code) {
                case PLAY:
                    startSound(cmd);
                    break;
                case STOP:
                    if (mPlayer != null) {
                        try {
                            mPlayer.stop();
                        } catch (RuntimeException ignored) {
                        }
                        mPlayer.release();
                        mPlayer = null;
                    }
                    break;
                default:
                    break;
            }
            synchronized (mCmdQueue) {
                if (mCmdQueue.isEmpty()) {
                    mThread = null;
                    return;
                }
            }
        }
    }

    private void startSound(Command cmd) {
        try {
            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(cmd.attributes);
            player.setDataSource(cmd.context, cmd.uri);
            player.setLooping(cmd.looping);
            player.prepare();
            player.start();
            if (mPlayer != null) {
                mPlayer.release();
            }
            mPlayer = player;
        } catch (Exception e) {
            Log.w(mTag, "error loading sound for " + cmd.uri, e);
        }
    }
}
