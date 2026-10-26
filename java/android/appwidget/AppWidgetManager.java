// SPDX-License-Identifier: GPL-3.0-only
package android.appwidget;
/** Desktop widget hosting is unavailable; callers may offer an alternative UI. */
public final class AppWidgetManager {
 private static final AppWidgetManager INSTANCE=new AppWidgetManager();
 private AppWidgetManager(){}
 public static AppWidgetManager getInstance(android.content.Context context){if(context==null)throw new NullPointerException("context");return INSTANCE;}
 public boolean isRequestPinAppWidgetSupported(){return false;}
}
