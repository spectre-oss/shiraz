// SPDX-License-Identifier: GPL-3.0-only
package android.app;
public final class UiModeManager {
 public static final int MODE_NIGHT_AUTO=0,MODE_NIGHT_NO=1,MODE_NIGHT_YES=2;
 private final android.content.Context context;
 public UiModeManager(android.content.Context context){this.context=context;}
 public int getCurrentModeType(){return context.getResources().getConfiguration().uiMode&15;}
 public int getNightMode(){return (context.getResources().getConfiguration().uiMode&48)==32?MODE_NIGHT_YES:MODE_NIGHT_NO;}
}
