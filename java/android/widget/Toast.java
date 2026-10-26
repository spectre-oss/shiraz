// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import shiraz.runtime.ToastOverlay;
import java.util.ArrayList;

/** Text toasts rendered as an overlay in the host window, without a desktop notification service. */
public class Toast {
    public static final int LENGTH_SHORT = 0, LENGTH_LONG = 1;
    private final Context context;
    private final Handler handler;
    private volatile int duration;
    private volatile View view;
    public Toast(Context context) {
        if (context == null) throw new NullPointerException("context");
        if (Looper.myLooper() == null) throw new RuntimeException("Toast requires a prepared Looper");
        this.context = context; handler = new Handler(context.getMainLooper());
    }
    public static Toast makeText(Context context, CharSequence text, int duration) {
        Toast toast = new Toast(context); toast.view = new TextToast(context, text); toast.setDuration(duration); return toast;
    }
    public static Toast makeText(Context context, int resource, int duration) { return makeText(context, context.getString(resource), duration); }
    public void setDuration(int value) { if (value != LENGTH_SHORT && value != LENGTH_LONG) throw new IllegalArgumentException("duration"); duration = value; }
    public int getDuration() { return duration; }
    public View getView() { return view; }
    public void setView(View value) {
        if (value != null) throw new UnsupportedOperationException("Custom toast View measurement/layout is not implemented");
        view = null;
    }
    public void setText(int resource) { setText(context.getString(resource)); }
    public void setText(CharSequence text) {
        if (!(view instanceof TextToast)) throw new IllegalStateException("Toast was not created with makeText");
        ((TextToast) view).setText(text);
        submit(new Runnable() { @Override public void run() { ToastOverlay.invalidate(Toast.this); } });
    }
    private void submit(Runnable action) { if (!handler.post(action)) throw new IllegalStateException("Toast session has ended"); }
    public void show() {
        if (view == null) throw new IllegalStateException("Toast has no View; use makeText");
        final View shown = view; final long millis = duration == LENGTH_LONG ? 3500 : 2000;
        submit(new Runnable() { @Override public void run() { ToastOverlay.show(Toast.this, shown, millis); } });
    }
    public void cancel() { submit(new Runnable() { @Override public void run() { ToastOverlay.cancel(Toast.this); } }); }
    private static final class TextToast extends View {
        private volatile String text;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        TextToast(Context context, CharSequence text) { super(context); paint.setTextSize(14); setText(text); }
        void setText(CharSequence value) {
            if (value == null) throw new NullPointerException("text");
            if (value.length() > 4096) throw new IllegalArgumentException("Toast text exceeds 4096 characters");
            text = value.toString(); invalidate();
        }
        private ArrayList<String> lines(String text, float width) {
            ArrayList<String> lines = new ArrayList<String>();
            String remaining = text;
            while (!remaining.isEmpty() && lines.size() < 3) {
                int newline = remaining.indexOf('\n');
                String segment = newline >= 0 ? remaining.substring(0, newline) : remaining;
                int end = segment.length();
                if (paint.measureText(segment) > width) {
                    int low = 0, high = segment.codePointCount(0, segment.length());
                    while (low < high) {
                        int count = (low + high + 1) / 2;
                        int cut = segment.offsetByCodePoints(0, count);
                        if (paint.measureText(segment.substring(0, cut)) <= width) low = count; else high = count - 1;
                    }
                    end = segment.offsetByCodePoints(0, Math.max(1, low));
                    int space = segment.lastIndexOf(' ', end - 1); if (space > 0) end = space;
                }
                lines.add(segment.substring(0, end));
                int consumed = end;
                if (end == segment.length() && newline >= 0) ++consumed;
                remaining = remaining.substring(consumed);
                while (remaining.startsWith(" ")) remaining = remaining.substring(1);
            }
            if (lines.isEmpty()) lines.add("");
            // Truncation is explicit in this minimal three-line text-toast layout.
            return lines;
        }
        @Override protected void onDraw(Canvas canvas) {
            float available = Math.max(1, getWidth() - 48);
            ArrayList<String> lines = lines(text, available);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float lineHeight = metrics.descent - metrics.ascent + metrics.leading;
            float widest = 0; for (String line : lines) widest = Math.max(widest, paint.measureText(line));
            float width = Math.min(getWidth(), widest + 24), height = Math.min(getHeight(), lineHeight * lines.size() + 16);
            float left = (getWidth() - width) / 2, top = Math.max(0, getHeight() - 32 - height);
            paint.setColor(0xe6202020); canvas.drawRect(left, top, left + width, top + height, paint);
            paint.setColor(0xffffffff);
            float baseline = top + 8 - metrics.ascent;
            for (String line : lines) { canvas.drawText(line, left + 12, baseline, paint); baseline += lineHeight; }
        }
    }
}
