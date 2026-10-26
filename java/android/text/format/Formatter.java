package android.text.format;

import android.content.Context;
import java.util.Locale;

public final class Formatter {
    private Formatter() {}

    public static String formatFileSize(Context context, long sizeBytes) {
        if (sizeBytes < 0) return "-" + formatFileSize(context, -sizeBytes);
        String[] units = {"B", "kB", "MB", "GB", "TB", "PB"};
        double value = sizeBytes;
        int unit = 0;
        while (value >= 1000 && unit < units.length - 1) { value /= 1000; unit++; }
        if (unit == 0) return String.format(Locale.getDefault(), "%d %s", sizeBytes, units[0]);
        return String.format(Locale.getDefault(), value < 10 ? "%.2f %s" : value < 100 ? "%.1f %s" : "%.0f %s", value, units[unit]);
    }
}
