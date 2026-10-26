// SPDX-License-Identifier: GPL-3.0-only
package android.view;
public interface ViewManager {
 void addView(View view,ViewGroup.LayoutParams params);
 void updateViewLayout(View view,ViewGroup.LayoutParams params);
 void removeView(View view);
}
