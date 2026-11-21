// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch.observer;

public interface ObserverCallback {
    void onSchemaChanged(SchemaChangeInfo changeInfo);

    void onDocumentChanged(DocumentChangeInfo changeInfo);
}
