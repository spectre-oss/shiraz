// SPDX-License-Identifier: GPL-3.0-only
import android.content.res.Resources;
import java.nio.file.Paths;

public final class ColorResourceFailures {
    public static void main(String[] args) throws Exception {
        String apk = Paths.get(args[0]).toRealPath().toString();
        long descriptors = ResourceLifetime.descriptors(apk), mappings = ResourceLifetime.mappings(apk);
        for (int i = 0; i < 32; ++i) {
            Resources resources = Resources.shirazOpen(apk, 96, 160);
            try {
                int malformed = resources.getIdentifier("selector_plain", "color", "org.shiraz.fixture");
                try { resources.getColor(malformed); throw new AssertionError("Malformed compiled selector accepted"); }
                catch (Resources.NotFoundException expected) { }
                if (resources.getColor(resources.getIdentifier("accent", "color", "org.shiraz.fixture")) != 0xff123456)
                    throw new AssertionError("Malformed XML corrupted subsequent resource reads");
            } finally { resources.shirazClose(); }
        }
        if (ResourceLifetime.descriptors(apk) != descriptors || ResourceLifetime.mappings(apk) != mappings)
            throw new AssertionError("Malformed color XML leaked its package");
        System.out.println("MALFORMED_COLOR_RESOURCE_CLEANUP_OK");
    }
}
