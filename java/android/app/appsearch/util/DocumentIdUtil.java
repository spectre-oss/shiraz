// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch.util;

import android.app.appsearch.GenericDocument;
import java.util.Objects;

public class DocumentIdUtil {
    private DocumentIdUtil() {
    }

    public static String createQualifiedId(
            String packageName, String databaseName, GenericDocument document) {
        Objects.requireNonNull(document);
        return createQualifiedId(
                packageName, databaseName, document.getNamespace(), document.getId());
    }

    public static String createQualifiedId(
            String packageName, String databaseName, String namespace, String id) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(databaseName);
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(id);
        StringBuilder sb = new StringBuilder();
        escapeAndAppend(sb, packageName);
        sb.append('$');
        escapeAndAppend(sb, databaseName);
        sb.append('/');
        escapeAndAppend(sb, namespace);
        sb.append('#');
        escapeAndAppend(sb, id);
        return sb.toString();
    }

    private static void escapeAndAppend(StringBuilder sb, String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '$' || c == '/' || c == '#') {
                sb.append('\\');
            }
            sb.append(c);
        }
    }
}
