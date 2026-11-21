// SPDX-License-Identifier: GPL-3.0-only
public final class ManifestClassSmoke {
    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("android.Manifest");
        if (!java.lang.reflect.Modifier.isFinal(type.getModifiers())) {
            throw new AssertionError("android.Manifest must be final");
        }
        if (type.getDeclaredMethods().length != 0 || type.getConstructor().newInstance() == null) {
            throw new AssertionError("unexpected android.Manifest API");
        }
        System.out.println("MANIFEST_CLASS_SMOKE_PASS");
    }
}
