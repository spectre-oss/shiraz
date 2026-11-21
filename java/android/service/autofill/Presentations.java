// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.widget.RemoteViews;

/** Holds UI presentations for autofill suggestions across menu, inline, and dialog surfaces. */
public final class Presentations {
    private final RemoteViews mMenuPresentation;
    private final InlinePresentation mInlinePresentation;
    private final RemoteViews mDialogPresentation;
    private final InlinePresentation mInlineTooltipPresentation;

    Presentations(
            RemoteViews menuPresentation,
            InlinePresentation inlinePresentation,
            RemoteViews dialogPresentation,
            InlinePresentation inlineTooltipPresentation) {
        mMenuPresentation = menuPresentation;
        mInlinePresentation = inlinePresentation;
        mDialogPresentation = dialogPresentation;
        mInlineTooltipPresentation = inlineTooltipPresentation;
    }

    public RemoteViews getMenuPresentation() {
        return mMenuPresentation;
    }

    public InlinePresentation getInlinePresentation() {
        return mInlinePresentation;
    }

    public RemoteViews getDialogPresentation() {
        return mDialogPresentation;
    }

    public InlinePresentation getInlineTooltipPresentation() {
        return mInlineTooltipPresentation;
    }

    public static final class Builder {
        private RemoteViews mMenuPresentation;
        private InlinePresentation mInlinePresentation;
        private RemoteViews mDialogPresentation;
        private InlinePresentation mInlineTooltipPresentation;

        public Builder() {
        }

        public Builder setMenuPresentation(RemoteViews value) {
            mMenuPresentation = value;
            return this;
        }

        public Builder setInlinePresentation(InlinePresentation value) {
            mInlinePresentation = value;
            return this;
        }

        public Builder setDialogPresentation(RemoteViews value) {
            mDialogPresentation = value;
            return this;
        }

        public Builder setInlineTooltipPresentation(InlinePresentation value) {
            mInlineTooltipPresentation = value;
            return this;
        }

        public Presentations build() {
            if (mMenuPresentation == null && mInlinePresentation == null && mDialogPresentation == null) {
                throw new IllegalStateException("All presentations are null.");
            }
            return new Presentations(
                    mMenuPresentation,
                    mInlinePresentation,
                    mDialogPresentation,
                    mInlineTooltipPresentation);
        }
    }
}
