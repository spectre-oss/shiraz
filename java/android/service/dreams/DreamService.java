// SPDX-License-Identifier: GPL-3.0-only
package android.service.dreams;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/** Extend this class to implement a custom dream (interactive screensaver). */
public class DreamService extends Service implements Window.Callback {
    public static final String DREAM_META_DATA = "android.service.dream";
    public static final String SERVICE_INTERFACE = "android.service.dreams.DreamService";

    private Window mWindow;
    private boolean mInteractive;
    private boolean mFullscreen;
    private boolean mScreenBright = true;
    private boolean mFinished;
    private final IBinder mBinder = new Binder();

    public DreamService() {
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!mInteractive) {
            if (event != null && event.getAction() == KeyEvent.ACTION_UP) {
                wakeUp();
            }
            return true;
        }
        if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_UP) {
            wakeUp();
            return true;
        }
        return false;
    }

    public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        if (!mInteractive) {
            wakeUp();
            return true;
        }
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!mInteractive) {
            if (event != null && event.getActionMasked() == MotionEvent.ACTION_UP) {
                wakeUp();
            }
            return true;
        }
        return mWindow != null && mWindow.getDecorView() != null
                && mWindow.getDecorView().dispatchTouchEvent(event);
    }

    public boolean dispatchTrackballEvent(MotionEvent event) {
        if (!mInteractive) {
            wakeUp();
            return true;
        }
        return false;
    }

    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return false;
    }

    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        return false;
    }

    @Override
    public View onCreatePanelView(int featureId) {
        return null;
    }

    @Override
    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        return false;
    }

    @Override
    public boolean onPreparePanel(int featureId, View view, Menu menu) {
        return false;
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        return false;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        return false;
    }

    public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) {
    }

    @Override
    public void onContentChanged() {
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
    }

    public void onAttachedToWindow() {
    }

    public void onDetachedFromWindow() {
    }

    @Override
    public void onPanelClosed(int featureId, Menu menu) {
    }

    public boolean onSearchRequested(SearchEvent event) {
        return onSearchRequested();
    }

    public boolean onSearchRequested() {
        return false;
    }

    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
        return null;
    }

    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type) {
        return null;
    }

    public void onActionModeStarted(ActionMode mode) {
    }

    public void onActionModeFinished(ActionMode mode) {
    }

    public WindowManager getWindowManager() {
        return mWindow != null ? mWindow.getWindowManager() : null;
    }

    public Window getWindow() {
        if (mWindow == null && getBaseContext() != null) {
            mWindow = new Window(this);
            mWindow.setCallback(this);
        }
        return mWindow;
    }

    public void setContentView(int layoutResID) {
        Window win = getWindow();
        if (win != null) {
            win.setContentView(layoutResID);
            onContentChanged();
        }
    }

    public void setContentView(View view) {
        Window win = getWindow();
        if (win != null) {
            win.setContentView(view);
            onContentChanged();
        }
    }

    public void setContentView(View view, ViewGroup.LayoutParams params) {
        Window win = getWindow();
        if (win != null) {
            win.setContentView(view, params);
            onContentChanged();
        }
    }

    public void addContentView(View view, ViewGroup.LayoutParams params) {
        Window win = getWindow();
        if (win != null) {
            win.addContentView(view, params);
            onContentChanged();
        }
    }

    public <T extends View> T findViewById(int id) {
        Window win = getWindow();
        return win != null ? win.<T>findViewById(id) : null;
    }

    public final <T extends View> T requireViewById(int id) {
        T view = findViewById(id);
        if (view == null) {
            throw new IllegalArgumentException("ID does not reference a View inside this DreamService");
        }
        return view;
    }

    public void setInteractive(boolean interactive) {
        mInteractive = interactive;
    }

    public boolean isInteractive() {
        return mInteractive;
    }

    public void setFullscreen(boolean fullscreen) {
        mFullscreen = fullscreen;
    }

    public boolean isFullscreen() {
        return mFullscreen;
    }

    public void setScreenBright(boolean screenBright) {
        mScreenBright = screenBright;
    }

    public boolean isScreenBright() {
        return mScreenBright;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public void onDreamingStarted() {
    }

    public void onDreamingStopped() {
    }

    public void onWakeUp() {
        finish();
    }

    @Override
    public final IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    public final void finish() {
        if (!mFinished) {
            mFinished = true;
            stopSelf();
        }
    }

    public final void wakeUp() {
        onWakeUp();
    }

    @Override
    public void onDestroy() {
        if (mWindow != null) {
            mWindow.shirazDestroy();
            mWindow = null;
        }
        super.onDestroy();
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (pw != null) {
            pw.println("DreamService: interactive=" + mInteractive
                    + " fullscreen=" + mFullscreen + " screenBright=" + mScreenBright);
        }
    }
}
