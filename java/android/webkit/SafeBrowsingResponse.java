// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;

public abstract class SafeBrowsingResponse {
    public SafeBrowsingResponse() {
    }

    public abstract void showInterstitial(boolean allowReporting);
    public abstract void proceed(boolean report);
    public abstract void backToSafety(boolean report);
}
