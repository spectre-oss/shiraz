// SPDX-License-Identifier: GPL-3.0-only
package android.sax;

import org.xml.sax.Attributes;

public interface StartElementListener {
    void start(Attributes attributes);
}
