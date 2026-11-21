// SPDX-License-Identifier: GPL-3.0-only
package android.net.http;

import java.util.List;
import java.util.Map;

public abstract class HeaderBlock {
    public HeaderBlock() {}

    public abstract List<Map.Entry<String, String>> getAsList();

    public abstract Map<String, List<String>> getAsMap();
}
