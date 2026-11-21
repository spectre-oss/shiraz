// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** RFC 6068 mailto: URI parser. */
public class MailTo {
    public static final String MAILTO_SCHEME = "mailto:";

    private static final String TO = "to";
    private static final String BODY = "body";
    private static final String CC = "cc";
    private static final String SUBJECT = "subject";

    private final HashMap<String, String> mHeaders = new HashMap<String, String>();

    private MailTo() {
    }

    public static boolean isMailTo(String url) {
        return url != null
                && url.length() >= MAILTO_SCHEME.length()
                && url.regionMatches(true, 0, MAILTO_SCHEME, 0, MAILTO_SCHEME.length());
    }

    public static MailTo parse(String url) throws ParseException {
        if (url == null) {
            throw new NullPointerException("url");
        }
        if (!isMailTo(url)) {
            throw new ParseException("Not a mailto scheme");
        }
        String noFrag = url;
        int hashIdx = noFrag.indexOf('#');
        if (hashIdx >= 0) {
            noFrag = noFrag.substring(0, hashIdx);
        }
        String rest = noFrag.substring(MAILTO_SCHEME.length());
        String addrPart;
        String queryPart;
        int qIdx = rest.indexOf('?');
        if (qIdx < 0) {
            addrPart = Uri.decode(rest);
            queryPart = null;
        } else {
            addrPart = Uri.decode(rest.substring(0, qIdx));
            queryPart = rest.substring(qIdx + 1);
        }

        MailTo m = new MailTo();
        if (queryPart != null && !queryPart.isEmpty()) {
            String[] queries = queryPart.split("&");
            for (String q : queries) {
                if (q.isEmpty()) {
                    continue;
                }
                int eq = q.indexOf('=');
                String name;
                String val;
                if (eq < 0) {
                    name = Uri.decode(q).toLowerCase(Locale.ROOT);
                    val = null;
                } else {
                    name = Uri.decode(q.substring(0, eq)).toLowerCase(Locale.ROOT);
                    val = Uri.decode(q.substring(eq + 1));
                }
                if (!name.isEmpty()) {
                    m.mHeaders.put(name, val);
                }
            }
        }

        if (addrPart != null && !addrPart.isEmpty()) {
            String existingTo = m.mHeaders.get(TO);
            if (existingTo != null && !existingTo.isEmpty()) {
                addrPart = addrPart + ", " + existingTo;
            }
            m.mHeaders.put(TO, addrPart);
        }

        return m;
    }

    public String getTo() {
        return mHeaders.get(TO);
    }

    public String getCc() {
        return mHeaders.get(CC);
    }

    public String getSubject() {
        return mHeaders.get(SUBJECT);
    }

    public String getBody() {
        return mHeaders.get(BODY);
    }

    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(mHeaders);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(MAILTO_SCHEME);
        sb.append('?');
        for (Map.Entry<String, String> header : mHeaders.entrySet()) {
            sb.append(Uri.encode(header.getKey()));
            sb.append('=');
            sb.append(Uri.encode(header.getValue()));
            sb.append('&');
        }
        return sb.toString();
    }
}
