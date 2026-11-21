// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Objects;

/** Strict numeric IPv4 and IPv6 address literal utilities that never trigger DNS resolution. */
public class InetAddresses {
    private InetAddresses() {
    }

    public static boolean isNumericAddress(String address) {
        Objects.requireNonNull(address, "address");
        return parseInternal(address) != null;
    }

    public static InetAddress parseNumericAddress(String address) {
        Objects.requireNonNull(address, "address");
        InetAddress parsed = parseInternal(address);
        if (parsed == null) {
            throw new IllegalArgumentException("Not a numeric IP address: " + address);
        }
        return parsed;
    }

    private static InetAddress parseInternal(String address) {
        if (address.isEmpty() || address.charAt(0) == '[' || address.charAt(address.length() - 1) == ']') {
            return null;
        }
        if (address.indexOf(':') >= 0) {
            return parseIpv6(address);
        }
        byte[] v4 = parseIpv4Bytes(address);
        if (v4 == null) {
            return null;
        }
        try {
            return InetAddress.getByAddress(null, v4);
        } catch (UnknownHostException impossible) {
            return null;
        }
    }

    static byte[] parseIpv4Bytes(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        byte[] out = new byte[4];
        int part = 0;
        int start = 0;
        int len = s.length();
        for (int i = 0; i <= len; i++) {
            if (i == len || s.charAt(i) == '.') {
                if (part >= 4) {
                    return null;
                }
                int segLen = i - start;
                if (segLen < 1 || segLen > 3) {
                    return null;
                }
                if (segLen > 1 && s.charAt(start) == '0') {
                    return null;
                }
                int val = 0;
                for (int j = start; j < i; j++) {
                    char c = s.charAt(j);
                    if (c < '0' || c > '9') {
                        return null;
                    }
                    val = val * 10 + (c - '0');
                }
                if (val > 255) {
                    return null;
                }
                out[part++] = (byte) val;
                start = i + 1;
            }
        }
        return part == 4 ? out : null;
    }

    private static InetAddress parseIpv6(String input) {
        String addrPart = input;
        String scopePart = null;
        int pct = input.indexOf('%');
        if (pct >= 0) {
            if (pct == 0 || pct == input.length() - 1 || input.indexOf('%', pct + 1) >= 0) {
                return null;
            }
            addrPart = input.substring(0, pct);
            scopePart = input.substring(pct + 1);
        }

        byte[] bytes = parseIpv6Bytes(addrPart);
        if (bytes == null) {
            return null;
        }
        try {
            if (scopePart == null) {
                return InetAddress.getByAddress(null, bytes);
            }
            boolean allDigits = true;
            for (int i = 0; i < scopePart.length(); i++) {
                char c = scopePart.charAt(i);
                if (c < '0' || c > '9') {
                    allDigits = false;
                    break;
                }
            }
            if (allDigits) {
                int scopeId;
                try {
                    scopeId = Integer.parseInt(scopePart);
                } catch (NumberFormatException e) {
                    return null;
                }
                if (scopeId < 0) {
                    return null;
                }
                return Inet6Address.getByAddress(null, bytes, scopeId);
            }
            try {
                NetworkInterface nif = NetworkInterface.getByName(scopePart);
                if (nif != null) {
                    return Inet6Address.getByAddress(null, bytes, nif);
                }
            } catch (SocketException ignored) {
            }
            return null;
        } catch (UnknownHostException impossible) {
            return null;
        }
    }

    static byte[] parseIpv6Bytes(String s) {
        if (s.length() < 2) {
            return null;
        }
        int doubleColon = s.indexOf("::");
        if (doubleColon >= 0 && s.indexOf("::", doubleColon + 1) >= 0) {
            return null;
        }
        if (s.charAt(0) == ':' && doubleColon != 0) {
            return null;
        }
        if (s.charAt(s.length() - 1) == ':' && doubleColon != s.length() - 2) {
            return null;
        }

        String headStr;
        String tailStr;
        if (doubleColon >= 0) {
            headStr = s.substring(0, doubleColon);
            tailStr = s.substring(doubleColon + 2);
        } else {
            headStr = s;
            tailStr = null;
        }

        byte[] trailingV4 = null;
        String v4Source = tailStr != null ? tailStr : headStr;
        if (v4Source.indexOf('.') >= 0) {
            int lastColon = v4Source.lastIndexOf(':');
            String v4Text = lastColon >= 0 ? v4Source.substring(lastColon + 1) : v4Source;
            trailingV4 = parseIpv4Bytes(v4Text);
            if (trailingV4 == null) {
                return null;
            }
            String prefix = lastColon >= 0 ? v4Source.substring(0, lastColon) : "";
            if (tailStr != null) {
                tailStr = prefix;
            } else {
                headStr = prefix;
            }
        }

        int[] headWords = parseHextets(headStr);
        if (headWords == null) {
            return null;
        }
        int[] tailWords = tailStr == null ? new int[0] : parseHextets(tailStr);
        if (tailWords == null) {
            return null;
        }

        int v4Words = trailingV4 != null ? 2 : 0;
        int totalWords = headWords.length + tailWords.length + v4Words;
        if (doubleColon >= 0) {
            if (totalWords >= 8) {
                return null;
            }
        } else if (totalWords != 8) {
            return null;
        }

        byte[] out = new byte[16];
        int pos = 0;
        for (int w : headWords) {
            out[pos++] = (byte) ((w >> 8) & 0xFF);
            out[pos++] = (byte) (w & 0xFF);
        }
        if (doubleColon >= 0) {
            int zeros = (8 - totalWords) * 2;
            pos += zeros;
        }
        for (int w : tailWords) {
            out[pos++] = (byte) ((w >> 8) & 0xFF);
            out[pos++] = (byte) (w & 0xFF);
        }
        if (trailingV4 != null) {
            System.arraycopy(trailingV4, 0, out, pos, 4);
        }
        return out;
    }

    private static int[] parseHextets(String part) {
        if (part.isEmpty()) {
            return new int[0];
        }
        String[] tokens = part.split(":", -1);
        int[] words = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            String tok = tokens[i];
            if (tok.isEmpty() || tok.length() > 4) {
                return null;
            }
            int val = 0;
            for (int j = 0; j < tok.length(); j++) {
                int d = Character.digit(tok.charAt(j), 16);
                if (d < 0) {
                    return null;
                }
                val = (val << 4) | d;
            }
            words[i] = val;
        }
        return words;
    }
}
