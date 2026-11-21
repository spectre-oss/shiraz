// SPDX-License-Identifier: GPL-3.0-only
package android.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

/** Object-selection filter helper driven by {@code ANDROID_OBJECT_FILTER}. */
public class DebugUtils {
    DebugUtils() {}

    public static boolean isObjectSelected(Object object) {
        if (object == null) {
            return false;
        }
        String filter = System.getenv("ANDROID_OBJECT_FILTER");
        if (filter == null || filter.isEmpty()) {
            return false;
        }
        String[] clauses = filter.split("@");
        if (clauses.length == 0) {
            return false;
        }
        Class<?> clazz = object.getClass();
        if (!clazz.getSimpleName().matches(clauses[0])) {
            return false;
        }
        boolean matched = clauses.length == 1;
        for (int i = 1; i < clauses.length; i++) {
            String[] pair = clauses[i].split("=");
            if (pair.length != 2) {
                continue;
            }
            String key = pair[0];
            String pattern = pair[1];
            try {
                String getterName =
                        "get" + key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1);
                Method method = clazz.getMethod(getterName);
                Object value = method.invoke(object);
                matched |= String.valueOf(value).matches(pattern);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
                // Ignore non-matching reflective getters per Android DebugUtils semantics.
            }
        }
        return matched;
    }
}
