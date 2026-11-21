// SPDX-License-Identifier: GPL-3.0-only
package android.text;

/** Deprecated legacy clipboard abstraction superseded by android.content.ClipboardManager. */
@Deprecated
public abstract class ClipboardManager {
    public ClipboardManager() {
    }

    public abstract CharSequence getText();

    public abstract void setText(CharSequence text);

    public abstract boolean hasText();
}
