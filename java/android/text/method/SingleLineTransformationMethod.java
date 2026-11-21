// SPDX-License-Identifier: GPL-3.0-only
package android.text.method;

/** TransformationMethod that replaces newlines with spaces and hides carriage returns. */
public class SingleLineTransformationMethod extends ReplacementTransformationMethod {
    private static final char[] ORIGINAL = new char[] { '\n', '\r' };
    private static final char[] REPLACEMENT = new char[] { ' ', '\uFEFF' };
    private static SingleLineTransformationMethod sInstance;

    public SingleLineTransformationMethod() {
    }

    @Override
    protected char[] getOriginal() {
        return ORIGINAL;
    }

    @Override
    protected char[] getReplacement() {
        return REPLACEMENT;
    }

    public static SingleLineTransformationMethod getInstance() {
        if (sInstance == null) {
            sInstance = new SingleLineTransformationMethod();
        }
        return sInstance;
    }
}
