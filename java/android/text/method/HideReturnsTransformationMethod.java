// SPDX-License-Identifier: GPL-3.0-only
package android.text.method;

/** TransformationMethod that hides carriage return ('\r') characters as zero-width non-breaking spaces. */
public class HideReturnsTransformationMethod extends ReplacementTransformationMethod {
    private static final char[] ORIGINAL = new char[] { '\r' };
    private static final char[] REPLACEMENT = new char[] { '\uFEFF' };
    private static HideReturnsTransformationMethod sInstance;

    public HideReturnsTransformationMethod() {
    }

    @Override
    protected char[] getOriginal() {
        return ORIGINAL;
    }

    @Override
    protected char[] getReplacement() {
        return REPLACEMENT;
    }

    public static HideReturnsTransformationMethod getInstance() {
        if (sInstance == null) {
            sInstance = new HideReturnsTransformationMethod();
        }
        return sInstance;
    }
}
