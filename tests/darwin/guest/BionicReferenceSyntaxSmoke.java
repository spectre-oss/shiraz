// SPDX-License-Identifier: GPL-3.0-only
// Portable supervisor parsing checks; never Android/bionic execution evidence.
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class BionicReferenceSyntaxSmoke {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] ignored) throws Exception {
        Method marker = BionicReferenceRunner.class.getDeclaredMethod("oneMarker", String.class, String.class);
        marker.setAccessible(true);
        require("payload".equals(marker.invoke(null, "noise\nREADY payload\n", "READY ")), "LF marker");
        require("payload".equals(marker.invoke(null, "noise\r\nREADY payload\r\n", "READY ")), "CRLF marker");
        for (String text : new String[] {"noise\n", "READY one\nREADY two\n"}) {
            try { marker.invoke(null, text, "READY "); throw new AssertionError("ambiguous marker accepted"); }
            catch (InvocationTargetException expected) {
                require(expected.getCause() instanceof AssertionError, "explicit marker rejection");
            }
        }
        Method name = BionicReferenceRunner.class.getDeclaredMethod("collectorBasename", String.class);
        name.setAccessible(true);
        require(Boolean.TRUE.equals(name.invoke(null, "libm5-reference-child.so")), "normal collector basename");
        for (String invalid : new String[] {"../lib.so", "lib\\.so", "lib.dylib", "lib.so/other"})
            require(Boolean.FALSE.equals(name.invoke(null, invalid)), "invalid collector basename");
        try { BionicReferenceRunner.main(new String[0]); throw new AssertionError("desktop reference was accepted"); }
        catch (ClassNotFoundException expected) { /* Standalone desktop lacks Android framework. */ }
        catch (AssertionError expected) {
            require(expected.getMessage().contains("Android ART/bionic required"), "explicit platform rejection");
        }
        System.out.println("M5_REFERENCE_JAVA_PASS parsing_only=1 reference_performed=0");
    }
}
