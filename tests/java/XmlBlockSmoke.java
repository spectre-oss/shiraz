// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.XmlResourceParser;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.zip.ZipFile;
import org.xmlpull.v1.XmlPullParser;

public final class XmlBlockSmoke {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        byte[] xml;
        try (ZipFile apk = new ZipFile("build/macos-apk/minimal.apk");
             InputStream input = apk.getInputStream(apk.getEntry("AndroidManifest.xml"));
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[1024];
            int count;
            while ((count = input.read(chunk)) != -1) bytes.write(chunk, 0, count);
            xml = bytes.toByteArray();
        }
        Class<?> type = Class.forName("android.content.res.XmlBlock");
        Constructor<?> whole = type.getDeclaredConstructor(byte[].class);
        Constructor<?> slice = type.getDeclaredConstructor(byte[].class, int.class, int.class);
        Method newParser = type.getDeclaredMethod("newParser"), close = type.getDeclaredMethod("close");
        whole.setAccessible(true); slice.setAccessible(true); newParser.setAccessible(true); close.setAccessible(true);
        byte[] padded = new byte[xml.length + 12];
        System.arraycopy(xml, 0, padded, 5, xml.length);
        Object block = slice.newInstance(padded, 5, xml.length);
        Arrays.fill(padded, (byte) 0);
        XmlResourceParser first = (XmlResourceParser) newParser.invoke(block);
        XmlResourceParser second = (XmlResourceParser) newParser.invoke(block);
        check(first.next() == XmlPullParser.START_TAG && "manifest".equals(first.getName()), "first cursor");
        check(second.getEventType() == XmlPullParser.START_DOCUMENT, "independent cursor");
        close.invoke(block); close.invoke(block);
        check(newParser.invoke(block) == null, "closed block must not create a cursor");
        check(second.next() == XmlPullParser.START_TAG && "manifest".equals(second.getName()), "cursor survives block close");
        first.close(); second.close();
        Object fresh = whole.newInstance((Object) xml);
        close.invoke(fresh);
        for (int[] range : new int[][] {{-1, 1}, {0, -1}, {xml.length, 1}, {Integer.MAX_VALUE, 2}}) {
            try { slice.newInstance(xml, range[0], range[1]); throw new AssertionError("invalid slice accepted"); }
            catch (InvocationTargetException expected) { check(expected.getCause() instanceof IndexOutOfBoundsException, "slice error"); }
        }
        try { whole.newInstance((Object) new byte[] {0, 1, 2}); throw new AssertionError("invalid XML accepted"); }
        catch (InvocationTargetException expected) { check(expected.getCause() instanceof IllegalArgumentException, "invalid XML error"); }
        System.out.println("XML_BLOCK_SMOKE_PASS");
    }
}
