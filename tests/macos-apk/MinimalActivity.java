// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.macosfixture;

/** One solid View; no resources, text, drawables, threads or native code. */
public final class MinimalActivity extends android.app.Activity {
    @Override
    public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.view.View view = new android.view.View(this);
        view.setBackgroundColor(0xff123456);
        setContentView(view);
        System.out.println("MACOS_FIXTURE_CONTENT_SET");
        // macOS boots without okhttp/wolfJSSE: URL parsing must work and
        // openConnection must fail with an IOException an app can catch. No
        // connection is attempted (.invalid never resolves). Any other
        // Throwable propagates and fails the run.
        for (String scheme : new String[] {"http", "https"}) {
            java.net.URL url;
            try {
                url = new java.net.URL(scheme + "://example.invalid/");
            } catch (java.net.MalformedURLException failure) {
                throw new IllegalStateException("URL parsing failed for " + scheme, failure);
            }
            try {
                url.openConnection();
                System.out.println("MACOS_FIXTURE_URL_CONNECTION_OPENED " + scheme);
            } catch (java.io.IOException expected) {
                System.out.println("MACOS_FIXTURE_URL_IOEXCEPTION " + scheme);
                System.out.println("MACOS_FIXTURE_URL_DETAIL " + expected);
            }
        }
    }
}
