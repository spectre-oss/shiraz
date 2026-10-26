// SPDX-License-Identifier: GPL-3.0-only
package android.view.inputmethod;
public class ExtractedText {
 public static final int FLAG_SINGLE_LINE=1,FLAG_SELECTING=2;
 public CharSequence text;public int startOffset,partialStartOffset=-1,partialEndOffset=-1,selectionStart,selectionEnd,flags;
}
