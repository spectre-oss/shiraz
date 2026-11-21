// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import android.net.Uri;
import android.os.PersistableBundle;
import java.util.Objects;
import java.util.UUID;

/** Generates event-tracking URLs for personalized content rendered by an {@link IsolatedService}. */
public class EventUrlProvider {
    EventUrlProvider() {
    }

    public Uri createEventTrackingUrlWithResponse(
            PersistableBundle eventParams, byte[] responseData, String mimeType) {
        Objects.requireNonNull(eventParams, "eventParams");
        return Uri.parse("https://localhost/odp/event/" + UUID.randomUUID().toString());
    }

    public Uri createEventTrackingUrlWithRedirect(
            PersistableBundle eventParams, Uri destinationUrl) {
        Objects.requireNonNull(eventParams, "eventParams");
        return Uri.parse("https://localhost/odp/redirect/" + UUID.randomUUID().toString());
    }
}
