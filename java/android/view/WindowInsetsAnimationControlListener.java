// SPDX-License-Identifier: GPL-3.0-only
package android.view;

public interface WindowInsetsAnimationControlListener {
    void onReady(WindowInsetsAnimationController controller, int types);
    void onFinished(WindowInsetsAnimationController controller);
    void onCancelled(WindowInsetsAnimationController controller);
}
