// SPDX-License-Identifier: GPL-3.0-only

package android.provider;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;

public class Browser {
    public static final String INITIAL_ZOOM_LEVEL = "browser.initialZoomLevel";
    public static final String EXTRA_APPLICATION_ID = "com.android.browser.application_id";
    public static final String EXTRA_HEADERS = "com.android.browser.headers";
    public static final String EXTRA_CREATE_NEW_TAB = "create_new_tab";

    public Browser() {
    }

    public static final void sendString(Context context, String stringToSend) {
        if (context == null) {
            throw new NullPointerException("context must not be null");
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, stringToSend);
        try {
            Intent chooser = Intent.createChooser(send, null);
            chooser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (ActivityNotFoundException ignored) {
        }
    }
}
