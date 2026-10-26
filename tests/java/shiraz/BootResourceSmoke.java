// SPDX-License-Identifier: GPL-3.0-only
package shiraz;

import java.io.File;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.security.Security;

/** Boot resources must follow the physical package after relocation. */
public final class BootResourceSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new AssertionError("Expected physical boot archive");
        URL resource = Security.class.getResource("security.properties");
        if (resource == null) throw new AssertionError("Missing boot security resource");
        JarURLConnection connection = (JarURLConnection) resource.openConnection();
        File actual = new File(connection.getJarFileURL().toURI()).getCanonicalFile();
        if (!actual.equals(new File(args[0]).getCanonicalFile()))
            throw new AssertionError("Resource came from another boot archive: " + actual);
        try (InputStream stream = resource.openStream()) {
            if (stream.read() < 0) throw new AssertionError("Empty security resource");
        }
        if (Security.getProvider("wolfJSSE") == null ||
                Security.getProvider("wolfJSSE").getService("SSLContext", "TLS") == null)
            throw new AssertionError("Pinned TLS provider unavailable after relocation");
        System.out.println("SHIRAZ_BOOT_RELOCATION_OK");
    }
}
