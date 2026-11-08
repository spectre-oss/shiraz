// SPDX-License-Identifier: GPL-3.0-only
// Identical DEX is run by Darwin ART and real Android app_process64.
import java.io.FileInputStream;
import java.security.MessageDigest;

public final class BionicDifferentialFixture {
    private static native String nRun();
    private static native void nCorruptGuard();

    private static String sha256(String path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[65536];
        long total = 0;
        try (FileInputStream input = new FileInputStream(path)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > 16 * 1024 * 1024) throw new AssertionError("fixture file exceeds bound");
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02x", value & 255));
        return result.toString();
    }

    private static String json(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    public static void main(String[] arguments) throws Exception {
        String library = System.getProperty("guest.fixture");
        String jar = System.getProperty("guest.fixture.jar");
        if (library == null || jar == null || arguments.length != 1)
            throw new AssertionError("explicit fixture, jar and run/stack-failure mode required");
        String libraryHash = sha256(library), jarHash = sha256(jar);
        String expectedLibrary = System.getProperty("guest.fixture.sha256");
        String expectedJar = System.getProperty("guest.fixture.jar.sha256");
        if (!libraryHash.equals(expectedLibrary) || !jarHash.equals(expectedJar))
            throw new AssertionError("fixture bytes do not match corpus hashes");
        int sdk = -1;
        try {
            sdk = Class.forName("android.os.Build$VERSION").getField("SDK_INT").getInt(null);
        } catch (ClassNotFoundException expected) {
            // Standalone ART has no Android framework; do not invent an SDK.
        }
        System.load(library);
        if (arguments[0].equals("stack-failure")) {
            System.out.println("M5_DIFFERENTIAL_STACK_CHILD_READY sha256=" + libraryHash);
            System.out.flush();
            nCorruptGuard();
            throw new AssertionError("stack corruption returned normally");
        }
        if (!arguments[0].equals("run")) throw new AssertionError("unknown mode");
        String report = nRun();
        String envelope = "{\"schema\":1,\"fixture_sha256\":" + json(libraryHash)
                + ",\"jar_sha256\":" + json(jarHash)
                + ",\"platform\":{\"os\":" + json(System.getProperty("os.name", ""))
                + ",\"vm\":" + json(System.getProperty("java.vm.name", ""))
                + ",\"android_sdk\":" + sdk + "},\"native\":" + report + "}";
        System.out.println("M5_DIFFERENTIAL_RESULT " + envelope);
        if (!report.contains("\"status\":\"pass\""))
            throw new AssertionError("native differential fixture failed: " + report);
    }
}
