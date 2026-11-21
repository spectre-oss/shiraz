// SPDX-License-Identifier: GPL-3.0-only
package android.media.projection;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import java.util.Objects;

/**
 * Manages the retrieval of certain types of {@link MediaProjection} tokens.
 */
public final class MediaProjectionManager {
    private static final String ACTION_MEDIA_PROJECTION =
            "android.media.projection.action.REQUEST_MEDIA_PROJECTION";
    private static final String EXTRA_MEDIA_PROJECTION_CONFIG =
            "android.media.projection.extra.EXTRA_MEDIA_PROJECTION_CONFIG";

    private final Context mContext;

    public MediaProjectionManager() {
        mContext = null;
    }

    public MediaProjectionManager(Context context) {
        mContext = context;
    }

    public Intent createScreenCaptureIntent() {
        return new Intent(ACTION_MEDIA_PROJECTION);
    }

    public Intent createScreenCaptureIntent(MediaProjectionConfig config) {
        Objects.requireNonNull(config, "config");
        Intent intent = createScreenCaptureIntent();
        intent.putExtra(EXTRA_MEDIA_PROJECTION_CONFIG, config);
        return intent;
    }

    public MediaProjection getMediaProjection(int resultCode, Intent resultData) {
        Objects.requireNonNull(resultData, "resultData must not be null");
        if (resultCode != Activity.RESULT_OK) {
            return null;
        }
        return new MediaProjection();
    }
}
