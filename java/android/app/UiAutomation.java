// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.util.SparseArray;
import android.view.InputEvent;
import android.view.Window;
import android.view.WindowAnimationFrameStats;
import android.view.WindowContentFrameStats;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

public final class UiAutomation {
    public static final int ROTATION_UNFREEZE = -2;
    public static final int ROTATION_FREEZE_CURRENT = -1;
    public static final int ROTATION_FREEZE_0 = 0;
    public static final int ROTATION_FREEZE_90 = 1;
    public static final int ROTATION_FREEZE_180 = 2;
    public static final int ROTATION_FREEZE_270 = 3;

    public static final int FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES = 1;
    public static final int FLAG_DONT_USE_ACCESSIBILITY = 2;

    public interface OnAccessibilityEventListener {
        void onAccessibilityEvent(AccessibilityEvent event);
    }

    public interface AccessibilityEventFilter {
        boolean accept(AccessibilityEvent event);
    }

    private OnAccessibilityEventListener mOnAccessibilityEventListener;
    private AccessibilityServiceInfo mServiceInfo;

    UiAutomation() {
    }

    public void setOnAccessibilityEventListener(OnAccessibilityEventListener listener) {
        mOnAccessibilityEventListener = listener;
    }

    public boolean clearCache() {
        return true;
    }

    public void adoptShellPermissionIdentity() {
    }

    public void adoptShellPermissionIdentity(String... permissions) {
    }

    public void dropShellPermissionIdentity() {
    }

    public boolean performGlobalAction(int action) {
        return false;
    }

    public AccessibilityNodeInfo findFocus(int focus) {
        return null;
    }

    public AccessibilityServiceInfo getServiceInfo() {
        return mServiceInfo;
    }

    public void setServiceInfo(AccessibilityServiceInfo info) {
        mServiceInfo = info;
    }

    public List<AccessibilityWindowInfo> getWindows() {
        return Collections.emptyList();
    }

    public SparseArray<List<AccessibilityWindowInfo>> getWindowsOnAllDisplays() {
        return new SparseArray<List<AccessibilityWindowInfo>>();
    }

    public AccessibilityNodeInfo getRootInActiveWindow() {
        return null;
    }

    public boolean injectInputEvent(InputEvent event, boolean sync) {
        return false;
    }

    public void setAnimationScale(float scale) {
    }

    public boolean setRotation(int rotation) {
        return false;
    }

    public AccessibilityEvent executeAndWaitForEvent(Runnable command,
            AccessibilityEventFilter filter, long timeoutMillis) throws TimeoutException {
        if (command != null) {
            command.run();
        }
        throw new TimeoutException("Accessibility event stream is not active");
    }

    public void waitForIdle(long idleTimeoutMillis, long globalTimeoutMillis) throws TimeoutException {
    }

    public Bitmap takeScreenshot() {
        return null;
    }

    public Bitmap takeScreenshot(Window window) {
        return null;
    }

    public void setRunAsMonkey(boolean enable) {
    }

    public boolean clearWindowContentFrameStats(int windowId) {
        return false;
    }

    public WindowContentFrameStats getWindowContentFrameStats(int windowId) {
        return null;
    }

    public void clearWindowAnimationFrameStats() {
    }

    public WindowAnimationFrameStats getWindowAnimationFrameStats() {
        return null;
    }

    public void grantRuntimePermission(String packageName, String permission) {
    }

    public void grantRuntimePermissionAsUser(String packageName, String permission, UserHandle userHandle) {
    }

    public void revokeRuntimePermission(String packageName, String permission) {
    }

    public void revokeRuntimePermissionAsUser(String packageName, String permission, UserHandle userHandle) {
    }

    public ParcelFileDescriptor executeShellCommand(String command) {
        throw new UnsupportedOperationException("Shell execution is disabled in Shiraz");
    }

    public ParcelFileDescriptor[] executeShellCommandRw(String command) {
        throw new UnsupportedOperationException("Shell execution is disabled in Shiraz");
    }

    public ParcelFileDescriptor[] executeShellCommandRwe(String command) {
        throw new UnsupportedOperationException("Shell execution is disabled in Shiraz");
    }

    @Override
    public String toString() {
        return "UiAutomation@" + Integer.toHexString(hashCode());
    }
}
