// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import java.util.List;

public class MediaCommunicationManager {
    public MediaCommunicationManager() {}

    public int getVersion() {
        return 1;
    }

    public List<Session2Token> getSession2Tokens() {
        return MediaSession2.shirazGetActiveTokens();
    }
}
