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
        // URL handlers must construct connection objects. openConnection does
        // not initiate I/O; the reserved .invalid hostname is never resolved.
        for (String scheme : new String[] {"http", "https"}) {
            java.net.URL url;
            try {
                url = new java.net.URL(scheme + "://example.invalid/");
            } catch (java.net.MalformedURLException failure) {
                throw new IllegalStateException("URL parsing failed for " + scheme, failure);
            }
            try {
                if (url.openConnection() == null) throw new IllegalStateException("Null URL connection");
                System.out.println("MACOS_FIXTURE_URL_CONNECTION_OPENED " + scheme);
            } catch (java.io.IOException failure) {
                throw new IllegalStateException("URL connection construction failed for " + scheme, failure);
            }
        }
    }
}
