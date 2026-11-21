// SPDX-License-Identifier: GPL-3.0-only

package android.view.textclassifier;

import java.util.Objects;

public final class TextClassificationManager {
    private static final TextClassifier DEFAULT_CLASSIFIER = new TextClassifier() {};

    private TextClassifier mCustomTextClassifier;
    private TextClassificationSessionFactory mSessionFactory;

    public TextClassificationManager() {
    }

    public TextClassifier getTextClassifier() {
        return mCustomTextClassifier != null ? mCustomTextClassifier : DEFAULT_CLASSIFIER;
    }

    public void setTextClassifier(TextClassifier textClassifier) {
        mCustomTextClassifier = textClassifier;
    }

    public TextClassifier createTextClassificationSession(
            TextClassificationContext classificationContext) {
        Objects.requireNonNull(classificationContext, "classificationContext");
        if (mSessionFactory != null) {
            TextClassifier tc =
                    mSessionFactory.createTextClassificationSession(classificationContext);
            if (tc != null) {
                return tc;
            }
        }
        return getTextClassifier();
    }

    public void setTextClassificationSessionFactory(TextClassificationSessionFactory factory) {
        mSessionFactory = factory;
    }
}
