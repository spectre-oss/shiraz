// SPDX-License-Identifier: GPL-3.0-only
package android.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Parser and lookup table for Android {@code event-log-tags} definitions. */
@Deprecated
public class EventLogTags {
    /** Tag number and symbolic name entry from an {@code event-log-tags} file. */
    public static class Description {
        public final int mTag;
        public final String mName;

        Description() {
            this(0, "");
        }

        Description(int tag, String name) {
            mTag = tag;
            mName = name;
        }
    }

    private final Map<String, Description> byName = new HashMap<>();
    private final Map<Integer, Description> byTag = new HashMap<>();

    public EventLogTags() throws IOException {
        File file = new File(System.getProperty("shiraz.event_log_tags", "/system/etc/event-log-tags"));
        if (file.isFile() && file.canRead()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                parse(reader);
            }
        }
    }

    public EventLogTags(BufferedReader input) throws IOException {
        if (input == null) {
            throw new NullPointerException("input == null");
        }
        parse(input);
    }

    private void parse(BufferedReader input) throws IOException {
        String line;
        while ((line = input.readLine()) != null) {
            int comment = line.indexOf('#');
            if (comment >= 0) {
                line = line.substring(0, comment);
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\\s+", 3);
            if (parts.length < 2) {
                continue;
            }
            try {
                int tag = Integer.parseInt(parts[0]);
                String name = parts[1];
                Description desc = new Description(tag, name);
                byName.put(name, desc);
                byTag.put(Integer.valueOf(tag), desc);
                EventLog.registerTag(tag, name);
            } catch (NumberFormatException ignored) {
                // Ignore malformed lines per Android EventLogTags behavior.
            }
        }
    }

    public Description get(String name) {
        return byName.get(name);
    }

    public Description get(int tag) {
        return byTag.get(Integer.valueOf(tag));
    }
}
