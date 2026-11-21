// SPDX-License-Identifier: GPL-3.0-only
package android.window;

public interface SplashScreen {
    int SPLASH_SCREEN_STYLE_SOLID_COLOR = 0;
    int SPLASH_SCREEN_STYLE_ICON = 1;

    void setOnExitAnimationListener(SplashScreen.OnExitAnimationListener listener);

    void clearOnExitAnimationListener();

    void setSplashScreenTheme(int themeId);

    interface OnExitAnimationListener {
        void onSplashScreenExit(SplashScreenView splashScreenView);
    }
}
