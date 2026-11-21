// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;

/** ImageView badge associated with a contact URI, email address, or phone number. */
public class QuickContactBadge extends ImageView implements View.OnClickListener {
    protected String[] mExcludeMimes = null;

    private Uri mContactUri;
    private String mContactEmail;
    private String mContactPhone;
    private Bundle mExtras;
    private Drawable mOverlay;
    private Drawable mDefaultAvatar;
    private String mPrioritizedMimeType;
    private int mMode;

    public QuickContactBadge(Context context) {
        this(context, null);
    }

    public QuickContactBadge(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QuickContactBadge(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public QuickContactBadge(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setOnClickListener(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        final Drawable overlay = mOverlay;
        if (overlay != null && overlay.isStateful()
                && overlay.setState(getDrawableState())) {
            invalidateDrawable(overlay);
        }
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);
        if (mOverlay != null) {
            mOverlay.setHotspot(x, y);
        }
    }

    @SuppressWarnings("unused")
    public void setMode(int size) {
        mMode = size;
    }

    public void setPrioritizedMimeType(String prioritizedMimeType) {
        mPrioritizedMimeType = prioritizedMimeType;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!isEnabled()) {
            return;
        }
        if (mOverlay == null || mOverlay.getIntrinsicWidth() == 0
                || mOverlay.getIntrinsicHeight() == 0) {
            return;
        }
        mOverlay.setBounds(0, 0, getWidth(), getHeight());
        if (getPaddingTop() == 0 && getPaddingLeft() == 0) {
            mOverlay.draw(canvas);
        } else {
            int saveCount = canvas.getSaveCount();
            canvas.save();
            canvas.translate(getPaddingLeft(), getPaddingTop());
            mOverlay.draw(canvas);
            canvas.restoreToCount(saveCount);
        }
    }

    private boolean isAssigned() {
        return mContactUri != null || mContactEmail != null || mContactPhone != null;
    }

    public void setImageToDefault() {
        setImageDrawable(mDefaultAvatar);
    }

    public void assignContactUri(Uri contactUri) {
        mContactUri = contactUri;
        mContactEmail = null;
        mContactPhone = null;
        onContactUriChanged();
    }

    public void assignContactFromEmail(String emailAddress, boolean lazyLookup) {
        assignContactFromEmail(emailAddress, lazyLookup, null);
    }

    public void assignContactFromEmail(String emailAddress, boolean lazyLookup, Bundle extras) {
        mContactEmail = emailAddress;
        mExtras = extras;
        if (!lazyLookup) {
            mContactUri = emailAddress != null ? Uri.fromParts("mailto", emailAddress, null) : null;
        } else {
            mContactUri = null;
        }
        onContactUriChanged();
    }

    public void assignContactFromPhone(String phoneNumber, boolean lazyLookup) {
        assignContactFromPhone(phoneNumber, lazyLookup, null);
    }

    public void assignContactFromPhone(String phoneNumber, boolean lazyLookup, Bundle extras) {
        mContactPhone = phoneNumber;
        mExtras = extras;
        if (!lazyLookup) {
            mContactUri = phoneNumber != null ? Uri.fromParts("tel", phoneNumber, null) : null;
        } else {
            mContactUri = null;
        }
        onContactUriChanged();
    }

    public void setOverlay(Drawable overlay) {
        mOverlay = overlay;
        invalidate();
    }

    private void onContactUriChanged() {
        setEnabled(isAssigned());
    }

    @Override
    public void onClick(View v) {
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return QuickContactBadge.class.getName();
    }

    public void setExcludeMimes(String[] excludeMimes) {
        mExcludeMimes = excludeMimes;
    }
}
