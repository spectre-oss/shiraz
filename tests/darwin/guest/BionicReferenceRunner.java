// SPDX-License-Identifier: GPL-3.0-only
// Android-only supervisor. It emits evidence only after actual native child waits.
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class BionicReferenceRunner {
    private static final int MAX_FILE = 16 * 1024 * 1024;
    private static final int MAX_OUTPUT = 65536;
    private static final String RESULT = "M5_DIFFERENTIAL_RESULT ";
    private static final String READY = "M5_DIFFERENTIAL_STACK_CHILD_READY sha256=";
    private static native String nChild(String[] argv);

    // Android supplies org.json; reflection keeps the corpus build independent
    // of an Android SDK/framework JAR and never substitutes a desktop parser.
    private static final class Json {
        final Object value;
        static Class<?> type;
        static Constructor<?> empty, parsed;
        static Method get, put;
        static void initialize() throws Exception {
            type = Class.forName("org.json.JSONObject");
            empty = type.getConstructor();
            parsed = type.getConstructor(String.class);
            get = type.getMethod("get", String.class);
            put = type.getMethod("put", String.class, Object.class);
        }
        Json() throws Exception { value = empty.newInstance(); }
        Json(String text) throws Exception {
            if (text == null || text.length() > 2 * MAX_OUTPUT + 4096)
                throw new AssertionError("collector JSON exceeds bound");
            value = parsed.newInstance(text);
        }
        Json(Object object) {
            if (!type.isInstance(object)) throw new AssertionError("JSON object required");
            value = object;
        }
        Object value(String name) throws Exception { return get.invoke(value, name); }
        String string(String name) throws Exception {
            Object item = value(name);
            if (!(item instanceof String)) throw new AssertionError("string required: " + name);
            return (String)item;
        }
        int integer(String name) throws Exception {
            Object item = value(name);
            if (!(item instanceof Integer) && !(item instanceof Long))
                throw new AssertionError("integer required: " + name);
            long number = ((Number)item).longValue();
            if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE)
                throw new AssertionError("integer outside range: " + name);
            return (int)number;
        }
        boolean bool(String name) throws Exception {
            Object item = value(name);
            if (!(item instanceof Boolean)) throw new AssertionError("boolean required: " + name);
            return (Boolean)item;
        }
        Json object(String name) throws Exception { return new Json(value(name)); }
        Json add(String name, Object item) throws Exception {
            put.invoke(value, name, item instanceof Json ? ((Json)item).value : item);
            return this;
        }
        public String toString() { return value.toString(); }
    }

    private static String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty() || value.length() > 4096)
            throw new AssertionError("missing/bounded property: " + name);
        return value;
    }
    private static String expectedHash(String name) {
        String hash = required(name);
        if (!hash.matches("[0-9a-f]{64}")) throw new AssertionError("SHA256 required: " + name);
        return hash;
    }
    private static File scopedFile(String name) throws Exception {
        File file = new File(required(name));
        if (!file.isAbsolute() || !file.isFile() || !file.canRead()
                || !file.getPath().equals(file.getCanonicalPath()))
            throw new AssertionError("explicit canonical readable file required: " + name);
        String parent = file.getParent();
        if (parent == null || !parent.matches("/data/local/tmp/shiraz-m5-[A-Za-z0-9][A-Za-z0-9._-]{0,95}"))
            throw new AssertionError("fixture file is outside the scoped reference directory");
        return file;
    }
    private static String hex(byte[] bytes) {
        final char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; ++i) {
            result[2 * i] = digits[(bytes[i] & 255) >>> 4];
            result[2 * i + 1] = digits[bytes[i] & 15];
        }
        return new String(result);
    }
    private static String sha256(File file) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        long size = 0;
        byte[] buffer = new byte[65536];
        try (FileInputStream input = new FileInputStream(file)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                size += count;
                if (size > MAX_FILE) throw new AssertionError("fixture input exceeds bound");
                hash.update(buffer, 0, count);
            }
        }
        if (size == 0) throw new AssertionError("empty fixture input");
        return hex(hash.digest());
    }
    private static void verify(File file, String expected) throws Exception {
        if (!expected.equals(sha256(file))) throw new AssertionError("fixture hash mismatch: " + file.getName());
    }
    private static byte[] decodeOutput(String text) {
        if (text.length() > 2 * MAX_OUTPUT || (text.length() & 1) != 0)
            throw new AssertionError("collector output exceeds bound or has odd hex length");
        byte[] result = new byte[text.length() / 2];
        for (int i = 0; i < result.length; ++i) {
            int hi = Character.digit(text.charAt(2 * i), 16);
            int lo = Character.digit(text.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) throw new AssertionError("collector output is not hex");
            result[i] = (byte)((hi << 4) | lo);
        }
        return result;
    }
    private static String utf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    private static boolean collectorBasename(String name) {
        return name.matches("[A-Za-z0-9._-]+[.]so");
    }
    private static String oneMarker(String text, String marker) {
        String found = null;
        for (String line : text.split("\\r?\\n", -1)) {
            if (!line.startsWith(marker)) continue;
            if (found != null) throw new AssertionError("duplicate child evidence marker");
            found = line.substring(marker.length());
        }
        if (found == null) throw new AssertionError("child evidence marker absent");
        return found;
    }
    private static String[] command(File library, File jar, String libraryHash, String jarHash, String mode) {
        return new String[] {"/system/bin/app_process64", "-Dguest.fixture=" + library,
                "-Dguest.fixture.jar=" + jar, "-Dguest.fixture.sha256=" + libraryHash,
                "-Dguest.fixture.jar.sha256=" + jarHash, "/system/bin",
                "BionicDifferentialFixture", mode};
    }
    private static final class Child {
        final int code, raw;
        final boolean timeout;
        final byte[] output;
        final String text;
        Child(String[] argv) throws Exception {
            Json report = new Json(nChild(argv));
            code = report.integer("returncode");
            raw = report.integer("raw_wait_status");
            timeout = report.bool("timed_out");
            output = decodeOutput(report.string("output_hex"));
            text = utf8(output);
            if (raw < 0 || raw > 65535) throw new AssertionError("invalid raw wait status");
        }
        Json evidence() throws Exception {
            return new Json().add("returncode", code).add("raw_wait_status", raw)
                    .add("timed_out", timeout).add("output_bytes", output.length)
                    .add("output_hex", hex(output))
                    .add("output_sha256", hex(MessageDigest.getInstance("SHA-256").digest(output)));
        }
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 0) throw new AssertionError("reference supervisor takes no command arguments");
        int sdk = Class.forName("android.os.Build$VERSION").getField("SDK_INT").getInt(null);
        String arch = System.getProperty("os.arch", "");
        if (!"Linux".equals(System.getProperty("os.name"))
                || !"Dalvik".equals(System.getProperty("java.vm.name")) || sdk < 27
                || !("aarch64".equals(arch) || "arm64".equals(arch)))
            throw new AssertionError("actual ARM64 API27+ Android ART/bionic required");
        Json.initialize();
        File library = scopedFile("guest.fixture");
        File jar = scopedFile("guest.fixture.jar");
        File collector = scopedFile("guest.reference.collector");
        if (!"libm5-differential.so".equals(library.getName()) || !"fixture.jar".equals(jar.getName())
                || !collectorBasename(collector.getName())
                || !library.getParent().equals(jar.getParent()) || !jar.getParent().equals(collector.getParent())
                || !jar.getPath().equals(System.getenv("CLASSPATH")))
            throw new AssertionError("fixed same-directory corpus and exact CLASSPATH required");
        String libraryHash = expectedHash("guest.fixture.sha256");
        String jarHash = expectedHash("guest.fixture.jar.sha256");
        String collectorHash = expectedHash("guest.reference.collector.sha256");
        verify(library, libraryHash); verify(jar, jarHash); verify(collector, collectorHash);
        System.load(collector.getPath());
        Child run = new Child(command(library, jar, libraryHash, jarHash, "run"));
        if (run.timeout || run.code != 0 || run.raw != 0)
            throw new AssertionError("reference positive child failed");
        Json positive = new Json(oneMarker(run.text, RESULT));
        Json platform = positive.object("platform");
        if (positive.integer("schema") != 1 || !libraryHash.equals(positive.string("fixture_sha256"))
                || !jarHash.equals(positive.string("jar_sha256")) || !"Linux".equals(platform.string("os"))
                || !"Dalvik".equals(platform.string("vm")) || platform.integer("android_sdk") < 27
                || positive.object("native").integer("schema") != 1
                || !"pass".equals(positive.object("native").string("status")))
            throw new AssertionError("positive child did not report the exact Android corpus");
        Child failure = new Child(command(library, jar, libraryHash, jarHash, "stack-failure"));
        if (failure.timeout || failure.code != -6 || (failure.raw & 127) != 6
                || (failure.raw >>> 8) != 0 || !libraryHash.equals(oneMarker(failure.text, READY)))
            throw new AssertionError("stack-failure child lacks exact readiness and actual waitpid SIGABRT");
        verify(library, libraryHash); verify(jar, jarHash); verify(collector, collectorHash);
        Json evidence = new Json().add("schema", 1).add("collection_method", "native-waitpid")
                .add("collector_sha256", collectorHash)
                .add("fixture_sha256", libraryHash).add("jar_sha256", jarHash)
                .add("run", run.evidence().add("report", positive))
                .add("stack_failure", failure.evidence().add("ready_fixture_sha256", libraryHash));
        String result = evidence.toString();
        if (result.length() > 512 * 1024) throw new AssertionError("reference result exceeds import bound");
        System.out.println("M5_BIONIC_REFERENCE_RESULT " + result);
        System.out.flush();
    }
}
