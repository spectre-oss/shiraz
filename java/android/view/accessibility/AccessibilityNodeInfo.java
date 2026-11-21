// SPDX-License-Identifier: GPL-3.0-only
package android.view.accessibility;

import android.graphics.Rect;
import android.graphics.Region;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Mutable local node snapshot. Service IPC and virtual descendant transport are not implemented. */
public class AccessibilityNodeInfo implements Parcelable {
    public static final int ACTION_FOCUS = 0x00000001;
    public static final int ACTION_CLEAR_FOCUS = 0x00000002;
    public static final int ACTION_SELECT = 0x00000004;
    public static final int ACTION_CLEAR_SELECTION = 0x00000008;
    public static final int ACTION_CLICK = 0x00000010;
    public static final int ACTION_LONG_CLICK = 0x00000020;
    public static final int ACTION_ACCESSIBILITY_FOCUS = 0x00000040;
    public static final int ACTION_CLEAR_ACCESSIBILITY_FOCUS = 0x00000080;
    public static final int ACTION_NEXT_AT_MOVEMENT_GRANULARITY = 0x00000100;
    public static final int ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY = 0x00000200;
    public static final int ACTION_NEXT_HTML_ELEMENT = 0x00000400;
    public static final int ACTION_PREVIOUS_HTML_ELEMENT = 0x00000800;
    public static final int ACTION_SCROLL_FORWARD = 0x00001000;
    public static final int ACTION_SCROLL_BACKWARD = 0x00002000;
    public static final int ACTION_COPY = 0x00004000;
    public static final int ACTION_PASTE = 0x00008000;
    public static final int ACTION_CUT = 0x00010000;
    public static final int ACTION_SET_SELECTION = 0x00020000;
    public static final int ACTION_EXPAND = 0x00040000;
    public static final int ACTION_COLLAPSE = 0x00080000;
    public static final int ACTION_DISMISS = 0x00100000;
    public static final int ACTION_SET_TEXT = 0x00200000;

    public static final int FOCUS_INPUT = 1;
    public static final int FOCUS_ACCESSIBILITY = 2;

    public static final int MOVEMENT_GRANULARITY_CHARACTER = 0x00000001;
    public static final int MOVEMENT_GRANULARITY_WORD = 0x00000002;
    public static final int MOVEMENT_GRANULARITY_LINE = 0x00000004;
    public static final int MOVEMENT_GRANULARITY_PARAGRAPH = 0x00000008;
    public static final int MOVEMENT_GRANULARITY_PAGE = 0x00000010;

    public static final String ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT =
            "ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT";
    public static final String ACTION_ARGUMENT_HTML_ELEMENT_STRING =
            "ACTION_ARGUMENT_HTML_ELEMENT_STRING";
    public static final String ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN =
            "ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN";
    public static final String ACTION_ARGUMENT_SELECTION_START_INT =
            "ACTION_ARGUMENT_SELECTION_START_INT";
    public static final String ACTION_ARGUMENT_SELECTION_END_INT =
            "ACTION_ARGUMENT_SELECTION_END_INT";
    public static final String ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE =
            "ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE";
    public static final String ACTION_ARGUMENT_ROW_INT =
            "android.view.accessibility.action.ARGUMENT_ROW_INT";
    public static final String ACTION_ARGUMENT_COLUMN_INT =
            "android.view.accessibility.action.ARGUMENT_COLUMN_INT";
    public static final String ACTION_ARGUMENT_PROGRESS_VALUE =
            "android.view.accessibility.action.ARGUMENT_PROGRESS_VALUE";
    public static final String ACTION_ARGUMENT_MOVE_WINDOW_X =
            "ACTION_ARGUMENT_MOVE_WINDOW_X";
    public static final String ACTION_ARGUMENT_MOVE_WINDOW_Y =
            "ACTION_ARGUMENT_MOVE_WINDOW_Y";
    public static final String ACTION_ARGUMENT_PRESS_AND_HOLD_DURATION_MILLIS_INT =
            "android.view.accessibility.action.ARGUMENT_PRESS_AND_HOLD_DURATION_MILLIS_INT";
    public static final String ACTION_ARGUMENT_DIRECTION_INT =
            "android.view.accessibility.action.ARGUMENT_DIRECTION_INT";
    public static final String ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT =
            "android.view.accessibility.action.ARGUMENT_SCROLL_AMOUNT_FLOAT";

    public static final String EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY =
            "android.view.accessibility.extra.DATA_TEXT_CHARACTER_LOCATION_KEY";
    public static final String EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX =
            "android.view.accessibility.extra.DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX";
    public static final String EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH =
            "android.view.accessibility.extra.DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH";
    public static final int EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_MAX_LENGTH = 20000;
    public static final String EXTRA_DATA_RENDERING_INFO_KEY =
            "android.view.accessibility.extra.DATA_RENDERING_INFO_KEY";

    public static final int FLAG_PREFETCH_ANCESTORS = 0x00000001;
    public static final int FLAG_PREFETCH_SIBLINGS = 0x00000002;
    public static final int FLAG_PREFETCH_DESCENDANTS_HYBRID = 0x00000004;
    public static final int FLAG_PREFETCH_DESCENDANTS_DEPTH_FIRST = 0x00000008;
    public static final int FLAG_PREFETCH_DESCENDANTS_BREADTH_FIRST = 0x00000010;
    public static final int FLAG_PREFETCH_UNINTERRUPTIBLE = 0x00000020;
    public static final int MAX_NUMBER_OF_PREFETCHED_NODES = 50;

    private View source;
    private View parent;
    private View traversalBefore;
    private View traversalAfter;
    private View labelFor;
    private View labeledBy;
    private boolean recycled;
    private int windowId = -1;
    private AccessibilityWindowInfo windowInfo;
    private final Rect parentBounds = new Rect();
    private final Rect screenBounds = new Rect();
    private final Rect windowBounds = new Rect();
    private final ArrayList<View> children = new ArrayList<View>();
    private final ArrayList<AccessibilityAction> actions = new ArrayList<AccessibilityAction>();
    private ArrayList<String> availableExtraData = new ArrayList<String>();
    private Bundle extras = new Bundle();

    private CharSequence packageName;
    private CharSequence className;
    private CharSequence text;
    private CharSequence contentDescription;
    private CharSequence stateDescription;
    private CharSequence hintText;
    private CharSequence tooltipText;
    private CharSequence error;
    private CharSequence paneTitle;
    private CharSequence containerTitle;
    private String viewIdResourceName;
    private String uniqueId;

    private int liveRegion;
    private int drawingOrder;
    private int movementGranularities;
    private int inputType;
    private int maxTextLength = -1;
    private int textSelectionStart = -1;
    private int textSelectionEnd = -1;
    private long minDurationBetweenContentChangesMillis;

    private boolean checkable;
    private boolean checked;
    private boolean focusable;
    private boolean focused;
    private boolean visibleToUser;
    private boolean accessibilityFocused;
    private boolean selected;
    private boolean clickable;
    private boolean longClickable;
    private boolean contextClickable;
    private boolean enabled;
    private boolean password;
    private boolean scrollable;
    private boolean granularScrollingSupported;
    private boolean textSelectable;
    private boolean requestInitialAccessibilityFocus;
    private boolean editable;
    private boolean accessibilityDataSensitive;
    private boolean multiLine;
    private boolean canOpenPopup;
    private boolean contentInvalid;
    private boolean dismissable;
    private boolean showingHintText;
    private boolean importantForAccessibility;
    private boolean screenReaderFocusable;
    private boolean heading;
    private boolean textEntryKey;

    private CollectionInfo collectionInfo;
    private CollectionItemInfo collectionItemInfo;
    private RangeInfo rangeInfo;
    private ExtraRenderingInfo extraRenderingInfo;
    private TouchDelegateInfo touchDelegateInfo;

    public AccessibilityNodeInfo() {
    }

    public AccessibilityNodeInfo(View source) {
        setSource(source);
    }

    public AccessibilityNodeInfo(View root, int virtualDescendantId) {
        setSource(root, virtualDescendantId);
    }

    public AccessibilityNodeInfo(AccessibilityNodeInfo info) {
        Objects.requireNonNull(info, "info");
        copyFromInternal(info);
    }

    public static AccessibilityNodeInfo obtain() {
        return new AccessibilityNodeInfo();
    }

    public static AccessibilityNodeInfo obtain(View view) {
        AccessibilityNodeInfo info = obtain();
        info.setSource(view);
        return info;
    }

    public static AccessibilityNodeInfo obtain(View view, int id) {
        virtual(id);
        return obtain(view);
    }

    public static AccessibilityNodeInfo obtain(AccessibilityNodeInfo other) {
        other.live();
        AccessibilityNodeInfo copy = obtain();
        copy.copyFromInternal(other);
        return copy;
    }

    private void copyFromInternal(AccessibilityNodeInfo other) {
        other.live();
        source = other.source;
        parent = other.parent;
        traversalBefore = other.traversalBefore;
        traversalAfter = other.traversalAfter;
        labelFor = other.labelFor;
        labeledBy = other.labeledBy;
        windowId = other.windowId;
        windowInfo = other.windowInfo;
        parentBounds.set(other.parentBounds);
        screenBounds.set(other.screenBounds);
        windowBounds.set(other.windowBounds);
        children.clear();
        children.addAll(other.children);
        actions.clear();
        actions.addAll(other.actions);
        availableExtraData = new ArrayList<String>(other.availableExtraData);
        extras = new Bundle(other.extras);
        packageName = other.packageName;
        className = other.className;
        text = other.text;
        contentDescription = other.contentDescription;
        stateDescription = other.stateDescription;
        hintText = other.hintText;
        tooltipText = other.tooltipText;
        error = other.error;
        paneTitle = other.paneTitle;
        containerTitle = other.containerTitle;
        viewIdResourceName = other.viewIdResourceName;
        uniqueId = other.uniqueId;
        liveRegion = other.liveRegion;
        drawingOrder = other.drawingOrder;
        movementGranularities = other.movementGranularities;
        inputType = other.inputType;
        maxTextLength = other.maxTextLength;
        textSelectionStart = other.textSelectionStart;
        textSelectionEnd = other.textSelectionEnd;
        minDurationBetweenContentChangesMillis = other.minDurationBetweenContentChangesMillis;
        checkable = other.checkable;
        checked = other.checked;
        focusable = other.focusable;
        focused = other.focused;
        visibleToUser = other.visibleToUser;
        accessibilityFocused = other.accessibilityFocused;
        selected = other.selected;
        clickable = other.clickable;
        longClickable = other.longClickable;
        contextClickable = other.contextClickable;
        enabled = other.enabled;
        password = other.password;
        scrollable = other.scrollable;
        granularScrollingSupported = other.granularScrollingSupported;
        textSelectable = other.textSelectable;
        requestInitialAccessibilityFocus = other.requestInitialAccessibilityFocus;
        editable = other.editable;
        accessibilityDataSensitive = other.accessibilityDataSensitive;
        multiLine = other.multiLine;
        canOpenPopup = other.canOpenPopup;
        contentInvalid = other.contentInvalid;
        dismissable = other.dismissable;
        showingHintText = other.showingHintText;
        importantForAccessibility = other.importantForAccessibility;
        screenReaderFocusable = other.screenReaderFocusable;
        heading = other.heading;
        textEntryKey = other.textEntryKey;
        collectionInfo = other.collectionInfo;
        collectionItemInfo = other.collectionItemInfo;
        rangeInfo = other.rangeInfo;
        extraRenderingInfo = other.extraRenderingInfo;
        touchDelegateInfo = other.touchDelegateInfo;
    }

    private static void virtual(int id) {
        if (id != -1) {
            throw new UnsupportedOperationException(
                    "Virtual accessibility nodes are not implemented");
        }
    }

    private void live() {
        if (recycled) {
            throw new IllegalStateException("Accessibility node recycled");
        }
    }

    public void recycle() {
        live();
        recycled = true;
        source = null;
        parent = null;
        traversalBefore = null;
        traversalAfter = null;
        labelFor = null;
        labeledBy = null;
        windowInfo = null;
        children.clear();
        actions.clear();
        availableExtraData.clear();
        extras.clear();
        collectionInfo = null;
        collectionItemInfo = null;
        rangeInfo = null;
        extraRenderingInfo = null;
        touchDelegateInfo = null;
    }

    public void setSource(View view) {
        live();
        source = view;
    }

    public void setSource(View view, int id) {
        virtual(id);
        setSource(view);
    }

    public AccessibilityNodeInfo findFocus(int focus) {
        live();
        if (focus == FOCUS_INPUT && focused) {
            return obtain(this);
        }
        if (focus == FOCUS_ACCESSIBILITY && accessibilityFocused) {
            return obtain(this);
        }
        for (int i = 0; i < children.size(); i++) {
            AccessibilityNodeInfo child = children.get(i).createAccessibilityNodeInfo();
            if (child != null) {
                AccessibilityNodeInfo found = child.findFocus(focus);
                child.recycle();
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public AccessibilityNodeInfo focusSearch(int direction) {
        live();
        return null;
    }

    public int getWindowId() {
        live();
        return windowId;
    }

    public boolean refresh() {
        live();
        if (source == null) {
            return false;
        }
        AccessibilityNodeInfo refreshed = source.createAccessibilityNodeInfo();
        if (refreshed == null) {
            return false;
        }
        copyFromInternal(refreshed);
        refreshed.recycle();
        return true;
    }

    public boolean refreshWithExtraData(String extraDataKey, Bundle args) {
        live();
        if (source == null) {
            return false;
        }
        boolean ok = refresh();
        if (ok && extraDataKey != null) {
            AccessibilityNodeProvider provider = source.getAccessibilityNodeProvider();
            if (provider != null) {
                provider.addExtraDataToAccessibilityNodeInfo(
                        AccessibilityNodeProvider.HOST_VIEW_ID, this, extraDataKey, args);
            }
        }
        return ok;
    }

    public void setParent(View view) {
        live();
        parent = view;
    }

    public void setParent(View view, int id) {
        virtual(id);
        setParent(view);
    }

    public AccessibilityNodeInfo getParent() {
        live();
        return parent == null ? null : parent.createAccessibilityNodeInfo();
    }

    public AccessibilityNodeInfo getParent(int prefetchingStrategy) {
        return getParent();
    }

    public void addChild(View view) {
        live();
        if (view == null) {
            throw new NullPointerException("child");
        }
        if (!children.contains(view)) {
            if (children.size() >= 4096) {
                throw new IllegalStateException("Accessibility child limit");
            }
            children.add(view);
        }
    }

    public void addChild(View view, int id) {
        virtual(id);
        addChild(view);
    }

    public boolean removeChild(View view) {
        live();
        return children.remove(view);
    }

    public boolean removeChild(View view, int id) {
        virtual(id);
        return removeChild(view);
    }

    public int getChildCount() {
        live();
        return children.size();
    }

    public AccessibilityNodeInfo getChild(int index) {
        live();
        return children.get(index).createAccessibilityNodeInfo();
    }

    public AccessibilityNodeInfo getChild(int index, int prefetchingStrategy) {
        return getChild(index);
    }

    public void getBoundsInParent(Rect out) {
        live();
        out.set(parentBounds);
    }

    public void setBoundsInParent(Rect value) {
        live();
        parentBounds.set(value);
    }

    public void getBoundsInScreen(Rect out) {
        live();
        out.set(screenBounds);
    }

    public void setBoundsInScreen(Rect value) {
        live();
        screenBounds.set(value);
        windowBounds.set(value);
    }

    public void getBoundsInWindow(Rect outBounds) {
        live();
        outBounds.set(windowBounds);
    }

    public void setBoundsInWindow(Rect bounds) {
        live();
        windowBounds.set(bounds);
    }

    public Bundle getExtras() {
        live();
        return extras;
    }

    public void addAction(int mask) {
        live();
        if ((mask & ~0x3fffff) != 0) {
            throw new IllegalArgumentException("Legacy accessibility action mask");
        }
        for (int bit = 1; bit <= 2097152; bit <<= 1) {
            if ((mask & bit) != 0) {
                addAction(new AccessibilityAction(bit, null));
            }
        }
    }

    public void addAction(AccessibilityAction action) {
        live();
        if (action == null) {
            return;
        }
        actions.remove(action);
        if (actions.size() >= 64) {
            throw new IllegalStateException("Accessibility action limit");
        }
        actions.add(action);
    }

    @Deprecated
    public void removeAction(int action) {
        live();
        if (action != 0) {
            actions.remove(new AccessibilityAction(action, null));
        }
    }

    public boolean removeAction(AccessibilityAction action) {
        live();
        return actions.remove(action);
    }

    public int getActions() {
        live();
        int mask = 0;
        for (int i = 0; i < actions.size(); i++) {
            AccessibilityAction action = actions.get(i);
            if (action.id > 0 && action.id <= 2097152 && (action.id & (action.id - 1)) == 0) {
                mask |= action.id;
            }
        }
        return mask;
    }

    public List<AccessibilityAction> getActionList() {
        live();
        return Collections.unmodifiableList(new ArrayList<AccessibilityAction>(actions));
    }

    public AccessibilityNodeInfo getTraversalBefore() {
        live();
        return traversalBefore == null ? null : traversalBefore.createAccessibilityNodeInfo();
    }

    public void setTraversalBefore(View view) {
        live();
        traversalBefore = view;
    }

    public void setTraversalBefore(View root, int virtualDescendantId) {
        virtual(virtualDescendantId);
        setTraversalBefore(root);
    }

    public AccessibilityNodeInfo getTraversalAfter() {
        live();
        return traversalAfter == null ? null : traversalAfter.createAccessibilityNodeInfo();
    }

    public void setTraversalAfter(View view) {
        live();
        traversalAfter = view;
    }

    public void setTraversalAfter(View root, int virtualDescendantId) {
        virtual(virtualDescendantId);
        setTraversalAfter(root);
    }

    public List<String> getAvailableExtraData() {
        live();
        return Collections.unmodifiableList(new ArrayList<String>(availableExtraData));
    }

    public void setAvailableExtraData(List<String> extraDataKeys) {
        live();
        availableExtraData.clear();
        if (extraDataKeys != null) {
            availableExtraData.addAll(extraDataKeys);
        }
    }

    public void setMinDurationBetweenContentChanges(Duration duration) {
        live();
        minDurationBetweenContentChangesMillis = duration == null ? 0L : duration.toMillis();
    }

    public Duration getMinDurationBetweenContentChanges() {
        live();
        return Duration.ofMillis(minDurationBetweenContentChangesMillis);
    }

    public boolean performAction(int action) {
        return performAction(action, null);
    }

    public boolean performAction(int action, Bundle arguments) {
        live();
        return source != null && source.performAccessibilityAction(action, arguments);
    }

    public List<AccessibilityNodeInfo> findAccessibilityNodeInfosByText(String searched) {
        live();
        if (searched == null || searched.isEmpty()) {
            return Collections.emptyList();
        }
        String needle = searched.toLowerCase(java.util.Locale.ROOT);
        ArrayList<AccessibilityNodeInfo> result = new ArrayList<AccessibilityNodeInfo>();
        collectMatchingText(this, needle, result);
        return result;
    }

    private static void collectMatchingText(
            AccessibilityNodeInfo node, String needle, List<AccessibilityNodeInfo> out) {
        CharSequence t = node.getText();
        CharSequence cd = node.getContentDescription();
        boolean match = (t != null && t.toString().toLowerCase(java.util.Locale.ROOT).contains(needle))
                || (cd != null && cd.toString().toLowerCase(java.util.Locale.ROOT).contains(needle));
        if (match) {
            out.add(obtain(node));
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectMatchingText(child, needle, out);
                child.recycle();
            }
        }
    }

    public List<AccessibilityNodeInfo> findAccessibilityNodeInfosByViewId(String viewId) {
        live();
        if (viewId == null || viewId.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<AccessibilityNodeInfo> result = new ArrayList<AccessibilityNodeInfo>();
        collectMatchingViewId(this, viewId, result);
        return result;
    }

    private static void collectMatchingViewId(
            AccessibilityNodeInfo node, String targetId, List<AccessibilityNodeInfo> out) {
        if (targetId.equals(node.getViewIdResourceName())) {
            out.add(obtain(node));
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectMatchingViewId(child, targetId, out);
                child.recycle();
            }
        }
    }

    public AccessibilityWindowInfo getWindow() {
        live();
        return windowInfo == null ? null : new AccessibilityWindowInfo(windowInfo);
    }

    public CharSequence getPackageName() {
        live();
        return packageName;
    }

    public void setPackageName(CharSequence value) {
        live();
        packageName = value == null ? null : value.toString();
    }

    public CharSequence getClassName() {
        live();
        return className;
    }

    public void setClassName(CharSequence value) {
        live();
        className = value == null ? null : value.toString();
    }

    public CharSequence getText() {
        live();
        return text;
    }

    public void setText(CharSequence value) {
        live();
        text = value == null ? null : value.toString();
    }

    public CharSequence getContentDescription() {
        live();
        return contentDescription;
    }

    public void setContentDescription(CharSequence value) {
        live();
        contentDescription = value == null ? null : value.toString();
    }

    public CharSequence getStateDescription() {
        live();
        return stateDescription;
    }

    public void setStateDescription(CharSequence value) {
        live();
        stateDescription = value == null ? null : value.toString();
    }

    public CharSequence getHintText() {
        live();
        return hintText;
    }

    public void setHintText(CharSequence value) {
        live();
        hintText = value == null ? null : value.toString();
    }

    public CharSequence getTooltipText() {
        live();
        return tooltipText;
    }

    public void setTooltipText(CharSequence value) {
        live();
        tooltipText = value == null ? null : value.toString();
    }

    public CharSequence getError() {
        live();
        return error;
    }

    public void setError(CharSequence value) {
        live();
        error = value == null ? null : value.toString();
    }

    public CharSequence getPaneTitle() {
        live();
        return paneTitle;
    }

    public void setPaneTitle(CharSequence value) {
        live();
        paneTitle = value == null ? null : value.toString();
    }

    public CharSequence getContainerTitle() {
        live();
        return containerTitle;
    }

    public void setContainerTitle(CharSequence value) {
        live();
        containerTitle = value == null ? null : value.toString();
    }

    public void setLabelFor(View labeled) {
        live();
        labelFor = labeled;
    }

    public void setLabelFor(View root, int virtualDescendantId) {
        virtual(virtualDescendantId);
        setLabelFor(root);
    }

    public AccessibilityNodeInfo getLabelFor() {
        live();
        return labelFor == null ? null : labelFor.createAccessibilityNodeInfo();
    }

    public void setLabeledBy(View label) {
        live();
        labeledBy = label;
    }

    public void setLabeledBy(View root, int virtualDescendantId) {
        virtual(virtualDescendantId);
        setLabeledBy(root);
    }

    public AccessibilityNodeInfo getLabeledBy() {
        live();
        return labeledBy == null ? null : labeledBy.createAccessibilityNodeInfo();
    }

    public int getLiveRegion() {
        live();
        return liveRegion;
    }

    public void setLiveRegion(int value) {
        live();
        if (value < 0 || value > 2) {
            throw new IllegalArgumentException("Accessibility live region");
        }
        liveRegion = value;
    }

    public int getDrawingOrder() {
        live();
        return drawingOrder;
    }

    public void setDrawingOrder(int drawingOrderInParent) {
        live();
        drawingOrder = drawingOrderInParent;
    }

    public String getViewIdResourceName() {
        live();
        return viewIdResourceName;
    }

    public void setViewIdResourceName(String value) {
        live();
        viewIdResourceName = value;
    }

    public String getUniqueId() {
        live();
        return uniqueId;
    }

    public void setUniqueId(String value) {
        live();
        uniqueId = value;
    }

    public void setQueryFromAppProcessEnabled(View view, boolean enabled) {
        live();
        Objects.requireNonNull(view, "view");
    }

    public boolean isCheckable() {
        live();
        return checkable;
    }

    public void setCheckable(boolean value) {
        live();
        checkable = value;
    }

    public boolean isChecked() {
        live();
        return checked;
    }

    public void setChecked(boolean value) {
        live();
        checked = value;
    }

    public boolean isFocusable() {
        live();
        return focusable;
    }

    public void setFocusable(boolean value) {
        live();
        focusable = value;
    }

    public boolean isFocused() {
        live();
        return focused;
    }

    public void setFocused(boolean value) {
        live();
        focused = value;
    }

    public boolean isVisibleToUser() {
        live();
        return visibleToUser;
    }

    public void setVisibleToUser(boolean value) {
        live();
        visibleToUser = value;
    }

    public boolean isAccessibilityFocused() {
        live();
        return accessibilityFocused;
    }

    public void setAccessibilityFocused(boolean value) {
        live();
        accessibilityFocused = value;
    }

    public boolean isSelected() {
        live();
        return selected;
    }

    public void setSelected(boolean value) {
        live();
        selected = value;
    }

    public boolean isClickable() {
        live();
        return clickable;
    }

    public void setClickable(boolean value) {
        live();
        clickable = value;
    }

    public boolean isLongClickable() {
        live();
        return longClickable;
    }

    public void setLongClickable(boolean value) {
        live();
        longClickable = value;
    }

    public boolean isContextClickable() {
        live();
        return contextClickable;
    }

    public void setContextClickable(boolean value) {
        live();
        contextClickable = value;
    }

    public boolean isEnabled() {
        live();
        return enabled;
    }

    public void setEnabled(boolean value) {
        live();
        enabled = value;
    }

    public boolean isPassword() {
        live();
        return password;
    }

    public void setPassword(boolean value) {
        live();
        password = value;
    }

    public boolean isScrollable() {
        live();
        return scrollable;
    }

    public void setScrollable(boolean value) {
        live();
        scrollable = value;
    }

    public boolean isGranularScrollingSupported() {
        live();
        return granularScrollingSupported;
    }

    public void setGranularScrollingSupported(boolean value) {
        live();
        granularScrollingSupported = value;
    }

    public boolean isTextSelectable() {
        live();
        return textSelectable;
    }

    public void setTextSelectable(boolean value) {
        live();
        textSelectable = value;
    }

    public boolean hasRequestInitialAccessibilityFocus() {
        live();
        return requestInitialAccessibilityFocus;
    }

    public void setRequestInitialAccessibilityFocus(boolean value) {
        live();
        requestInitialAccessibilityFocus = value;
    }

    public boolean isEditable() {
        live();
        return editable;
    }

    public void setEditable(boolean value) {
        live();
        editable = value;
    }

    public boolean isAccessibilityDataSensitive() {
        live();
        return accessibilityDataSensitive;
    }

    public void setAccessibilityDataSensitive(boolean value) {
        live();
        accessibilityDataSensitive = value;
    }

    public boolean isMultiLine() {
        live();
        return multiLine;
    }

    public void setMultiLine(boolean value) {
        live();
        multiLine = value;
    }

    public boolean canOpenPopup() {
        live();
        return canOpenPopup;
    }

    public void setCanOpenPopup(boolean value) {
        live();
        canOpenPopup = value;
    }

    public boolean isContentInvalid() {
        live();
        return contentInvalid;
    }

    public void setContentInvalid(boolean value) {
        live();
        contentInvalid = value;
    }

    public boolean isDismissable() {
        live();
        return dismissable;
    }

    public void setDismissable(boolean value) {
        live();
        dismissable = value;
    }

    public boolean isShowingHintText() {
        live();
        return showingHintText;
    }

    public void setShowingHintText(boolean value) {
        live();
        showingHintText = value;
    }

    public boolean isImportantForAccessibility() {
        live();
        return importantForAccessibility;
    }

    public void setImportantForAccessibility(boolean value) {
        live();
        importantForAccessibility = value;
    }

    public boolean isScreenReaderFocusable() {
        live();
        return screenReaderFocusable;
    }

    public void setScreenReaderFocusable(boolean value) {
        live();
        screenReaderFocusable = value;
    }

    public boolean isHeading() {
        live();
        return heading;
    }

    public void setHeading(boolean value) {
        live();
        heading = value;
    }

    public boolean isTextEntryKey() {
        live();
        return textEntryKey;
    }

    public void setTextEntryKey(boolean value) {
        live();
        textEntryKey = value;
    }

    public int getMovementGranularities() {
        live();
        return movementGranularities;
    }

    public void setMovementGranularities(int value) {
        live();
        movementGranularities = value;
    }

    public int getInputType() {
        live();
        return inputType;
    }

    public void setInputType(int value) {
        live();
        inputType = value;
    }

    public int getMaxTextLength() {
        live();
        return maxTextLength;
    }

    public void setMaxTextLength(int value) {
        live();
        maxTextLength = value;
    }

    public int getTextSelectionStart() {
        live();
        return textSelectionStart;
    }

    public int getTextSelectionEnd() {
        live();
        return textSelectionEnd;
    }

    public void setTextSelection(int start, int end) {
        live();
        textSelectionStart = start;
        textSelectionEnd = end;
    }

    public CollectionInfo getCollectionInfo() {
        live();
        return collectionInfo;
    }

    public void setCollectionInfo(CollectionInfo collectionInfo) {
        live();
        this.collectionInfo = collectionInfo;
    }

    public CollectionItemInfo getCollectionItemInfo() {
        live();
        return collectionItemInfo;
    }

    public void setCollectionItemInfo(CollectionItemInfo collectionItemInfo) {
        live();
        this.collectionItemInfo = collectionItemInfo;
    }

    public RangeInfo getRangeInfo() {
        live();
        return rangeInfo;
    }

    public void setRangeInfo(RangeInfo rangeInfo) {
        live();
        this.rangeInfo = rangeInfo;
    }

    public ExtraRenderingInfo getExtraRenderingInfo() {
        live();
        return extraRenderingInfo;
    }

    void setExtraRenderingInfo(ExtraRenderingInfo info) {
        live();
        extraRenderingInfo = info;
    }

    public TouchDelegateInfo getTouchDelegateInfo() {
        live();
        return touchDelegateInfo;
    }

    public void setTouchDelegateInfo(TouchDelegateInfo delegatedInfo) {
        live();
        touchDelegateInfo = delegatedInfo;
    }

    @Override
    public int describeContents() {
        live();
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        live();
        Objects.requireNonNull(out, "out");
        out.writeInt(windowId);
        out.writeInt(parentBounds.left);
        out.writeInt(parentBounds.top);
        out.writeInt(parentBounds.right);
        out.writeInt(parentBounds.bottom);
        out.writeInt(screenBounds.left);
        out.writeInt(screenBounds.top);
        out.writeInt(screenBounds.right);
        out.writeInt(screenBounds.bottom);
        out.writeInt(windowBounds.left);
        out.writeInt(windowBounds.top);
        out.writeInt(windowBounds.right);
        out.writeInt(windowBounds.bottom);
        out.writeString(packageName == null ? null : packageName.toString());
        out.writeString(className == null ? null : className.toString());
        out.writeString(text == null ? null : text.toString());
        out.writeString(contentDescription == null ? null : contentDescription.toString());
        out.writeString(stateDescription == null ? null : stateDescription.toString());
        out.writeString(hintText == null ? null : hintText.toString());
        out.writeString(tooltipText == null ? null : tooltipText.toString());
        out.writeString(error == null ? null : error.toString());
        out.writeString(paneTitle == null ? null : paneTitle.toString());
        out.writeString(containerTitle == null ? null : containerTitle.toString());
        out.writeString(viewIdResourceName);
        out.writeString(uniqueId);
        out.writeInt(liveRegion);
        out.writeInt(drawingOrder);
        out.writeInt(movementGranularities);
        out.writeInt(inputType);
        out.writeInt(maxTextLength);
        out.writeInt(textSelectionStart);
        out.writeInt(textSelectionEnd);
        out.writeLong(minDurationBetweenContentChangesMillis);
        long b = 0L;
        if (checkable) b |= 1L << 0;
        if (checked) b |= 1L << 1;
        if (focusable) b |= 1L << 2;
        if (focused) b |= 1L << 3;
        if (visibleToUser) b |= 1L << 4;
        if (accessibilityFocused) b |= 1L << 5;
        if (selected) b |= 1L << 6;
        if (clickable) b |= 1L << 7;
        if (longClickable) b |= 1L << 8;
        if (contextClickable) b |= 1L << 9;
        if (enabled) b |= 1L << 10;
        if (password) b |= 1L << 11;
        if (scrollable) b |= 1L << 12;
        if (granularScrollingSupported) b |= 1L << 13;
        if (textSelectable) b |= 1L << 14;
        if (requestInitialAccessibilityFocus) b |= 1L << 15;
        if (editable) b |= 1L << 16;
        if (accessibilityDataSensitive) b |= 1L << 17;
        if (multiLine) b |= 1L << 18;
        if (canOpenPopup) b |= 1L << 19;
        if (contentInvalid) b |= 1L << 20;
        if (dismissable) b |= 1L << 21;
        if (showingHintText) b |= 1L << 22;
        if (importantForAccessibility) b |= 1L << 23;
        if (screenReaderFocusable) b |= 1L << 24;
        if (heading) b |= 1L << 25;
        if (textEntryKey) b |= 1L << 26;
        out.writeLong(b);
        out.writeInt(actions.size());
        for (int i = 0; i < actions.size(); i++) {
            actions.get(i).writeToParcel(out, flags);
        }
        out.writeBundle(extras);
        if (collectionInfo != null) {
            out.writeInt(1);
            out.writeInt(collectionInfo.getRowCount());
            out.writeInt(collectionInfo.getColumnCount());
            out.writeInt(collectionInfo.isHierarchical() ? 1 : 0);
            out.writeInt(collectionInfo.getSelectionMode());
            out.writeInt(collectionInfo.getItemCount());
            out.writeInt(collectionInfo.getImportantForAccessibilityItemCount());
        } else {
            out.writeInt(0);
        }
        if (collectionItemInfo != null) {
            out.writeInt(1);
            out.writeInt(collectionItemInfo.getRowIndex());
            out.writeInt(collectionItemInfo.getRowSpan());
            out.writeInt(collectionItemInfo.getColumnIndex());
            out.writeInt(collectionItemInfo.getColumnSpan());
            out.writeInt(collectionItemInfo.isHeading() ? 1 : 0);
            out.writeInt(collectionItemInfo.isSelected() ? 1 : 0);
            out.writeString(collectionItemInfo.getRowTitle());
            out.writeString(collectionItemInfo.getColumnTitle());
        } else {
            out.writeInt(0);
        }
        if (rangeInfo != null) {
            out.writeInt(1);
            out.writeInt(rangeInfo.getType());
            out.writeFloat(rangeInfo.getMin());
            out.writeFloat(rangeInfo.getMax());
            out.writeFloat(rangeInfo.getCurrent());
        } else {
            out.writeInt(0);
        }
        if (touchDelegateInfo != null) {
            out.writeInt(1);
            touchDelegateInfo.writeToParcel(out, flags);
        } else {
            out.writeInt(0);
        }
    }

    private void initFromParcel(Parcel in) {
        windowId = in.readInt();
        parentBounds.set(in.readInt(), in.readInt(), in.readInt(), in.readInt());
        screenBounds.set(in.readInt(), in.readInt(), in.readInt(), in.readInt());
        windowBounds.set(in.readInt(), in.readInt(), in.readInt(), in.readInt());
        packageName = in.readString();
        className = in.readString();
        text = in.readString();
        contentDescription = in.readString();
        stateDescription = in.readString();
        hintText = in.readString();
        tooltipText = in.readString();
        error = in.readString();
        paneTitle = in.readString();
        containerTitle = in.readString();
        viewIdResourceName = in.readString();
        uniqueId = in.readString();
        liveRegion = in.readInt();
        drawingOrder = in.readInt();
        movementGranularities = in.readInt();
        inputType = in.readInt();
        maxTextLength = in.readInt();
        textSelectionStart = in.readInt();
        textSelectionEnd = in.readInt();
        minDurationBetweenContentChangesMillis = in.readLong();
        long b = in.readLong();
        checkable = (b & (1L << 0)) != 0;
        checked = (b & (1L << 1)) != 0;
        focusable = (b & (1L << 2)) != 0;
        focused = (b & (1L << 3)) != 0;
        visibleToUser = (b & (1L << 4)) != 0;
        accessibilityFocused = (b & (1L << 5)) != 0;
        selected = (b & (1L << 6)) != 0;
        clickable = (b & (1L << 7)) != 0;
        longClickable = (b & (1L << 8)) != 0;
        contextClickable = (b & (1L << 9)) != 0;
        enabled = (b & (1L << 10)) != 0;
        password = (b & (1L << 11)) != 0;
        scrollable = (b & (1L << 12)) != 0;
        granularScrollingSupported = (b & (1L << 13)) != 0;
        textSelectable = (b & (1L << 14)) != 0;
        requestInitialAccessibilityFocus = (b & (1L << 15)) != 0;
        editable = (b & (1L << 16)) != 0;
        accessibilityDataSensitive = (b & (1L << 17)) != 0;
        multiLine = (b & (1L << 18)) != 0;
        canOpenPopup = (b & (1L << 19)) != 0;
        contentInvalid = (b & (1L << 20)) != 0;
        dismissable = (b & (1L << 21)) != 0;
        showingHintText = (b & (1L << 22)) != 0;
        importantForAccessibility = (b & (1L << 23)) != 0;
        screenReaderFocusable = (b & (1L << 24)) != 0;
        heading = (b & (1L << 25)) != 0;
        textEntryKey = (b & (1L << 26)) != 0;
        int actionCount = in.readInt();
        actions.clear();
        for (int i = 0; i < actionCount; i++) {
            actions.add(AccessibilityAction.CREATOR.createFromParcel(in));
        }
        Bundle readExtras = in.readBundle(null);
        extras = readExtras == null ? new Bundle() : readExtras;
        if (in.readInt() != 0) {
            collectionInfo = new CollectionInfo(
                    in.readInt(),
                    in.readInt(),
                    in.readInt() != 0,
                    in.readInt(),
                    in.readInt(),
                    in.readInt());
        } else {
            collectionInfo = null;
        }
        if (in.readInt() != 0) {
            collectionItemInfo = new CollectionItemInfo.Builder()
                    .setRowIndex(in.readInt())
                    .setRowSpan(in.readInt())
                    .setColumnIndex(in.readInt())
                    .setColumnSpan(in.readInt())
                    .setHeading(in.readInt() != 0)
                    .setSelected(in.readInt() != 0)
                    .setRowTitle(in.readString())
                    .setColumnTitle(in.readString())
                    .build();
        } else {
            collectionItemInfo = null;
        }
        if (in.readInt() != 0) {
            rangeInfo = new RangeInfo(
                    in.readInt(), in.readFloat(), in.readFloat(), in.readFloat());
        } else {
            rangeInfo = null;
        }
        if (in.readInt() != 0) {
            touchDelegateInfo = TouchDelegateInfo.CREATOR.createFromParcel(in);
        } else {
            touchDelegateInfo = null;
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        AccessibilityNodeInfo other = (AccessibilityNodeInfo) object;
        return source == other.source
                && windowId == other.windowId
                && Objects.equals(uniqueId, other.uniqueId)
                && Objects.equals(viewIdResourceName, other.viewIdResourceName);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + (source == null ? 0 : System.identityHashCode(source));
        result = 31 * result + windowId;
        result = 31 * result + (uniqueId == null ? 0 : uniqueId.hashCode());
        return result;
    }

    @Override
    public String toString() {
        live();
        return "AccessibilityNodeInfo[className=" + className
                + ", text=" + text
                + ", contentDescription=" + contentDescription
                + ", viewIdResName=" + viewIdResourceName
                + ", boundsInScreen=" + screenBounds
                + ", enabled=" + enabled
                + ", clickable=" + clickable
                + ", actions=" + actions + "]";
    }

    public static final Parcelable.Creator<AccessibilityNodeInfo> CREATOR =
            new Parcelable.Creator<AccessibilityNodeInfo>() {
                @Override
                public AccessibilityNodeInfo createFromParcel(Parcel in) {
                    AccessibilityNodeInfo info = new AccessibilityNodeInfo();
                    info.initFromParcel(in);
                    return info;
                }

                @Override
                public AccessibilityNodeInfo[] newArray(int size) {
                    return new AccessibilityNodeInfo[size];
                }
            };

    public static final class AccessibilityAction implements Parcelable {
        public static final AccessibilityAction ACTION_FOCUS =
                new AccessibilityAction(AccessibilityNodeInfo.ACTION_FOCUS, null);
        public static final AccessibilityAction ACTION_CLEAR_FOCUS =
                new AccessibilityAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS, null);
        public static final AccessibilityAction ACTION_SELECT =
                new AccessibilityAction(AccessibilityNodeInfo.ACTION_SELECT, null);
        public static final AccessibilityAction ACTION_CLEAR_SELECTION =
                new AccessibilityAction(AccessibilityNodeInfo.ACTION_CLEAR_SELECTION, null);
        public static final AccessibilityAction ACTION_CLICK =
                new AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null);
        public static final AccessibilityAction ACTION_LONG_CLICK =
                new AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null);
        public static final AccessibilityAction ACTION_ACCESSIBILITY_FOCUS =
                new AccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
        public static final AccessibilityAction ACTION_CLEAR_ACCESSIBILITY_FOCUS =
                new AccessibilityAction(
                        AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null);
        public static final AccessibilityAction ACTION_NEXT_AT_MOVEMENT_GRANULARITY =
                new AccessibilityAction(
                        AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY, null);
        public static final AccessibilityAction ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY =
                new AccessibilityAction(
                        AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY, null);
        public static final AccessibilityAction ACTION_NEXT_HTML_ELEMENT =
                new AccessibilityAction(AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT, null);
        public static final AccessibilityAction ACTION_PREVIOUS_HTML_ELEMENT =
                new AccessibilityAction(AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT, null);
        public static final AccessibilityAction ACTION_SCROLL_FORWARD =
                new AccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null);
        public static final AccessibilityAction ACTION_SCROLL_BACKWARD =
                new AccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null);
        public static final AccessibilityAction ACTION_COPY =
                new AccessibilityAction(AccessibilityNodeInfo.ACTION_COPY, null);
        public static final AccessibilityAction ACTION_PASTE =
                new AccessibilityAction(AccessibilityNodeInfo.ACTION_PASTE, null);
        public static final AccessibilityAction ACTION_CUT =
                new AccessibilityAction(AccessibilityNodeInfo.ACTION_CUT, null);
        public static final AccessibilityAction ACTION_SET_SELECTION =
                new AccessibilityAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, null);
        public static final AccessibilityAction ACTION_EXPAND =
                new AccessibilityAction(AccessibilityNodeInfo.ACTION_EXPAND, null);
        public static final AccessibilityAction ACTION_COLLAPSE =
                new AccessibilityAction(AccessibilityNodeInfo.ACTION_COLLAPSE, null);
        public static final AccessibilityAction ACTION_DISMISS =
                new AccessibilityAction(AccessibilityNodeInfo.ACTION_DISMISS, null);
        public static final AccessibilityAction ACTION_SET_TEXT =
                new AccessibilityAction(AccessibilityNodeInfo.ACTION_SET_TEXT, null);
        public static final AccessibilityAction ACTION_SHOW_ON_SCREEN =
                new AccessibilityAction(16908342, null);
        public static final AccessibilityAction ACTION_SCROLL_TO_POSITION =
                new AccessibilityAction(16908343, null);
        public static final AccessibilityAction ACTION_SCROLL_UP =
                new AccessibilityAction(16908344, null);
        public static final AccessibilityAction ACTION_SCROLL_LEFT =
                new AccessibilityAction(16908345, null);
        public static final AccessibilityAction ACTION_SCROLL_DOWN =
                new AccessibilityAction(16908346, null);
        public static final AccessibilityAction ACTION_SCROLL_RIGHT =
                new AccessibilityAction(16908347, null);
        public static final AccessibilityAction ACTION_CONTEXT_CLICK =
                new AccessibilityAction(16908348, null);
        public static final AccessibilityAction ACTION_SET_PROGRESS =
                new AccessibilityAction(16908349, null);
        public static final AccessibilityAction ACTION_MOVE_WINDOW =
                new AccessibilityAction(16908354, null);
        public static final AccessibilityAction ACTION_SHOW_TOOLTIP =
                new AccessibilityAction(16908356, null);
        public static final AccessibilityAction ACTION_HIDE_TOOLTIP =
                new AccessibilityAction(16908357, null);
        public static final AccessibilityAction ACTION_PAGE_UP =
                new AccessibilityAction(16908358, null);
        public static final AccessibilityAction ACTION_PAGE_DOWN =
                new AccessibilityAction(16908359, null);
        public static final AccessibilityAction ACTION_PAGE_LEFT =
                new AccessibilityAction(16908360, null);
        public static final AccessibilityAction ACTION_PAGE_RIGHT =
                new AccessibilityAction(16908361, null);
        public static final AccessibilityAction ACTION_PRESS_AND_HOLD =
                new AccessibilityAction(16908362, null);
        public static final AccessibilityAction ACTION_IME_ENTER =
                new AccessibilityAction(16908372, null);
        public static final AccessibilityAction ACTION_DRAG_START =
                new AccessibilityAction(16908373, null);
        public static final AccessibilityAction ACTION_DRAG_DROP =
                new AccessibilityAction(16908374, null);
        public static final AccessibilityAction ACTION_DRAG_CANCEL =
                new AccessibilityAction(16908375, null);
        public static final AccessibilityAction ACTION_SHOW_TEXT_SUGGESTIONS =
                new AccessibilityAction(16908376, null);
        public static final AccessibilityAction ACTION_SCROLL_IN_DIRECTION =
                new AccessibilityAction(16908382, null);

        final int id;
        private final CharSequence label;

        public AccessibilityAction(int actionId, CharSequence label) {
            if (actionId == 0) {
                throw new IllegalArgumentException("Action ID");
            }
            this.id = actionId;
            this.label = label == null ? null : label.toString();
        }

        public int getId() {
            return id;
        }

        public CharSequence getLabel() {
            return label;
        }

        @Override
        public int hashCode() {
            return id;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof AccessibilityAction
                    && ((AccessibilityAction) other).id == id;
        }

        @Override
        public String toString() {
            return "AccessibilityAction: " + Integer.toHexString(id) + " - " + label;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            Objects.requireNonNull(out, "out");
            out.writeInt(id);
            out.writeString(label == null ? null : label.toString());
        }

        public static final Parcelable.Creator<AccessibilityAction> CREATOR =
                new Parcelable.Creator<AccessibilityAction>() {
                    @Override
                    public AccessibilityAction createFromParcel(Parcel in) {
                        int actionId = in.readInt();
                        String lbl = in.readString();
                        return new AccessibilityAction(actionId, lbl);
                    }

                    @Override
                    public AccessibilityAction[] newArray(int size) {
                        return new AccessibilityAction[size];
                    }
                };
    }

    public static final class CollectionInfo {
        public static final int SELECTION_MODE_NONE = 0;
        public static final int SELECTION_MODE_SINGLE = 1;
        public static final int SELECTION_MODE_MULTIPLE = 2;
        public static final int UNDEFINED = -1;

        private final int mRowCount;
        private final int mColumnCount;
        private final boolean mHierarchical;
        private final int mSelectionMode;
        private final int mItemCount;
        private final int mImportantForAccessibilityItemCount;

        public CollectionInfo(int rowCount, int columnCount, boolean hierarchical) {
            this(rowCount, columnCount, hierarchical, SELECTION_MODE_NONE);
        }

        public CollectionInfo(
                int rowCount, int columnCount, boolean hierarchical, int selectionMode) {
            this(rowCount, columnCount, hierarchical, selectionMode, UNDEFINED, UNDEFINED);
        }

        private CollectionInfo(
                int rowCount,
                int columnCount,
                boolean hierarchical,
                int selectionMode,
                int itemCount,
                int importantForAccessibilityItemCount) {
            mRowCount = rowCount;
            mColumnCount = columnCount;
            mHierarchical = hierarchical;
            mSelectionMode = selectionMode;
            mItemCount = itemCount;
            mImportantForAccessibilityItemCount = importantForAccessibilityItemCount;
        }

        public static CollectionInfo obtain(
                int rowCount, int columnCount, boolean hierarchical) {
            return new CollectionInfo(rowCount, columnCount, hierarchical);
        }

        public static CollectionInfo obtain(
                int rowCount, int columnCount, boolean hierarchical, int selectionMode) {
            return new CollectionInfo(rowCount, columnCount, hierarchical, selectionMode);
        }

        public int getRowCount() {
            return mRowCount;
        }

        public int getColumnCount() {
            return mColumnCount;
        }

        public boolean isHierarchical() {
            return mHierarchical;
        }

        public int getSelectionMode() {
            return mSelectionMode;
        }

        public int getItemCount() {
            return mItemCount;
        }

        public int getImportantForAccessibilityItemCount() {
            return mImportantForAccessibilityItemCount;
        }

        public static final class Builder {
            private int mRowCount = UNDEFINED;
            private int mColumnCount = UNDEFINED;
            private boolean mHierarchical;
            private int mSelectionMode = SELECTION_MODE_NONE;
            private int mItemCount = UNDEFINED;
            private int mImportantForAccessibilityItemCount = UNDEFINED;

            public Builder() {
            }

            public Builder setRowCount(int rowCount) {
                mRowCount = rowCount;
                return this;
            }

            public Builder setColumnCount(int columnCount) {
                mColumnCount = columnCount;
                return this;
            }

            public Builder setHierarchical(boolean hierarchical) {
                mHierarchical = hierarchical;
                return this;
            }

            public Builder setSelectionMode(int selectionMode) {
                mSelectionMode = selectionMode;
                return this;
            }

            public Builder setItemCount(int itemCount) {
                mItemCount = itemCount;
                return this;
            }

            public Builder setImportantForAccessibilityItemCount(
                    int importantForAccessibilityItemCount) {
                mImportantForAccessibilityItemCount = importantForAccessibilityItemCount;
                return this;
            }

            public CollectionInfo build() {
                return new CollectionInfo(
                        mRowCount,
                        mColumnCount,
                        mHierarchical,
                        mSelectionMode,
                        mItemCount,
                        mImportantForAccessibilityItemCount);
            }
        }
    }

    public static final class CollectionItemInfo {
        private final boolean mHeading;
        private final int mColumnIndex;
        private final int mRowIndex;
        private final int mColumnSpan;
        private final int mRowSpan;
        private final boolean mSelected;
        private final String mRowTitle;
        private final String mColumnTitle;

        public CollectionItemInfo(
                int rowIndex, int rowSpan, int columnIndex, int columnSpan, boolean heading) {
            this(rowIndex, rowSpan, columnIndex, columnSpan, heading, false);
        }

        public CollectionItemInfo(
                int rowIndex,
                int rowSpan,
                int columnIndex,
                int columnSpan,
                boolean heading,
                boolean selected) {
            this(rowIndex, rowSpan, columnIndex, columnSpan, heading, selected, null, null);
        }

        private CollectionItemInfo(
                int rowIndex,
                int rowSpan,
                int columnIndex,
                int columnSpan,
                boolean heading,
                boolean selected,
                String rowTitle,
                String columnTitle) {
            mRowIndex = rowIndex;
            mRowSpan = rowSpan;
            mColumnIndex = columnIndex;
            mColumnSpan = columnSpan;
            mHeading = heading;
            mSelected = selected;
            mRowTitle = rowTitle;
            mColumnTitle = columnTitle;
        }

        public static CollectionItemInfo obtain(
                int rowIndex, int rowSpan, int columnIndex, int columnSpan, boolean heading) {
            return new CollectionItemInfo(rowIndex, rowSpan, columnIndex, columnSpan, heading);
        }

        public static CollectionItemInfo obtain(
                int rowIndex,
                int rowSpan,
                int columnIndex,
                int columnSpan,
                boolean heading,
                boolean selected) {
            return new CollectionItemInfo(
                    rowIndex, rowSpan, columnIndex, columnSpan, heading, selected);
        }

        public int getColumnIndex() {
            return mColumnIndex;
        }

        public int getRowIndex() {
            return mRowIndex;
        }

        public int getColumnSpan() {
            return mColumnSpan;
        }

        public int getRowSpan() {
            return mRowSpan;
        }

        public boolean isHeading() {
            return mHeading;
        }

        public boolean isSelected() {
            return mSelected;
        }

        public String getRowTitle() {
            return mRowTitle;
        }

        public String getColumnTitle() {
            return mColumnTitle;
        }

        public static final class Builder {
            private boolean mHeading;
            private int mColumnIndex;
            private int mRowIndex;
            private int mColumnSpan = 1;
            private int mRowSpan = 1;
            private boolean mSelected;
            private String mRowTitle;
            private String mColumnTitle;

            public Builder() {
            }

            public Builder setHeading(boolean heading) {
                mHeading = heading;
                return this;
            }

            public Builder setColumnIndex(int columnIndex) {
                mColumnIndex = columnIndex;
                return this;
            }

            public Builder setRowIndex(int rowIndex) {
                mRowIndex = rowIndex;
                return this;
            }

            public Builder setColumnSpan(int columnSpan) {
                mColumnSpan = columnSpan;
                return this;
            }

            public Builder setRowSpan(int rowSpan) {
                mRowSpan = rowSpan;
                return this;
            }

            public Builder setSelected(boolean selected) {
                mSelected = selected;
                return this;
            }

            public Builder setRowTitle(String rowTitle) {
                mRowTitle = rowTitle;
                return this;
            }

            public Builder setColumnTitle(String columnTitle) {
                mColumnTitle = columnTitle;
                return this;
            }

            public CollectionItemInfo build() {
                return new CollectionItemInfo(
                        mRowIndex,
                        mRowSpan,
                        mColumnIndex,
                        mColumnSpan,
                        mHeading,
                        mSelected,
                        mRowTitle,
                        mColumnTitle);
            }
        }
    }

    public static final class RangeInfo {
        public static final int RANGE_TYPE_INT = 0;
        public static final int RANGE_TYPE_FLOAT = 1;
        public static final int RANGE_TYPE_PERCENT = 2;

        private final int mType;
        private final float mMin;
        private final float mMax;
        private final float mCurrent;

        public RangeInfo(int type, float min, float max, float current) {
            mType = type;
            mMin = min;
            mMax = max;
            mCurrent = current;
        }

        public static RangeInfo obtain(int type, float min, float max, float current) {
            return new RangeInfo(type, min, max, current);
        }

        public int getType() {
            return mType;
        }

        public float getMin() {
            return mMin;
        }

        public float getMax() {
            return mMax;
        }

        public float getCurrent() {
            return mCurrent;
        }
    }

    public static final class ExtraRenderingInfo {
        private final Size mLayoutSize;
        private final float mTextSizeInPx;
        private final int mTextSizeUnit;

        ExtraRenderingInfo(Size layoutSize, float textSizeInPx, int textSizeUnit) {
            mLayoutSize = layoutSize;
            mTextSizeInPx = textSizeInPx;
            mTextSizeUnit = textSizeUnit;
        }

        public Size getLayoutSize() {
            return mLayoutSize;
        }

        public float getTextSizeInPx() {
            return mTextSizeInPx;
        }

        public int getTextSizeUnit() {
            return mTextSizeUnit >= 0 ? mTextSizeUnit : TypedValue.COMPLEX_UNIT_PX;
        }
    }

    public static final class TouchDelegateInfo implements Parcelable {
        private final ArrayList<Region> mRegions = new ArrayList<Region>();
        private final LinkedHashMap<Region, View> mTargetMap = new LinkedHashMap<Region, View>();

        public TouchDelegateInfo(Map<Region, View> targetMap) {
            Objects.requireNonNull(targetMap, "targetMap");
            if (targetMap.isEmpty()) {
                throw new IllegalArgumentException("targetMap cannot be empty");
            }
            for (Map.Entry<Region, View> entry : targetMap.entrySet()) {
                Region reg = Objects.requireNonNull(entry.getKey(), "region");
                View target = Objects.requireNonNull(entry.getValue(), "target");
                Region copy = new Region(reg);
                mRegions.add(copy);
                mTargetMap.put(copy, target);
            }
        }

        private TouchDelegateInfo(Parcel parcel) {
            int count = parcel.readInt();
            for (int i = 0; i < count; i++) {
                int left = parcel.readInt();
                int top = parcel.readInt();
                int right = parcel.readInt();
                int bottom = parcel.readInt();
                Region reg = new Region();
                if (left < right && top < bottom) {
                    reg.set(left, top, right, bottom);
                }
                mRegions.add(reg);
            }
        }

        public int getRegionCount() {
            return mRegions.size();
        }

        public Region getRegionAt(int index) {
            return new Region(mRegions.get(index));
        }

        public AccessibilityNodeInfo getTargetForRegion(Region region) {
            Objects.requireNonNull(region, "region");
            View target = mTargetMap.get(region);
            return target == null ? null : target.createAccessibilityNodeInfo();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            Objects.requireNonNull(dest, "dest");
            dest.writeInt(mRegions.size());
            Rect bounds = new Rect();
            for (int i = 0; i < mRegions.size(); i++) {
                mRegions.get(i).getBounds(bounds);
                dest.writeInt(bounds.left);
                dest.writeInt(bounds.top);
                dest.writeInt(bounds.right);
                dest.writeInt(bounds.bottom);
            }
        }

        public static final Parcelable.Creator<TouchDelegateInfo> CREATOR =
                new Parcelable.Creator<TouchDelegateInfo>() {
                    @Override
                    public TouchDelegateInfo createFromParcel(Parcel parcel) {
                        return new TouchDelegateInfo(parcel);
                    }

                    @Override
                    public TouchDelegateInfo[] newArray(int size) {
                        return new TouchDelegateInfo[size];
                    }
                };
    }
}
