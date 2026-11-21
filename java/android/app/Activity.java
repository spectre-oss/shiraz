// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.View;

/** First lifecycle/content subset. Window management and the broader styled-widget framework remain incomplete. */
public class Activity extends ContextThemeWrapper
        implements android.view.LayoutInflater.Factory2,
                android.view.Window.Callback,
                android.view.KeyEvent.Callback,
                android.view.View.OnCreateContextMenuListener,
                android.content.ComponentCallbacks2 {

    public static final int DEFAULT_KEYS_DISABLE = 0;
    public static final int DEFAULT_KEYS_DIALER = 1;
    public static final int DEFAULT_KEYS_SHORTCUT = 2;
    public static final int DEFAULT_KEYS_SEARCH_LOCAL = 3;
    public static final int DEFAULT_KEYS_SEARCH_GLOBAL = 4;

    protected static final int[] FOCUSED_STATE_SET = new int[] {0x0101009c};

    public static final int FULLSCREEN_MODE_REQUEST_EXIT = 0;
    public static final int FULLSCREEN_MODE_REQUEST_ENTER = 1;

    public static final int OVERRIDE_TRANSITION_OPEN = 0;
    public static final int OVERRIDE_TRANSITION_CLOSE = 1;

    public static final int RESULT_CANCELED = 0;
    public static final int RESULT_OK = -1;
    public static final int RESULT_FIRST_USER = 1;

    public interface ScreenCaptureCallback {
        void onScreenCaptured();
    }

    private final java.util.IdentityHashMap<android.content.ServiceConnection, Boolean> serviceBindings =
            new java.util.IdentityHashMap<android.content.ServiceConnection, Boolean>();
    private final java.util.IdentityHashMap<android.content.ServiceConnection, Boolean> releasedServiceBindings =
            new java.util.IdentityHashMap<android.content.ServiceConnection, Boolean>();

    @Override
    public boolean bindService(android.content.Intent intent, android.content.ServiceConnection connection, int flags) {
        boolean bound = super.bindService(intent, connection, flags);
        releasedServiceBindings.remove(connection);
        serviceBindings.put(connection, Boolean.TRUE);
        return bound;
    }

    @Override
    public void unbindService(android.content.ServiceConnection connection) {
        if (releasedServiceBindings.remove(connection) != null) return;
        super.unbindService(connection);
        serviceBindings.remove(connection);
    }

    /** Android releases connections registered through an Activity when that Activity is destroyed. */
    public final void shirazReleaseServiceBindings() {
        android.content.ServiceConnection[] connections =
                serviceBindings.keySet().toArray(new android.content.ServiceConnection[serviceBindings.size()]);
        serviceBindings.clear();
        for (android.content.ServiceConnection connection : connections) {
            releasedServiceBindings.put(connection, Boolean.TRUE);
            try {
                super.unbindService(connection);
            } catch (IllegalArgumentException alreadyUnbound) {
            }
        }
    }

    private android.media.session.MediaController mediaController;

    public final void setMediaController(android.media.session.MediaController controller) {
        mediaController = controller;
    }

    public final android.media.session.MediaController getMediaController() {
        return mediaController;
    }

    private ActivityManager.TaskDescription taskDescription;

    public void setTaskDescription(ActivityManager.TaskDescription description) {
        taskDescription = description;
    }

    public ActivityManager.TaskDescription getTaskDescription() {
        return taskDescription;
    }

    public final void runOnUiThread(Runnable action) {
        if (action == null) throw new NullPointerException("action");
        android.os.Looper main = android.os.Looper.getMainLooper();
        if (main == null) throw new IllegalStateException("UI Looper is not initialized");
        if (Thread.currentThread() == main.getThread()) action.run();
        else if (!new android.os.Handler(main).post(action)) throw new IllegalStateException("UI Looper has stopped");
    }

    private shiraz.runtime.PermissionRequests permissionRequests;

    public final void requestPermissions(String[] permissions, int requestCode) {
        if (permissionRequests == null) permissionRequests = new shiraz.runtime.PermissionRequests(this);
        permissionRequests.request(permissions, requestCode);
    }

    public final void requestPermissions(String[] permissions, int requestCode, int deviceId) {
        requestPermissions(permissions, requestCode);
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {}

    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults, int deviceId) {
        onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public boolean shouldShowRequestPermissionRationale(String permission) {
        if (permission == null) throw new IllegalArgumentException("permission");
        shiraz.runtime.CapturePermissions policy = shiraz.runtime.CapturePermissions.current();
        return policy != null && policy.canRequest(permission) && policy.wasDenied(permission);
    }

    public boolean shouldShowRequestPermissionRationale(String permission, int deviceId) {
        return shouldShowRequestPermissionRationale(permission);
    }

    public final shiraz.runtime.PermissionRequests shirazTakePermissionRequests() {
        shiraz.runtime.PermissionRequests value = permissionRequests;
        permissionRequests = null;
        if (value != null) value.bind(null);
        return value;
    }

    public final void shirazRestorePermissionRequests(shiraz.runtime.PermissionRequests value) {
        if (permissionRequests != null) throw new IllegalStateException("Permission owner already initialized");
        permissionRequests = value;
        if (value != null) value.bind(this);
    }

    private android.content.Intent intent;
    private ComponentCaller caller;
    private ComponentCaller initialCaller;
    private ComponentCaller currentCaller;

    public android.content.Intent getIntent() {
        return intent;
    }

    public void setIntent(android.content.Intent value) {
        intent = value;
    }

    public void setIntent(android.content.Intent newIntent, ComponentCaller newCaller) {
        intent = newIntent;
        caller = newCaller;
    }

    public ComponentCaller getCaller() {
        return caller;
    }

    public ComponentCaller getInitialCaller() {
        if (initialCaller == null) {
            initialCaller = new ComponentCaller(-1, null);
        }
        return initialCaller;
    }

    public ComponentCaller getCurrentCaller() {
        return currentCaller != null ? currentCaller : caller;
    }

    protected void onNewIntent(android.content.Intent value) {}

    public void onNewIntent(android.content.Intent intent, ComponentCaller caller) {
        ComponentCaller prev = currentCaller;
        currentCaller = caller;
        try {
            onNewIntent(intent);
        } finally {
            currentCaller = prev;
        }
    }

    public void setLocusContext(android.content.LocusId locusId, Bundle bundle) {}

    private FragmentManager fragments;
    private int fragmentPhase;

    @Deprecated
    public FragmentManager getFragmentManager() {
        if (fragments == null) fragments = new FragmentManager.Impl(this, fragmentPhase);
        return fragments;
    }

    @Deprecated
    public void onAttachFragment(Fragment fragment) {}

    public final void shirazPrepareFragments(Bundle state) {
        fragmentPhase = 1;
        if (state != null && state.getBundle("android:fragments") != null) {
            getFragmentManager().restore(state.getBundle("android:fragments"));
        }
    }

    public final void shirazMoveFragments(int phase) {
        fragmentPhase = phase;
        if (fragments != null) fragments.moveTo(phase);
    }

    public final void shirazSaveFragments(Bundle out) {
        if (fragments != null) out.putBundle("android:fragments", fragments.save());
    }

    public final void shirazDestroyFragments() {
        fragmentPhase = 0;
        if (fragments != null) fragments.destroy();
    }

    private LoaderManager.Impl loaderManager;

    @Deprecated
    public LoaderManager getLoaderManager() {
        if (loaderManager == null) loaderManager = new LoaderManager.Impl();
        return loaderManager;
    }

    private CharSequence title = "";
    private int titleColor;
    private boolean titleBound;

    public final void shirazBindTitle(CharSequence value) {
        if (titleBound) throw new IllegalStateException("Activity title already initialized");
        android.view.Window.shirazTitle(value);
        titleBound = true;
        title = value;
        getWindow().setTitle(value);
    }

    public void setTitle(CharSequence value) {
        android.view.Window.shirazTitle(value);
        title = value;
        onTitleChanged(value, titleColor);
    }

    public void setTitle(int id) {
        setTitle(getString(id));
    }

    public final CharSequence getTitle() {
        return title;
    }

    @Deprecated
    public void setTitleColor(int color) {
        titleColor = color;
        onTitleChanged(title, color);
    }

    public final int getTitleColor() {
        return titleColor;
    }

    protected void onTitleChanged(CharSequence value, int color) {
        getWindow().setTitle(value);
        if (color != 0) getWindow().setTitleColor(color);
    }

    protected void onChildTitleChanged(Activity child, CharSequence value) {}

    private android.content.ComponentName component;

    public final void shirazBindComponent(android.content.ComponentName value) {
        shirazBindComponent(value, value == null ? null : value.getClassName());
    }

    public final void shirazBindComponent(android.content.ComponentName value, String targetClass) {
        if (value == null) throw new NullPointerException("component");
        if (component != null) throw new IllegalStateException("Activity component already bound");
        if (!getClass().getName().equals(targetClass)) {
            throw new IllegalArgumentException("Activity class identity mismatch");
        }
        component = value;
    }

    public android.content.ComponentName getComponentName() {
        return component;
    }

    public final void setVolumeControlStream(int streamType) {
        getWindow().setVolumeControlStream(streamType);
    }

    public final int getVolumeControlStream() {
        return getWindow().getVolumeControlStream();
    }

    public android.content.SharedPreferences getPreferences(int mode) {
        return getSharedPreferences(getLocalClassName(), mode);
    }

    public String getLocalClassName() {
        if (component == null) throw new IllegalStateException("Activity component is not bound");
        String name = component.getClassName();
        String prefix = component.getPackageName() + ".";
        return name.startsWith(prefix) ? name.substring(prefix.length()) : name;
    }

    private final android.view.KeyEvent.DispatcherState keyState = new android.view.KeyEvent.DispatcherState();
    private View content;
    private android.view.Window window;
    private ActionBar actionBar;
    private boolean windowFocused;

    public ActionBar getActionBar() {
        if (actionBar != null) return actionBar;
        android.view.Window w = getWindow();
        if (w.hasFeature(android.view.Window.FEATURE_NO_TITLE)) return null;
        actionBar = new ActionBar.Impl(this);
        return actionBar;
    }

    public void setActionBar(android.widget.Toolbar toolbar) {
        if (toolbar == null) {
            actionBar = null;
            return;
        }
        ActionBar.Impl impl = new ActionBar.Impl(this);
        if (toolbar.getTitle() != null) {
            impl.setTitle(toolbar.getTitle());
        } else if (title != null && title.length() > 0) {
            impl.setTitle(title);
            toolbar.setTitle(title);
        }
        if (toolbar.getSubtitle() != null) {
            impl.setSubtitle(toolbar.getSubtitle());
        }
        actionBar = impl;
    }

    public android.view.Window getWindow() {
        if (window == null) {
            window = new android.view.Window(this);
            window.setCallback(this);
            window.shirazSetRootListener(new Runnable() {
                public void run() {
                    content = window.peekDecorView();
                }
            });
        }
        return window;
    }

    public void onContentChanged() {}

    public void onWindowFocusChanged(boolean focused) {
        windowFocused = focused;
    }

    public void onWindowAttributesChanged(android.view.WindowManager.LayoutParams params) {}

    public void onAttachedToWindow() {}

    public void onDetachedFromWindow() {}

    public boolean hasWindowFocus() {
        return windowFocused || (content != null && content.hasWindowFocus());
    }

    private android.view.MenuInflater menuInflater;

    public android.view.MenuInflater getMenuInflater() {
        if (menuInflater == null) menuInflater = new android.view.MenuInflater(this);
        return menuInflater;
    }

    public void invalidateOptionsMenu() {
        getWindow().invalidatePanelMenu(0);
    }

    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        return true;
    }

    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        return true;
    }

    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        return false;
    }

    public void onOptionsMenuClosed(android.view.Menu menu) {}

    public View onCreatePanelView(int feature) {
        return null;
    }

    public boolean onCreatePanelMenu(int feature, android.view.Menu menu) {
        return feature == 0 && onCreateOptionsMenu(menu);
    }

    public boolean onPreparePanel(int feature, View view, android.view.Menu menu) {
        return feature == 0 && onPrepareOptionsMenu(menu);
    }

    public boolean onMenuItemSelected(int feature, android.view.MenuItem item) {
        return feature == 0 ? onOptionsItemSelected(item) : feature == 6 && onContextItemSelected(item);
    }

    public boolean onMenuOpened(int feature, android.view.Menu menu) {
        return true;
    }

    public void onPanelClosed(int feature, android.view.Menu menu) {
        if (feature == 0) onOptionsMenuClosed(menu);
    }

    public void openOptionsMenu() {
        getWindow().openPanel(0, null);
    }

    public void closeOptionsMenu() {
        getWindow().closePanel(0);
    }

    public final void shirazReleaseWindow() {
        if (window != null) window.shirazDestroy();
    }

    public final boolean requestWindowFeature(int feature) {
        return getWindow().requestFeature(feature);
    }

    public final void setFeatureDrawableResource(int featureId, int resId) {}

    public final void setFeatureDrawableUri(int featureId, android.net.Uri uri) {}

    public final void setFeatureDrawable(int featureId, android.graphics.drawable.Drawable drawable) {}

    public final void setFeatureDrawableAlpha(int featureId, int alpha) {}

    /** Deprecated title-bar progress; Material and AppCompat decors have no slot for it, so this shows nothing (as on API 21+). */
    @Deprecated
    public final void setProgressBarIndeterminateVisibility(boolean visible) {}

    @Deprecated
    public final void setProgressBarVisibility(boolean visible) {}

    @Deprecated
    public final void setProgressBarIndeterminate(boolean indeterminate) {}

    @Deprecated
    public final void setProgress(int progress) {}

    @Deprecated
    public final void setSecondaryProgress(int progress) {}

    public View getCurrentFocus() {
        return getWindow().getCurrentFocus();
    }

    public void addContentView(View view, android.view.ViewGroup.LayoutParams params) {
        getWindow().addContentView(view, params);
    }

    public void setContentView(View view, android.view.ViewGroup.LayoutParams params) {
        keyState.reset();
        getWindow().setContentView(view, params);
    }

    private android.transition.TransitionManager contentTransitionManager;
    private boolean finishOnTouchOutside = true;
    private int defaultKeyMode = DEFAULT_KEYS_DISABLE;

    public android.transition.TransitionManager getContentTransitionManager() {
        return contentTransitionManager;
    }

    public void setContentTransitionManager(android.transition.TransitionManager tm) {
        contentTransitionManager = tm;
    }

    public android.transition.Scene getContentScene() {
        return null;
    }

    public void setFinishOnTouchOutside(boolean finish) {
        finishOnTouchOutside = finish;
    }

    public final void setDefaultKeyMode(int mode) {
        defaultKeyMode = mode;
    }

    private shiraz.runtime.OrientationRequest orientation;

    public final void shirazBindOrientation(shiraz.runtime.OrientationRequest value) {
        if (orientation != null || value == null) {
            throw new IllegalStateException("Orientation owner already bound or missing");
        }
        orientation = value;
    }

    public int getRequestedOrientation() {
        return orientation == null ? -1 : orientation.get();
    }

    public void setRequestedOrientation(int value) {
        if (orientation == null) throw new IllegalStateException("Activity has no host orientation owner");
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        orientation.request(value, metrics.widthPixels, metrics.heightPixels);
    }

    private boolean finishing;
    private boolean changingConfigurations;
    private boolean destroyed;
    private boolean recreationRequested;
    private int changingConfigMask;
    private final Thread activityThread = Thread.currentThread();

    /** Schedule replacement after the current input/lifecycle callback returns. */
    public void recreate() {
        if (Thread.currentThread() != activityThread) {
            throw new IllegalStateException("Activity recreation requires the activity thread");
        }
        if (!finishing && !destroyed) recreationRequested = true;
    }

    public final boolean shirazTakeRecreationRequest() {
        boolean requested = recreationRequested;
        recreationRequested = false;
        return requested && !finishing && !destroyed;
    }

    private Object lastNonConfiguration;

    public boolean isChangingConfigurations() {
        return changingConfigurations;
    }

    public int getChangingConfigurations() {
        return changingConfigMask;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    /** Desktop window resizing does not place this activity in an Android split-screen task. */
    public boolean isInMultiWindowMode() {
        return false;
    }

    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, android.content.res.Configuration newConfig) {
        onMultiWindowModeChanged(isInMultiWindowMode);
    }

    @Deprecated
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {}

    public Object onRetainNonConfigurationInstance() {
        return null;
    }

    public Object getLastNonConfigurationInstance() {
        return lastNonConfiguration;
    }

    public final void shirazSetLifecycleState(boolean changing, boolean destroyed, Object retained) {
        changingConfigurations = changing;
        this.destroyed = destroyed;
        lastNonConfiguration = retained;
    }

    public final void shirazSetDestroyed() {
        destroyed = true;
        if (permissionRequests != null) {
            permissionRequests.close();
            permissionRequests = null;
        }
    }

    public Activity() {
        super();
    }

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(newBase);
    }

    @Override
    public void setTheme(int resid) {
        super.setTheme(resid);
    }

    protected void onApplyThemeResource(android.content.res.Resources.Theme theme, int resid, boolean first) {
        if (theme != null) {
            theme.applyStyle(resid, true);
        }
    }

    public void registerForContextMenu(View view) {
        view.setOnCreateContextMenuListener(this);
    }

    public void unregisterForContextMenu(View view) {
        view.setOnCreateContextMenuListener(null);
    }

    public void onCreateContextMenu(
            android.view.ContextMenu menu, View view, android.view.ContextMenu.ContextMenuInfo info) {}

    public boolean onContextItemSelected(android.view.MenuItem item) {
        return false;
    }

    public void onContextMenuClosed(android.view.Menu menu) {}

    public void openContextMenu(View view) {
        view.showContextMenu();
    }

    public final shiraz.runtime.HostContextMenu shirazCreateContextMenu(View view) {
        if (view == null) throw new NullPointerException("view");
        shiraz.runtime.HostContextMenu menu = new shiraz.runtime.HostContextMenu(this);
        menu.shirazSetSelectionListener(new android.view.MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem item) {
                return onContextItemSelected(item);
            }
        });
        view.createContextMenu(menu);
        return menu;
    }

    public void closeContextMenu() {
        throw new UnsupportedOperationException("Context menu popup presentation is not implemented");
    }

    public void onConfigurationChanged(android.content.res.Configuration configuration) {
        if (fragments != null) fragments.configuration(configuration);
    }

    public void onLowMemory() {
        if (fragments != null) fragments.lowMemory();
    }

    public void onTrimMemory(int level) {
        if (fragments != null) fragments.trimMemory(level);
    }

    @Override
    public void registerComponentCallbacks(android.content.ComponentCallbacks callback) {
        super.registerComponentCallbacks(callback);
    }

    @Override
    public void unregisterComponentCallbacks(android.content.ComponentCallbacks callback) {
        super.unregisterComponentCallbacks(callback);
    }

    private final Application.LifecycleCallbacks lifecycleCallbacks = new Application.LifecycleCallbacks();

    public void registerActivityLifecycleCallbacks(Application.ActivityLifecycleCallbacks callback) {
        lifecycleCallbacks.add(callback);
    }

    public void unregisterActivityLifecycleCallbacks(Application.ActivityLifecycleCallbacks callback) {
        lifecycleCallbacks.remove(callback);
    }

    public final void shirazDispatchLifecycle(int kind, int phase, Bundle state) {
        android.content.Context root = this;
        while (root instanceof android.content.ContextWrapper && !(root instanceof Application)) {
            root = ((android.content.ContextWrapper) root).getBaseContext();
            if (root == null) break;
        }
        android.content.Context context = root == null ? null : getApplicationContext();
        Application app = context instanceof Application ? (Application) context : null;
        boolean appFirst = phase == 0 || (phase == 1 && kind < 3);
        if (appFirst && app != null) app.shirazDispatchLifecycle(kind, phase, this, state);
        lifecycleCallbacks.dispatch(kind, phase, this, state, kind >= 3);
        if (!appFirst && app != null) app.shirazDispatchLifecycle(kind, phase, this, state);
    }

    private android.window.SplashScreen splashScreen;

    public final android.window.SplashScreen getSplashScreen() {
        if (splashScreen == null) {
            splashScreen = new android.window.SplashScreen() {
                @Override
                public void setOnExitAnimationListener(
                        android.window.SplashScreen.OnExitAnimationListener listener) {}

                @Override
                public void clearOnExitAnimationListener() {}

                @Override
                public void setSplashScreenTheme(int themeId) {}
            };
        }
        return splashScreen;
    }

    protected void onCreate(Bundle savedInstanceState) {
        shirazDispatchLifecycle(0, 1, savedInstanceState);
    }

    public void onCreate(Bundle savedInstanceState, android.os.PersistableBundle persistentState) {
        onCreate(savedInstanceState);
    }

    protected void onSaveInstanceState(Bundle state) {
        if (state == null) throw new NullPointerException("state");
        if (content != null) {
            Bundle hierarchy = new Bundle();
            android.util.SparseArray<android.os.Parcelable> views =
                    new android.util.SparseArray<android.os.Parcelable>();
            content.saveHierarchyState(views);
            hierarchy.putSparseParcelableArray("android:views", views);
            View focused = content.findFocus();
            if (focused != null && focused.getId() != View.NO_ID) {
                hierarchy.putInt("android:focusedViewId", focused.getId());
            }
            state.putBundle("android:viewHierarchyState", hierarchy);
        }
        shirazDispatchLifecycle(5, 1, state);
    }

    public void onSaveInstanceState(Bundle outState, android.os.PersistableBundle outPersistentState) {
        onSaveInstanceState(outState);
    }

    protected void onRestoreInstanceState(Bundle state) {
        if (state == null) throw new NullPointerException("state");
        Bundle hierarchy = state.getBundle("android:viewHierarchyState");
        if (content != null && hierarchy != null) {
            android.util.SparseArray<android.os.Parcelable> views =
                    hierarchy.getSparseParcelableArray("android:views");
            if (views != null) content.restoreHierarchyState(views);
            int focused = hierarchy.getInt("android:focusedViewId", View.NO_ID);
            View view = focused == View.NO_ID ? null : content.findViewById(focused);
            if (view != null) view.requestFocus();
        }
    }

    public void onRestoreInstanceState(Bundle savedInstanceState, android.os.PersistableBundle persistentState) {
        onRestoreInstanceState(savedInstanceState);
    }

    protected void onStart() {
        shirazDispatchLifecycle(1, 1, null);
    }

    protected void onRestart() {}

    public void onStateNotSaved() {}

    protected void onResume() {
        shirazDispatchLifecycle(2, 1, null);
    }

    protected void onPostCreate(Bundle state) {}

    public void onPostCreate(Bundle savedInstanceState, android.os.PersistableBundle persistentState) {
        onPostCreate(savedInstanceState);
    }

    protected void onPostResume() {}

    public void onTopResumedActivityChanged(boolean isTopResumedActivity) {}

    protected void onPause() {
        shirazDispatchLifecycle(3, 1, null);
    }

    protected void onStop() {
        shirazDispatchLifecycle(4, 1, null);
    }

    protected void onDestroy() {
        shirazDispatchLifecycle(6, 1, null);
    }

    public boolean isVoiceInteraction() {
        return false;
    }

    public boolean isVoiceInteractionRoot() {
        return false;
    }

    public VoiceInteractor getVoiceInteractor() {
        return null;
    }

    public boolean isLocalVoiceInteractionSupported() {
        return false;
    }

    public void startLocalVoiceInteraction(Bundle privateOptions) {}

    public void onLocalVoiceInteractionStarted() {}

    public void onLocalVoiceInteractionStopped() {}

    public void stopLocalVoiceInteraction() {}

    @Deprecated
    public boolean onCreateThumbnail(android.graphics.Bitmap outBitmap, android.graphics.Canvas canvas) {
        return false;
    }

    public CharSequence onCreateDescription() {
        return null;
    }

    public void onProvideAssistData(Bundle data) {}

    public void onProvideAssistContent(android.app.assist.AssistContent outContent) {}

    public void onGetDirectActions(
            android.os.CancellationSignal cancellationSignal,
            java.util.function.Consumer<java.util.List<DirectAction>> callback) {
        if (callback != null) {
            callback.accept(java.util.Collections.<DirectAction>emptyList());
        }
    }

    public void onPerformDirectAction(
            String actionId,
            Bundle arguments,
            android.os.CancellationSignal cancellationSignal,
            java.util.function.Consumer<Bundle> resultListener) {
        if (resultListener != null) {
            resultListener.accept(Bundle.EMPTY);
        }
    }

    public final void requestShowKeyboardShortcuts() {}

    public final void dismissKeyboardShortcutsHelper() {}

    public void onProvideKeyboardShortcuts(
            java.util.List<android.view.KeyboardShortcutGroup> data, android.view.Menu menu, int deviceId) {}

    public boolean showAssist(Bundle args) {
        return false;
    }

    private boolean shouldDockBigOverlays = true;

    public void requestFullscreenMode(
            int request, android.os.OutcomeReceiver<Void, Throwable> approvalCallback) {
        if (approvalCallback != null) {
            approvalCallback.onResult(null);
        }
    }

    public void setShouldDockBigOverlays(boolean shouldDockBigOverlays) {
        this.shouldDockBigOverlays = shouldDockBigOverlays;
    }

    public boolean shouldDockBigOverlays() {
        return shouldDockBigOverlays;
    }

    @Deprecated
    public final android.database.Cursor managedQuery(
            android.net.Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        return getContentResolver().query(uri, projection, selection, selectionArgs, sortOrder);
    }

    @Deprecated
    public void startManagingCursor(android.database.Cursor c) {}

    @Deprecated
    public void stopManagingCursor(android.database.Cursor c) {}

    @Override
    public Object getSystemService(String name) {
        Object value = super.getSystemService(name);
        if (LAYOUT_INFLATER_SERVICE.equals(name)) {
            ((android.view.LayoutInflater) value).setPrivateFactory(this);
        }
        return value;
    }

    public View onCreateView(String name, android.content.Context context, android.util.AttributeSet attrs) {
        if ("fragment".equals(name)) {
            throw new UnsupportedOperationException("Platform fragment inflation is not implemented");
        }
        return null;
    }

    public View onCreateView(
            View parent, String name, android.content.Context context, android.util.AttributeSet attrs) {
        return onCreateView(name, context, attrs);
    }

    public android.view.LayoutInflater getLayoutInflater() {
        return android.view.LayoutInflater.from(this);
    }

    public void setContentView(int resource) {
        keyState.reset();
        getWindow().setContentView(resource);
    }

    public void setContentView(View view) {
        keyState.reset();
        getWindow().setContentView(view);
    }

    public <T extends View> T findViewById(int id) {
        return content == null ? null : content.<T>findViewById(id);
    }

    public final <T extends View> T requireViewById(int id) {
        T view = findViewById(id);
        if (view == null) {
            throw new IllegalArgumentException("ID does not reference a View inside this Activity");
        }
        return view;
    }

    public void onUserInteraction() {}

    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event == null) throw new NullPointerException("event");
        onUserInteraction();
        if (content != null
                && content.getVisibility() == View.VISIBLE
                && content.hasFocus()
                && content.dispatchKeyEvent(event)) {
            return true;
        }
        return event.dispatch(this, keyState, this);
    }

    public boolean dispatchKeyShortcutEvent(android.view.KeyEvent event) {
        onUserInteraction();
        return event != null && onKeyShortcut(event.getKeyCode(), event);
    }

    public boolean onKeyDown(int code, android.view.KeyEvent event) {
        if (code == android.view.KeyEvent.KEYCODE_BACK) {
            event.startTracking();
            return true;
        }
        return false;
    }

    public boolean onKeyLongPress(int code, android.view.KeyEvent event) {
        return false;
    }

    public boolean onKeyUp(int code, android.view.KeyEvent event) {
        if (code == android.view.KeyEvent.KEYCODE_BACK && event.isTracking() && !event.isCanceled()) {
            onBackPressed();
            return true;
        }
        return false;
    }

    public boolean onKeyMultiple(int code, int count, android.view.KeyEvent event) {
        return false;
    }

    public boolean onKeyShortcut(int keyCode, android.view.KeyEvent event) {
        return false;
    }

    public void onBackPressed() {
        finish();
    }

    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        if (event == null) throw new NullPointerException("event");
        if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) onUserInteraction();
        if (content != null && content.getVisibility() == View.VISIBLE && content.dispatchTouchEvent(event)) {
            return true;
        }
        return onTouchEvent(event);
    }

    public boolean dispatchTrackballEvent(android.view.MotionEvent ev) {
        onUserInteraction();
        return onTrackballEvent(ev);
    }

    public boolean dispatchGenericMotionEvent(android.view.MotionEvent ev) {
        onUserInteraction();
        return onGenericMotionEvent(ev);
    }

    public boolean dispatchPopulateAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
        return false;
    }

    public boolean onTouchEvent(android.view.MotionEvent event) {
        return false;
    }

    public boolean onTrackballEvent(android.view.MotionEvent event) {
        return false;
    }

    public boolean onGenericMotionEvent(android.view.MotionEvent event) {
        return false;
    }

    public android.view.WindowManager getWindowManager() {
        return (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
    }

    public final Application getApplication() {
        return (Application) getApplicationContext();
    }

    private android.util.SparseArray<Dialog> managedDialogs;
    private android.view.SearchEvent currentSearchEvent;

    @Deprecated
    protected Dialog onCreateDialog(int id) {
        return null;
    }

    @Deprecated
    protected Dialog onCreateDialog(int id, Bundle args) {
        return onCreateDialog(id);
    }

    @Deprecated
    protected void onPrepareDialog(int id, Dialog dialog) {
        if (dialog != null) {
            dialog.setOwnerActivity(this);
        }
    }

    @Deprecated
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
        onPrepareDialog(id, dialog);
    }

    @Deprecated
    public final void showDialog(int id) {
        showDialog(id, null);
    }

    @Deprecated
    public final boolean showDialog(int id, Bundle args) {
        if (managedDialogs == null) {
            managedDialogs = new android.util.SparseArray<Dialog>();
        }
        Dialog d = managedDialogs.get(id);
        if (d == null) {
            d = onCreateDialog(id, args);
            if (d == null) {
                return false;
            }
            d.setOwnerActivity(this);
            managedDialogs.put(id, d);
        }
        onPrepareDialog(id, d, args);
        d.show();
        return true;
    }

    @Deprecated
    public final void dismissDialog(int id) {
        Dialog d = managedDialogs != null ? managedDialogs.get(id) : null;
        if (d == null) {
            throw new IllegalArgumentException("no dialog with id " + id + " was ever shown via Activity#showDialog");
        }
        d.dismiss();
    }

    @Deprecated
    public final void removeDialog(int id) {
        if (managedDialogs != null) {
            Dialog d = managedDialogs.get(id);
            if (d != null) {
                d.dismiss();
                managedDialogs.remove(id);
            }
        }
    }

    public boolean onSearchRequested(android.view.SearchEvent searchEvent) {
        currentSearchEvent = searchEvent;
        try {
            return onSearchRequested();
        } finally {
            currentSearchEvent = null;
        }
    }

    public boolean onSearchRequested() {
        startSearch(null, false, null, false);
        return true;
    }

    public final android.view.SearchEvent getSearchEvent() {
        return currentSearchEvent;
    }

    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData, boolean globalSearch) {}

    public void triggerSearch(String query, Bundle appSearchData) {}

    public void takeKeyEvents(boolean get) {}

    @Override
    public void startActivity(android.content.Intent intent) {
        startActivity(intent, null);
    }

    @Override
    public void startActivity(android.content.Intent intent, Bundle options) {
        if (options != null && !options.isEmpty()) {
            throw new UnsupportedOperationException("Activity launch options");
        }
        shiraz.runtime.CapturePermissions policy = shiraz.runtime.CapturePermissions.current();
        if (policy != null
                && intent != null
                && intent.getComponent() == null
                && "android.settings.APPLICATION_DETAILS_SETTINGS".equals(intent.getAction())
                && intent.getData() != null
                && ("package:" + getPackageName()).equals(intent.getData().toString())) {
            policy.showPrivacy(this);
            return;
        }
        if (shiraz.runtime.HostAppInstaller.handlesRemoval(intent)) {
            shiraz.runtime.HostAppInstaller.confirmRemoval(
                    this,
                    intent,
                    new shiraz.runtime.HostAppInstaller.RemovalResult() {
                        public void complete(boolean removed) {
                            shirazDeliverResult(
                                    removed ? RESULT_OK : RESULT_CANCELED,
                                    new android.content.Intent()
                                            .putExtra("android.intent.extra.INSTALL_RESULT", removed ? 1 : -1));
                        }
                    });
            return;
        }
        shirazActivityLaunches().enqueue(intent, launchingForResult);
    }

    public void startActivities(android.content.Intent[] intents) {
        startActivities(intents, null);
    }

    public void startActivities(android.content.Intent[] intents, Bundle options) {
        if (intents == null) throw new NullPointerException("intents");
        for (int i = 0; i < intents.length; i++) {
            startActivity(intents[i], options);
        }
    }

    private int resultCode = RESULT_CANCELED;
    private int pendingRequestCode = -1;
    private boolean launchingForResult;
    private android.content.Intent resultData;

    public final void setResult(int code) {
        resultCode = code;
        resultData = null;
    }

    public final void setResult(int code, android.content.Intent data) {
        resultCode = code;
        resultData = data;
    }

    public void startActivityForResult(android.content.Intent intent, int requestCode) {
        startActivityForResult(intent, requestCode, null);
    }

    public void startActivityForResult(android.content.Intent intent, int requestCode, Bundle options) {
        if (intent != null
                && requestCode >= 0
                && (intent.getFlags() & android.content.Intent.FLAG_ACTIVITY_FORWARD_RESULT) != 0) {
            throw new android.util.AndroidRuntimeException(
                    "FORWARD_RESULT cannot be combined with a result request");
        }
        if (options != null && !options.isEmpty()) {
            throw new UnsupportedOperationException("Activity launch options");
        }
        if (requestCode >= 0
                && shiraz.runtime.HostDocumentPicker.handles(intent)
                && (!shiraz.runtime.HostContentPicker.handles(intent)
                        || !shiraz.runtime.HostContentPicker.hasConfiguredFile())) {
            pendingRequestCode = requestCode;
            android.widget.PopupWindow.shirazClear();
            shiraz.runtime.HostDocumentPicker.launch(
                    this,
                    intent,
                    new shiraz.runtime.HostDocumentPicker.Result() {
                        public void complete(int code, android.content.Intent data) {
                            shirazDeliverResult(code, data);
                        }
                    });
            return;
        }
        if (requestCode >= 0 && shiraz.runtime.HostCameraCapture.handles(intent)) {
            pendingRequestCode = requestCode;
            android.widget.PopupWindow.shirazClear();
            shiraz.runtime.HostCameraCapture.launch(
                    this,
                    intent,
                    new shiraz.runtime.HostCameraCapture.Result() {
                        public void complete(int code, android.content.Intent data) {
                            shirazDeliverResult(code, data);
                        }
                    });
            return;
        }
        if (requestCode >= 0 && shiraz.runtime.HostContentPicker.handles(intent)) {
            pendingRequestCode = requestCode;
            android.widget.PopupWindow.shirazClear();
            android.net.Uri uri = shiraz.runtime.HostContentPicker.pick(intent);
            android.content.Intent data = null;
            int code = RESULT_CANCELED;
            if (uri != null) {
                data = new android.content.Intent();
                data.setData(uri);
                data.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                code = RESULT_OK;
            }
            shirazDeliverResult(code, data);
            return;
        }
        int previousRequest = pendingRequestCode;
        boolean previousLaunch = launchingForResult;
        if (requestCode >= 0) pendingRequestCode = requestCode;
        launchingForResult = requestCode >= 0;
        try {
            startActivity(intent, options);
        } catch (RuntimeException failure) {
            pendingRequestCode = previousRequest;
            throw failure;
        } finally {
            launchingForResult = previousLaunch;
        }
    }

    public boolean isActivityTransitionRunning() {
        return false;
    }

    public void startIntentSenderForResult(
            android.content.IntentSender intent,
            int requestCode,
            android.content.Intent fillInIntent,
            int flagsMask,
            int flagsValues,
            int extraFlags)
            throws android.content.IntentSender.SendIntentException {
        startIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask, flagsValues, extraFlags, null);
    }

    public void startIntentSenderForResult(
            android.content.IntentSender intent,
            int requestCode,
            android.content.Intent fillInIntent,
            int flagsMask,
            int flagsValues,
            int extraFlags,
            Bundle options)
            throws android.content.IntentSender.SendIntentException {
        if (intent == null) {
            throw new android.content.IntentSender.SendIntentException();
        }
        intent.sendIntent(this, 0, fillInIntent, null, null);
    }

    public void startIntentSender(
            android.content.IntentSender intent,
            android.content.Intent fillInIntent,
            int flagsMask,
            int flagsValues,
            int extraFlags)
            throws android.content.IntentSender.SendIntentException {
        startIntentSender(intent, fillInIntent, flagsMask, flagsValues, extraFlags, null);
    }

    public void startIntentSender(
            android.content.IntentSender intent,
            android.content.Intent fillInIntent,
            int flagsMask,
            int flagsValues,
            int extraFlags,
            Bundle options)
            throws android.content.IntentSender.SendIntentException {
        startIntentSenderForResult(intent, -1, fillInIntent, flagsMask, flagsValues, extraFlags, options);
    }

    public boolean startActivityIfNeeded(android.content.Intent intent, int requestCode) {
        return startActivityIfNeeded(intent, requestCode, null);
    }

    public boolean startActivityIfNeeded(android.content.Intent intent, int requestCode, Bundle options) {
        startActivityForResult(intent, requestCode, options);
        return true;
    }

    public boolean startNextMatchingActivity(android.content.Intent intent) {
        return startNextMatchingActivity(intent, null);
    }

    public boolean startNextMatchingActivity(android.content.Intent intent, Bundle options) {
        return false;
    }

    @Deprecated
    public void startActivityFromChild(Activity child, android.content.Intent intent, int requestCode) {
        startActivityFromChild(child, intent, requestCode, null);
    }

    @Deprecated
    public void startActivityFromChild(
            Activity child, android.content.Intent intent, int requestCode, Bundle options) {
        startActivityForResult(intent, requestCode, options);
    }

    @Deprecated
    public void startActivityFromFragment(Fragment fragment, android.content.Intent intent, int requestCode) {
        startActivityFromFragment(fragment, intent, requestCode, null);
    }

    @Deprecated
    public void startActivityFromFragment(
            Fragment fragment, android.content.Intent intent, int requestCode, Bundle options) {
        startActivityForResult(intent, requestCode, options);
    }

    @Deprecated
    public void startIntentSenderFromChild(
            Activity child,
            android.content.IntentSender intent,
            int requestCode,
            android.content.Intent fillInIntent,
            int flagsMask,
            int flagsValues,
            int extraFlags)
            throws android.content.IntentSender.SendIntentException {
        startIntentSenderFromChild(
                child, intent, requestCode, fillInIntent, flagsMask, flagsValues, extraFlags, null);
    }

    @Deprecated
    public void startIntentSenderFromChild(
            Activity child,
            android.content.IntentSender intent,
            int requestCode,
            android.content.Intent fillInIntent,
            int flagsMask,
            int flagsValues,
            int extraFlags,
            Bundle options)
            throws android.content.IntentSender.SendIntentException {
        startIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask, flagsValues, extraFlags, options);
    }

    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {}

    public void onActivityResult(
            int requestCode, int resultCode, android.content.Intent data, ComponentCaller caller) {
        ComponentCaller prev = currentCaller;
        currentCaller = caller;
        try {
            onActivityResult(requestCode, resultCode, data);
        } finally {
            currentCaller = prev;
        }
    }

    public void onActivityReenter(int resultCode, android.content.Intent data) {}

    public PendingIntent createPendingResult(int requestCode, android.content.Intent data, int flags) {
        return PendingIntent.getActivity(this, requestCode, data != null ? data : new android.content.Intent(), flags);
    }

    public final int shirazResultCode() {
        return resultCode;
    }

    public final android.content.Intent shirazResultData() {
        return resultData;
    }

    public final void shirazDeliverResult(int code, android.content.Intent data) {
        int request = pendingRequestCode;
        if (request < 0) return;
        pendingRequestCode = -1;
        onActivityResult(request, code, data);
    }

    public android.net.Uri getReferrer() {
        if (intent != null) {
            android.net.Uri ref = intent.getParcelableExtra("android.intent.extra.REFERRER");
            if (ref != null) return ref;
            String refName = intent.getStringExtra("android.intent.extra.REFERRER_NAME");
            if (refName != null) return android.net.Uri.parse(refName);
        }
        return null;
    }

    public android.net.Uri onProvideReferrer() {
        return null;
    }

    public android.content.ComponentName getCallingActivity() {
        return null;
    }

    public String getCallingPackage() {
        android.content.ComponentName callerComponent = getCallingActivity();
        return callerComponent == null ? null : callerComponent.getPackageName();
    }

    public int getLaunchedFromUid() {
        return -1;
    }

    public String getLaunchedFromPackage() {
        return null;
    }

    private int[] pendingTransition;

    @Deprecated
    public void overridePendingTransition(int enterAnim, int exitAnim) {
        pendingTransition = new int[] {enterAnim, exitAnim};
    }

    @Deprecated
    public void overridePendingTransition(int enterAnim, int exitAnim, int backgroundColor) {
        pendingTransition = new int[] {enterAnim, exitAnim};
    }

    public void overrideActivityTransition(int overrideType, int enterAnim, int exitAnim) {
        pendingTransition = new int[] {enterAnim, exitAnim};
    }

    public void overrideActivityTransition(
            int overrideType, int enterAnim, int exitAnim, int backgroundColor) {
        pendingTransition = new int[] {enterAnim, exitAnim};
    }

    public void clearOverrideActivityTransition(int overrideType) {
        pendingTransition = null;
    }

    public final int[] shirazTakeTransition() {
        int[] value = pendingTransition;
        pendingTransition = null;
        return value;
    }

    protected void onUserLeaveHint() {}

    public void reportFullyDrawn() {}

    public void setShowWhenLocked(boolean value) {}

    public void setInheritShowWhenLocked(boolean inheritShowWhenLocked) {}

    public void setTurnScreenOn(boolean value) {}

    public void setAllowCrossUidActivitySwitchFromBelow(boolean allowed) {}

    public void setRecentsScreenshotEnabled(boolean enabled) {}

    public boolean isInPictureInPictureMode() {
        return false;
    }

    public void onPictureInPictureModeChanged(
            boolean isInPictureInPictureMode, android.content.res.Configuration newConfig) {
        onPictureInPictureModeChanged(isInPictureInPictureMode);
    }

    @Deprecated
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {}

    public void onPictureInPictureUiStateChanged(PictureInPictureUiState pipState) {}

    @Deprecated
    public void enterPictureInPictureMode() {}

    public boolean enterPictureInPictureMode(PictureInPictureParams params) {
        return false;
    }

    public void setPictureInPictureParams(PictureInPictureParams params) {}

    public int getMaxNumPictureInPictureActions() {
        return 3;
    }

    public boolean onPictureInPictureRequested() {
        return false;
    }

    public final boolean isChild() {
        return false;
    }

    public final Activity getParent() {
        return null;
    }

    public android.content.Intent getParentActivityIntent() {
        return null;
    }

    public boolean shouldUpRecreateTask(android.content.Intent target) {
        return false;
    }

    public boolean navigateUpTo(android.content.Intent upIntent) {
        if (upIntent == null) return false;
        startActivity(upIntent);
        finish();
        return true;
    }

    @Deprecated
    public boolean navigateUpToFromChild(Activity child, android.content.Intent upIntent) {
        return navigateUpTo(upIntent);
    }

    public boolean onNavigateUp() {
        android.content.Intent upIntent = getParentActivityIntent();
        if (upIntent == null) return false;
        if (shouldUpRecreateTask(upIntent)) {
            TaskStackBuilder b = TaskStackBuilder.create(this);
            onCreateNavigateUpTaskStack(b);
            onPrepareNavigateUpTaskStack(b);
            b.startActivities();
            finishAffinity();
        } else {
            navigateUpTo(upIntent);
        }
        return true;
    }

    @Deprecated
    public boolean onNavigateUpFromChild(Activity child) {
        return onNavigateUp();
    }

    public void onCreateNavigateUpTaskStack(TaskStackBuilder builder) {
        if (builder != null) {
            builder.addParentStack(this);
        }
    }

    public void onPrepareNavigateUpTaskStack(TaskStackBuilder builder) {}

    public void setVisible(boolean visible) {
        if (content != null) {
            content.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    public void finish() {
        finishing = true;
    }

    @Deprecated
    public void finishFromChild(Activity child) {
        finish();
    }

    public void finishActivity(int requestCode) {}

    @Deprecated
    public void finishActivityFromChild(Activity child, int requestCode) {
        finishActivity(requestCode);
    }

    // The host keeps no persistent Android recents entry. Finishing the root
    // activity closes its host task through the normal runner lifecycle.
    public void finishAndRemoveTask() {
        finish();
    }

    public boolean releaseInstance() {
        return false;
    }

    private java.util.function.BooleanSupplier taskRootQuery;

    public final void shirazSetTaskRootQuery(java.util.function.BooleanSupplier query) {
        taskRootQuery = query;
    }

    public boolean isTaskRoot() {
        return !isDestroyed() && taskRootQuery != null && taskRootQuery.getAsBoolean();
    }

    public int getTaskId() {
        return 1;
    }

    public boolean moveTaskToBack(boolean nonRoot) {
        return false;
    }

    public boolean isLaunchedFromBubble() {
        return false;
    }

    public void finishAffinity() {
        finish();
    }

    public void finishAfterTransition() {
        finish();
    }

    public boolean isFinishing() {
        return finishing;
    }

    public void dump(
            String prefix,
            java.io.FileDescriptor fd,
            java.io.PrintWriter writer,
            String[] args) {
        if (writer != null) {
            writer.print(prefix);
            writer.print("Activity ");
            writer.println(component != null ? component.flattenToShortString() : getClass().getName());
        }
        if (fragments != null) {
            fragments.dump(prefix + "  ", fd, writer, args);
        }
        if (loaderManager != null) {
            loaderManager.dump(prefix + "  ", fd, writer, args);
        }
    }

    private boolean immersive;

    public boolean isImmersive() {
        return immersive;
    }

    public void setImmersive(boolean i) {
        immersive = i;
    }

    public boolean setTranslucent(boolean translucent) {
        return false;
    }

    @Deprecated
    public boolean requestVisibleBehind(boolean visible) {
        return false;
    }

    @Deprecated
    public void onVisibleBehindCanceled() {}

    public void onEnterAnimationComplete() {}

    public void setVrModeEnabled(boolean enabled, android.content.ComponentName requestedComponent)
            throws android.content.pm.PackageManager.NameNotFoundException {}

    public android.view.ActionMode startActionMode(android.view.ActionMode.Callback callback) {
        return onWindowStartingActionMode(callback);
    }

    public android.view.ActionMode startActionMode(android.view.ActionMode.Callback callback, int type) {
        return onWindowStartingActionMode(callback, type);
    }

    public android.view.ActionMode onWindowStartingActionMode(android.view.ActionMode.Callback callback) {
        return null;
    }

    public android.view.ActionMode onWindowStartingActionMode(
            android.view.ActionMode.Callback callback, int type) {
        if (type == android.view.ActionMode.TYPE_PRIMARY) {
            return onWindowStartingActionMode(callback);
        }
        return null;
    }

    public void onActionModeStarted(android.view.ActionMode mode) {}

    public void onActionModeFinished(android.view.ActionMode mode) {}

    public void setEnterSharedElementCallback(SharedElementCallback callback) {}

    public void setExitSharedElementCallback(SharedElementCallback callback) {}

    public void postponeEnterTransition() {}

    public void startPostponedEnterTransition() {}

    public android.view.DragAndDropPermissions requestDragAndDropPermissions(android.view.DragEvent event) {
        return null;
    }

    public void startLockTask() {}

    public void stopLockTask() {}

    public void showLockTaskEscapeMessage() {}

    private android.window.WindowOnBackInvokedDispatcher onBackInvokedDispatcher;

    public android.window.OnBackInvokedDispatcher getOnBackInvokedDispatcher() {
        if (onBackInvokedDispatcher == null) {
            onBackInvokedDispatcher = new android.window.WindowOnBackInvokedDispatcher();
        }
        return onBackInvokedDispatcher;
    }

    private final java.util.IdentityHashMap<ScreenCaptureCallback, java.util.concurrent.Executor>
            screenCaptureCallbacks =
                    new java.util.IdentityHashMap<ScreenCaptureCallback, java.util.concurrent.Executor>();

    public void registerScreenCaptureCallback(
            java.util.concurrent.Executor executor, ScreenCaptureCallback callback) {
        if (executor == null) throw new NullPointerException("executor");
        if (callback == null) throw new NullPointerException("callback");
        synchronized (screenCaptureCallbacks) {
            screenCaptureCallbacks.put(callback, executor);
        }
    }

    public void unregisterScreenCaptureCallback(ScreenCaptureCallback callback) {
        if (callback == null) throw new NullPointerException("callback");
        synchronized (screenCaptureCallbacks) {
            screenCaptureCallbacks.remove(callback);
        }
    }

    // Host adapter access. Applications compiled against the Android SDK cannot call this.
    public final View shirazContentView() {
        return content;
    }
}
