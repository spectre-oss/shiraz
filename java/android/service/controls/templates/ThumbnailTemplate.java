// SPDX-License-Identifier: GPL-3.0-only
package android.service.controls.templates;

import android.graphics.drawable.Icon;
import java.util.Objects;

public final class ThumbnailTemplate extends ControlTemplate {
    private final boolean mActive;
    private final Icon mThumbnail;
    private final CharSequence mContentDescription;

    public ThumbnailTemplate(String templateId, boolean active, Icon thumbnail,
            CharSequence contentDescription) {
        super(templateId);
        mActive = active;
        mThumbnail = Objects.requireNonNull(thumbnail, "thumbnail must not be null");
        mContentDescription = Objects.requireNonNull(
                contentDescription, "contentDescription must not be null");
    }

    public boolean isActive() { return mActive; }
    public Icon getThumbnail() { return mThumbnail; }
    public CharSequence getContentDescription() { return mContentDescription; }

    @Override
    public int getTemplateType() {
        return TYPE_THUMBNAIL;
    }
}
