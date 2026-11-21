// SPDX-License-Identifier: GPL-3.0-only
package android.appwidget;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SizeF;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.Executor;

public class AppWidgetHostView extends FrameLayout {
    private int mAppWidgetId;
    private AppWidgetProviderInfo mInfo;
    private Executor mAsyncExecutor;
    private boolean mOnLightBackground;
    private SparseIntArray mColorMapping;

    public AppWidgetHostView(Context context) {
        this(context, 0, 0);
    }

    public AppWidgetHostView(Context context, int animationIn, int animationOut) {
        super(context);
    }

    public void setAppWidget(int appWidgetId, AppWidgetProviderInfo info) {
        mAppWidgetId = appWidgetId;
        mInfo = info;
        if (info != null) {
            Rect padding = getDefaultPaddingForWidget(getContext(), info.provider, null);
            setPadding(padding.left, padding.top, padding.right, padding.bottom);
        }
    }

    public static Rect getDefaultPaddingForWidget(
            Context context, ComponentName component, Rect padding) {
        if (padding == null) {
            padding = new Rect(0, 0, 0, 0);
        } else {
            padding.set(0, 0, 0, 0);
        }
        return padding;
    }

    public int getAppWidgetId() {
        return mAppWidgetId;
    }

    public AppWidgetProviderInfo getAppWidgetInfo() {
        return mInfo;
    }

    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        // Host manages widget state across configuration/recreation via appWidgetId.
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        // Host manages widget state across configuration/recreation via appWidgetId.
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    public void updateAppWidgetSize(
            Bundle newOptions, int minWidth, int minHeight, int maxWidth, int maxHeight) {
        Bundle options = newOptions != null ? new Bundle(newOptions) : new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, minWidth);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, minHeight);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, maxWidth);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, maxHeight);
        updateAppWidgetOptions(options);
    }

    public void updateAppWidgetSize(Bundle newOptions, List<SizeF> sizes) {
        if (sizes == null) {
            throw new NullPointerException("sizes");
        }
        Bundle options = newOptions != null ? new Bundle(newOptions) : new Bundle();
        if (!sizes.isEmpty()) {
            float minW = Float.MAX_VALUE;
            float minH = Float.MAX_VALUE;
            float maxW = 0f;
            float maxH = 0f;
            for (int i = 0; i < sizes.size(); i++) {
                SizeF s = sizes.get(i);
                if (s != null) {
                    minW = Math.min(minW, s.getWidth());
                    minH = Math.min(minH, s.getHeight());
                    maxW = Math.max(maxW, s.getWidth());
                    maxH = Math.max(maxH, s.getHeight());
                }
            }
            if (minW != Float.MAX_VALUE) {
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, (int) minW);
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, (int) minH);
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, (int) maxW);
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, (int) maxH);
            }
        }
        updateAppWidgetOptions(options);
    }

    public void updateAppWidgetOptions(Bundle options) {
        AppWidgetManager.getInstance(getContext()).updateAppWidgetOptions(mAppWidgetId, options);
    }

    @Override
    public FrameLayout.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return super.generateLayoutParams(attrs);
    }

    public void setExecutor(Executor executor) {
        mAsyncExecutor = executor;
    }

    public void setOnLightBackground(boolean onLightBackground) {
        mOnLightBackground = onLightBackground;
    }

    public void updateAppWidget(RemoteViews remoteViews) {
        removeAllViews();
        View content = null;
        if (remoteViews == null) {
            content = getDefaultView();
        } else {
            content = getDefaultView();
        }
        if (content == null) {
            content = getErrorView();
        }
        if (content != null) {
            prepareView(content);
            addView(content);
        }
    }

    protected void prepareView(View view) {
        if (view == null) {
            return;
        }
        FrameLayout.LayoutParams requested =
                (FrameLayout.LayoutParams) view.getLayoutParams();
        if (requested == null) {
            requested = new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        } else {
            requested.width = LayoutParams.MATCH_PARENT;
            requested.height = LayoutParams.MATCH_PARENT;
        }
        view.setLayoutParams(requested);
    }

    protected View getDefaultView() {
        if (mInfo != null && mInfo.initialLayout != 0) {
            try {
                return LayoutInflater.from(getContext())
                        .inflate(mInfo.initialLayout, this, false);
            } catch (RuntimeException ignored) {
                return getErrorView();
            }
        }
        return null;
    }

    protected View getErrorView() {
        TextView tv = new TextView(getContext());
        tv.setText("Widget unavailable");
        return tv;
    }

    public void setColorResources(SparseIntArray colorMapping) {
        mColorMapping = colorMapping != null ? colorMapping.clone() : null;
    }

    public void resetColorResources() {
        mColorMapping = null;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
    }
}
