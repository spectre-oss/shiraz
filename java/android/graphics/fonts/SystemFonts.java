// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.fonts;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

public final class SystemFonts {
    private static final int MAX_SYSTEM_FONTS = 12;
    private static final long MAX_FONT_FILE_BYTES = 8L * 1024L * 1024L;
    private static Set<Font> cachedFonts;

    private SystemFonts() {
    }

    public static synchronized Set<Font> getAvailableFonts() {
        if (cachedFonts != null) {
            return cachedFonts;
        }
        Set<Font> fonts = new LinkedHashSet<Font>();
        ArrayList<File> dirs = new ArrayList<File>();
        addDir(dirs, System.getenv("SHIRAZ_FONT_DIR"));
        addDir(dirs, System.getenv("SHIRAZ_ANDROID_FONT_DIR"));
        addDir(dirs, "/system/fonts");
        addDir(dirs, "/System/Library/Fonts/Supplemental");
        addDir(dirs, "/System/Library/Fonts");
        addDir(dirs, "/Library/Fonts");
        addDir(dirs, "/usr/share/fonts/truetype");
        addDir(dirs, "/usr/share/fonts/opentype");
        addDir(dirs, "/usr/share/fonts");

        ArrayList<File> candidates = new ArrayList<File>();
        for (int i = 0; i < dirs.size(); i++) {
            collectFontFiles(dirs.get(i), 0, candidates);
            if (candidates.size() >= 64) {
                break;
            }
        }

        for (int i = 0; i < candidates.size() && fonts.size() < MAX_SYSTEM_FONTS; i++) {
            File file = candidates.get(i);
            try {
                Font font = new Font.Builder(file).build();
                fonts.add(font);
            } catch (IOException ignored) {
                // Skip font collections, variation-only fonts, or unsupported formats.
            } catch (RuntimeException ignored) {
                // Skip invalid or non-Loadable fonts.
            }
        }

        cachedFonts = Collections.unmodifiableSet(fonts);
        return cachedFonts;
    }

    private static void addDir(ArrayList<File> dirs, String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        File dir = new File(path);
        if (dir.isDirectory() && !dirs.contains(dir)) {
            dirs.add(dir);
        }
    }

    private static void collectFontFiles(File dir, int depth, ArrayList<File> out) {
        if (dir == null || depth > 2 || out.size() >= 64) {
            return;
        }
        File[] entries = dir.listFiles();
        if (entries == null || entries.length == 0) {
            return;
        }
        Arrays.sort(
                entries,
                new Comparator<File>() {
                    @Override
                    public int compare(File a, File b) {
                        return a.getName().compareToIgnoreCase(b.getName());
                    }
                });
        for (int i = 0; i < entries.length && out.size() < 64; i++) {
            File entry = entries[i];
            if (entry.isFile()) {
                String lower = entry.getName().toLowerCase();
                if ((lower.endsWith(".ttf") || lower.endsWith(".otf"))
                        && entry.length() > 0
                        && entry.length() <= MAX_FONT_FILE_BYTES) {
                    out.add(entry);
                }
            } else if (entry.isDirectory() && depth < 2) {
                collectFontFiles(entry, depth + 1, out);
            }
        }
    }
}
