// SPDX-License-Identifier: GPL-3.0-only
package android.inputmethodservice;

import android.app.Service;
import android.content.ComponentCallbacks;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethod;
import android.view.inputmethod.InputMethodSession;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractInputMethodService extends Service implements KeyEvent.Callback {
    private InputMethod mInputMethod;
    private final KeyEvent.DispatcherState mDispatcherState = new KeyEvent.DispatcherState();
    private final List<ComponentCallbacks> mCallbacks = new ArrayList<>();

    public abstract class AbstractInputMethodImpl implements InputMethod {
        public AbstractInputMethodImpl() {
        }

        @Override
        public void createSession(SessionCallback callback) {
            InputMethodSession session = onCreateInputMethodSessionInterface();
            if (callback != null) {
                callback.sessionCreated(session);
            }
        }

        @Override
        public void setSessionEnabled(InputMethodSession session, boolean enabled) {
            if (session instanceof AbstractInputMethodSessionImpl) {
                ((AbstractInputMethodSessionImpl) session).setEnabled(enabled);
            }
        }

        @Override
        public void revokeSession(InputMethodSession session) {
            if (session instanceof AbstractInputMethodSessionImpl) {
                ((AbstractInputMethodSessionImpl) session).revokeSelf();
            }
        }
    }

    public abstract class AbstractInputMethodSessionImpl implements InputMethodSession {
        private boolean mEnabled = true;
        private boolean mRevoked;

        public AbstractInputMethodSessionImpl() {
        }

        public boolean isEnabled() {
            return mEnabled;
        }

        public boolean isRevoked() {
            return mRevoked;
        }

        public void setEnabled(boolean enabled) {
            if (!mRevoked) {
                mEnabled = enabled;
            }
        }

        public void revokeSelf() {
            mRevoked = true;
            mEnabled = false;
        }

        @Override
        public void dispatchKeyEvent(int seq, KeyEvent event, EventCallback callback) {
            boolean handled = false;
            if (mEnabled && event != null) {
                handled = event.dispatch(AbstractInputMethodService.this, mDispatcherState, this);
            }
            if (callback != null) {
                callback.finishedEvent(seq, handled);
            }
        }

        @Override
        public void dispatchTrackballEvent(int seq, MotionEvent event, EventCallback callback) {
            boolean handled = mEnabled && event != null && onTrackballEvent(event);
            if (callback != null) {
                callback.finishedEvent(seq, handled);
            }
        }

        @Override
        public void dispatchGenericMotionEvent(int seq, MotionEvent event, EventCallback callback) {
            boolean handled = mEnabled && event != null && onGenericMotionEvent(event);
            if (callback != null) {
                callback.finishedEvent(seq, handled);
            }
        }
    }

    public AbstractInputMethodService() {
    }

    public KeyEvent.DispatcherState getKeyDispatcherState() {
        return mDispatcherState;
    }

    public abstract AbstractInputMethodImpl onCreateInputMethodInterface();

    public abstract AbstractInputMethodSessionImpl onCreateInputMethodSessionInterface();

    protected void dump(FileDescriptor fd, PrintWriter fout, String[] args) {
    }

    @Override
    public final IBinder onBind(Intent intent) {
        if (mInputMethod == null) {
            mInputMethod = onCreateInputMethodInterface();
        }
        return new InputMethodBinder(mInputMethod);
    }

    public boolean onTrackballEvent(MotionEvent event) {
        return false;
    }

    public boolean onGenericMotionEvent(MotionEvent event) {
        return false;
    }

    @Override
    public Object getSystemService(String name) {
        return super.getSystemService(name);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        synchronized (mCallbacks) {
            mCallbacks.clear();
        }
    }

    @Override
    public void registerComponentCallbacks(ComponentCallbacks callback) {
        if (callback != null) {
            synchronized (mCallbacks) {
                if (!mCallbacks.contains(callback)) {
                    mCallbacks.add(callback);
                }
            }
        }
    }

    @Override
    public void unregisterComponentCallbacks(ComponentCallbacks callback) {
        if (callback != null) {
            synchronized (mCallbacks) {
                mCallbacks.remove(callback);
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        ComponentCallbacks[] snapshot;
        synchronized (mCallbacks) {
            snapshot = mCallbacks.toArray(new ComponentCallbacks[0]);
        }
        for (int i = 0; i < snapshot.length; i++) {
            snapshot[i].onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        ComponentCallbacks[] snapshot;
        synchronized (mCallbacks) {
            snapshot = mCallbacks.toArray(new ComponentCallbacks[0]);
        }
        for (int i = 0; i < snapshot.length; i++) {
            snapshot[i].onLowMemory();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
    }

    private static final class InputMethodBinder extends Binder {
        final InputMethod inputMethod;

        InputMethodBinder(InputMethod inputMethod) {
            this.inputMethod = inputMethod;
        }
    }
}
