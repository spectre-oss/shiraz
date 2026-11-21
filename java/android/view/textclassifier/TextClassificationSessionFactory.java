// SPDX-License-Identifier: GPL-3.0-only

package android.view.textclassifier;

public interface TextClassificationSessionFactory {
    TextClassifier createTextClassificationSession(
            TextClassificationContext classificationContext);
}
