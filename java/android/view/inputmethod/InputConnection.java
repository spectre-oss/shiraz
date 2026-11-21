// SPDX-License-Identifier: GPL-3.0-only

package android.view.inputmethod;

import android.graphics.RectF;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.view.KeyEvent;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public interface InputConnection {
    int GET_TEXT_WITH_STYLES = 1;
    int GET_EXTRACTED_TEXT_MONITOR = 1;
    int CURSOR_UPDATE_IMMEDIATE = 1;
    int CURSOR_UPDATE_MONITOR = 2;
    int CURSOR_UPDATE_FILTER_EDITOR_BOUNDS = 4;
    int CURSOR_UPDATE_FILTER_CHARACTER_BOUNDS = 8;
    int CURSOR_UPDATE_FILTER_INSERTION_MARKER = 16;
    int CURSOR_UPDATE_FILTER_VISIBLE_LINE_BOUNDS = 32;
    int CURSOR_UPDATE_FILTER_TEXT_APPEARANCE = 64;

    int HANDWRITING_GESTURE_RESULT_UNKNOWN = 0;
    int HANDWRITING_GESTURE_RESULT_SUCCESS = 1;
    int HANDWRITING_GESTURE_RESULT_UNSUPPORTED = 2;
    int HANDWRITING_GESTURE_RESULT_FAILED = 3;
    int HANDWRITING_GESTURE_RESULT_CANCELLED = 4;
    int HANDWRITING_GESTURE_RESULT_FALLBACK = 5;

    int INPUT_CONTENT_GRANT_READ_URI_PERMISSION = 1;

    CharSequence getTextBeforeCursor(int n, int flags);

    CharSequence getTextAfterCursor(int n, int flags);

    CharSequence getSelectedText(int flags);

    default SurroundingText getSurroundingText(int beforeLength, int afterLength, int flags) {
        CharSequence before = getTextBeforeCursor(beforeLength, flags);
        if (before == null) {
            return null;
        }
        CharSequence after = getTextAfterCursor(afterLength, flags);
        if (after == null) {
            return null;
        }
        CharSequence selected = getSelectedText(flags);
        String sel = selected != null ? selected.toString() : "";
        String combined = before.toString() + sel + after.toString();
        return new SurroundingText(
                combined,
                before.length(),
                before.length() + sel.length(),
                -1);
    }

    int getCursorCapsMode(int reqModes);

    ExtractedText getExtractedText(ExtractedTextRequest request, int flags);

    boolean deleteSurroundingText(int beforeLength, int afterLength);

    default boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        return false;
    }

    boolean setComposingText(CharSequence text, int newCursorPosition);

    default boolean setComposingText(
            CharSequence text, int newCursorPosition, TextAttribute textAttribute) {
        return setComposingText(text, newCursorPosition);
    }

    default boolean setComposingRegion(int start, int end) {
        return false;
    }

    default boolean setComposingRegion(int start, int end, TextAttribute textAttribute) {
        return setComposingRegion(start, end);
    }

    boolean finishComposingText();

    boolean commitText(CharSequence text, int newCursorPosition);

    default boolean commitText(
            CharSequence text, int newCursorPosition, TextAttribute textAttribute) {
        return commitText(text, newCursorPosition);
    }

    default boolean commitCompletion(CompletionInfo text) {
        return false;
    }

    default boolean commitCorrection(CorrectionInfo correctionInfo) {
        return false;
    }

    boolean setSelection(int start, int end);

    boolean performEditorAction(int editorAction);

    default boolean performContextMenuAction(int id) {
        return false;
    }

    boolean beginBatchEdit();

    boolean endBatchEdit();

    boolean sendKeyEvent(KeyEvent event);

    default boolean clearMetaKeyStates(int states) {
        return false;
    }

    default boolean reportFullscreenMode(boolean enabled) {
        return false;
    }

    default boolean performSpellCheck() {
        return false;
    }

    default boolean performPrivateCommand(String action, Bundle data) {
        return false;
    }

    default void performHandwritingGesture(
            HandwritingGesture gesture, Executor executor, final IntConsumer consumer) {
        if (executor != null && consumer != null) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    consumer.accept(HANDWRITING_GESTURE_RESULT_UNSUPPORTED);
                }
            });
        }
    }

    default boolean previewHandwritingGesture(
            PreviewableHandwritingGesture gesture, CancellationSignal cancellationSignal) {
        return false;
    }

    default boolean requestCursorUpdates(int cursorUpdateMode) {
        return false;
    }

    default boolean requestCursorUpdates(int cursorUpdateMode, int cursorUpdateFilter) {
        if (cursorUpdateFilter == 0) {
            return requestCursorUpdates(cursorUpdateMode);
        }
        return false;
    }

    default void requestTextBoundsInfo(
            RectF bounds, Executor executor, final Consumer<TextBoundsInfoResult> consumer) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(consumer, "consumer");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                consumer.accept(new TextBoundsInfoResult(TextBoundsInfoResult.CODE_UNSUPPORTED));
            }
        });
    }

    default Handler getHandler() {
        return null;
    }

    void closeConnection();

    default boolean commitContent(
            InputContentInfo inputContentInfo, int flags, Bundle opts) {
        return false;
    }

    default boolean setImeConsumesInput(boolean imeConsumesInput) {
        return false;
    }

    default TextSnapshot takeSnapshot() {
        return null;
    }

    default boolean replaceText(
            int start,
            int end,
            CharSequence text,
            int newCursorPosition,
            TextAttribute textAttribute) {
        Objects.requireNonNull(text, "text");
        beginBatchEdit();
        finishComposingText();
        setSelection(start, end);
        commitText(text, newCursorPosition);
        endBatchEdit();
        return true;
    }
}
