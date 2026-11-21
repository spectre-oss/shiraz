// SPDX-License-Identifier: GPL-3.0-only
package android.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Structured binary event log facility backed by an in-process thread-safe ring buffer and
 * {@code event-log-tags} registry.
 */
public class EventLog {
    private static final int MAX_BUFFER_EVENTS = 1024;
    private static final Deque<Event> RING_BUFFER = new ArrayDeque<>();
    private static final Map<Integer, String> TAG_NAMES = new ConcurrentHashMap<>();
    private static final Map<String, Integer> TAG_CODES = new ConcurrentHashMap<>();
    private static volatile boolean sTagsLoaded;

    EventLog() {}

    /** Immutable structured event record read from {@link EventLog#readEvents}. */
    public static final class Event {
        private final int processId;
        private final int threadId;
        private final long timeNanos;
        private final int tag;
        private final Object data;

        Event() {
            this(0, 0, 0L, 0, null);
        }

        Event(int processId, int threadId, long timeNanos, int tag, Object data) {
            this.processId = processId;
            this.threadId = threadId;
            this.timeNanos = timeNanos;
            this.tag = tag;
            this.data = data;
        }

        public int getProcessId() {
            return processId;
        }

        public int getThreadId() {
            return threadId;
        }

        public long getTimeNanos() {
            return timeNanos;
        }

        public int getTag() {
            return tag;
        }

        public synchronized Object getData() {
            if (data instanceof Object[]) {
                return ((Object[]) data).clone();
            }
            return data;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Event)) {
                return false;
            }
            Event other = (Event) o;
            return processId == other.processId
                    && threadId == other.threadId
                    && timeNanos == other.timeNanos
                    && tag == other.tag
                    && Objects.deepEquals(data, other.data);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(processId, threadId, timeNanos, tag);
            result = 31 * result + (data instanceof Object[] ? Arrays.deepHashCode((Object[]) data) : Objects.hashCode(data));
            return result;
        }
    }

    public static int writeEvent(int tag, int value) {
        record(tag, Integer.valueOf(value));
        return 4 + 1 + 4;
    }

    public static int writeEvent(int tag, long value) {
        record(tag, Long.valueOf(value));
        return 4 + 1 + 8;
    }

    public static int writeEvent(int tag, float value) {
        record(tag, Float.valueOf(value));
        return 4 + 1 + 4;
    }

    public static int writeEvent(int tag, String str) {
        if (str == null) {
            str = "NULL";
        }
        record(tag, str);
        return 4 + 1 + 4 + str.getBytes(StandardCharsets.UTF_8).length;
    }

    public static int writeEvent(int tag, Object... list) {
        if (list == null) {
            throw new IllegalArgumentException("null payload list");
        }
        Object[] copy = new Object[list.length];
        int bytes = 4 + 2;
        for (int i = 0; i < list.length; i++) {
            Object item = list[i];
            if (item == null) {
                String s = "NULL";
                copy[i] = s;
                bytes += 1 + 4 + s.length();
            } else if (item instanceof Integer || item instanceof Byte || item instanceof Short) {
                int v = ((Number) item).intValue();
                copy[i] = Integer.valueOf(v);
                bytes += 1 + 4;
            } else if (item instanceof Long) {
                copy[i] = item;
                bytes += 1 + 8;
            } else if (item instanceof Float) {
                copy[i] = item;
                bytes += 1 + 4;
            } else if (item instanceof String) {
                String s = (String) item;
                copy[i] = s;
                bytes += 1 + 4 + s.getBytes(StandardCharsets.UTF_8).length;
            } else {
                throw new IllegalArgumentException("Unsupported EventLog payload type: " + item.getClass().getName());
            }
        }
        record(tag, copy);
        return bytes;
    }

    public static void readEvents(int[] tags, Collection<Event> output) throws IOException {
        if (tags == null || output == null) {
            throw new NullPointerException("tags and output must not be null");
        }
        synchronized (RING_BUFFER) {
            for (Event event : RING_BUFFER) {
                for (int wanted : tags) {
                    if (event.getTag() == wanted) {
                        output.add(event);
                        break;
                    }
                }
            }
        }
    }

    public static String getTagName(int tag) {
        ensureTagsLoaded();
        return TAG_NAMES.get(Integer.valueOf(tag));
    }

    public static int getTagCode(String name) {
        if (name == null) {
            return -1;
        }
        ensureTagsLoaded();
        Integer code = TAG_CODES.get(name);
        return code != null ? code.intValue() : -1;
    }

    static void registerTag(int tag, String name) {
        if (name != null && !name.isEmpty()) {
            TAG_NAMES.put(Integer.valueOf(tag), name);
            TAG_CODES.put(name, Integer.valueOf(tag));
        }
    }

    private static void record(int tag, Object payload) {
        int pid = currentPid();
        int tid = (int) Thread.currentThread().getId();
        Event event = new Event(pid, tid, System.nanoTime(), tag, payload);
        synchronized (RING_BUFFER) {
            if (RING_BUFFER.size() >= MAX_BUFFER_EVENTS) {
                RING_BUFFER.removeFirst();
            }
            RING_BUFFER.addLast(event);
        }
    }

    private static int currentPid() {
        try {
            return android.os.Process.myPid();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void ensureTagsLoaded() {
        if (sTagsLoaded) {
            return;
        }
        synchronized (EventLog.class) {
            if (sTagsLoaded) {
                return;
            }
            registerTag(52004, "binder_sample");
            registerTag(30001, "am_finish_activity");
            registerTag(30005, "am_create_activity");
            registerTag(75000, "sqlite_mem_alarm_current");
            String customPath = System.getProperty("shiraz.event_log_tags", "/system/etc/event-log-tags");
            File file = new File(customPath);
            if (file.isFile() && file.canRead()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    new EventLogTags(reader);
                } catch (IOException ignored) {
                    // Keep built-in tag mappings when no host tags file is present.
                }
            }
            sTagsLoaded = true;
        }
    }
}
