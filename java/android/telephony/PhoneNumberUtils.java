// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TtsSpan;
import android.util.SparseIntArray;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PhoneNumberUtils {
    public static final char PAUSE = ',';
    public static final char WAIT = ';';
    public static final char WILD = 'N';

    public static final int TOA_International = 0x91;
    public static final int TOA_Unknown = 0x81;

    public static final int FORMAT_UNKNOWN = 0;
    public static final int FORMAT_NANP = 1;
    public static final int FORMAT_JAPAN = 2;

    public static final int BCD_EXTENDED_TYPE_EF_ADN = 1;
    public static final int BCD_EXTENDED_TYPE_CALLED_PARTY = 2;

    private static final int MIN_MATCH = 7;
    private static final Pattern GLOBAL_PHONE_NUMBER_PATTERN =
            Pattern.compile("[\\+]?[0-9.-]+");

    private static final String[] NANP_COUNTRIES = new String[] {
        "US", "CA", "AS", "AI", "AG", "BS", "BB", "BM", "VG", "KY",
        "DM", "DO", "GD", "GU", "JM", "PR", "MS", "MP", "KN", "LC",
        "VC", "TT", "TC", "VI"
    };

    private static final Map<String, String> COUNTRY_CALLING_CODES = new HashMap<String, String>();
    static {
        for (int i = 0; i < NANP_COUNTRIES.length; i++) {
            COUNTRY_CALLING_CODES.put(NANP_COUNTRIES[i], "1");
        }
        COUNTRY_CALLING_CODES.put("GB", "44");
        COUNTRY_CALLING_CODES.put("DE", "49");
        COUNTRY_CALLING_CODES.put("FR", "33");
        COUNTRY_CALLING_CODES.put("IT", "39");
        COUNTRY_CALLING_CODES.put("ES", "34");
        COUNTRY_CALLING_CODES.put("JP", "81");
        COUNTRY_CALLING_CODES.put("KR", "82");
        COUNTRY_CALLING_CODES.put("CN", "86");
        COUNTRY_CALLING_CODES.put("IN", "91");
        COUNTRY_CALLING_CODES.put("AU", "61");
        COUNTRY_CALLING_CODES.put("NZ", "64");
        COUNTRY_CALLING_CODES.put("BR", "55");
        COUNTRY_CALLING_CODES.put("MX", "52");
        COUNTRY_CALLING_CODES.put("RU", "7");
        COUNTRY_CALLING_CODES.put("NL", "31");
        COUNTRY_CALLING_CODES.put("CH", "41");
        COUNTRY_CALLING_CODES.put("SE", "46");
        COUNTRY_CALLING_CODES.put("NO", "47");
        COUNTRY_CALLING_CODES.put("DK", "45");
        COUNTRY_CALLING_CODES.put("FI", "358");
        COUNTRY_CALLING_CODES.put("PL", "48");
        COUNTRY_CALLING_CODES.put("IE", "353");
        COUNTRY_CALLING_CODES.put("PT", "351");
        COUNTRY_CALLING_CODES.put("SG", "65");
        COUNTRY_CALLING_CODES.put("HK", "852");
        COUNTRY_CALLING_CODES.put("TW", "886");
        COUNTRY_CALLING_CODES.put("IL", "972");
        COUNTRY_CALLING_CODES.put("AE", "971");
        COUNTRY_CALLING_CODES.put("ZA", "27");
    }

    private static final SparseIntArray KEYPAD_MAP = new SparseIntArray();
    static {
        KEYPAD_MAP.put('a', '2'); KEYPAD_MAP.put('b', '2'); KEYPAD_MAP.put('c', '2');
        KEYPAD_MAP.put('A', '2'); KEYPAD_MAP.put('B', '2'); KEYPAD_MAP.put('C', '2');

        KEYPAD_MAP.put('d', '3'); KEYPAD_MAP.put('e', '3'); KEYPAD_MAP.put('f', '3');
        KEYPAD_MAP.put('D', '3'); KEYPAD_MAP.put('E', '3'); KEYPAD_MAP.put('F', '3');

        KEYPAD_MAP.put('g', '4'); KEYPAD_MAP.put('h', '4'); KEYPAD_MAP.put('i', '4');
        KEYPAD_MAP.put('G', '4'); KEYPAD_MAP.put('H', '4'); KEYPAD_MAP.put('I', '4');

        KEYPAD_MAP.put('j', '5'); KEYPAD_MAP.put('k', '5'); KEYPAD_MAP.put('l', '5');
        KEYPAD_MAP.put('J', '5'); KEYPAD_MAP.put('K', '5'); KEYPAD_MAP.put('L', '5');

        KEYPAD_MAP.put('m', '6'); KEYPAD_MAP.put('n', '6'); KEYPAD_MAP.put('o', '6');
        KEYPAD_MAP.put('M', '6'); KEYPAD_MAP.put('N', '6'); KEYPAD_MAP.put('O', '6');

        KEYPAD_MAP.put('p', '7'); KEYPAD_MAP.put('q', '7'); KEYPAD_MAP.put('r', '7'); KEYPAD_MAP.put('s', '7');
        KEYPAD_MAP.put('P', '7'); KEYPAD_MAP.put('Q', '7'); KEYPAD_MAP.put('R', '7'); KEYPAD_MAP.put('S', '7');

        KEYPAD_MAP.put('t', '8'); KEYPAD_MAP.put('u', '8'); KEYPAD_MAP.put('v', '8');
        KEYPAD_MAP.put('T', '8'); KEYPAD_MAP.put('U', '8'); KEYPAD_MAP.put('V', '8');

        KEYPAD_MAP.put('w', '9'); KEYPAD_MAP.put('x', '9'); KEYPAD_MAP.put('y', '9'); KEYPAD_MAP.put('z', '9');
        KEYPAD_MAP.put('W', '9'); KEYPAD_MAP.put('X', '9'); KEYPAD_MAP.put('Y', '9'); KEYPAD_MAP.put('Z', '9');
    }

    public PhoneNumberUtils() {}

    public static boolean isISODigit(char c) {
        return c >= '0' && c <= '9';
    }

    public static final boolean is12Key(char c) {
        return (c >= '0' && c <= '9') || c == '*' || c == '#';
    }

    public static final boolean isDialable(char c) {
        return (c >= '0' && c <= '9') || c == '*' || c == '#' || c == '+' || c == WILD;
    }

    public static final boolean isReallyDialable(char c) {
        return (c >= '0' && c <= '9') || c == '*' || c == '#' || c == '+';
    }

    public static final boolean isNonSeparator(char c) {
        return (c >= '0' && c <= '9') || c == '*' || c == '#' || c == '+'
                || c == WILD || c == WAIT || c == PAUSE;
    }

    public static final boolean isStartsPostDial(char c) {
        return c == PAUSE || c == WAIT;
    }

    public static String getNumberFromIntent(Intent intent, Context context) {
        if (intent == null) {
            return null;
        }
        Uri uri = intent.getData();
        if (uri == null) {
            return null;
        }
        String scheme = uri.getScheme();
        if ("tel".equals(scheme) || "sip".equals(scheme) || "voicemail".equals(scheme)) {
            return uri.getSchemeSpecificPart();
        }
        return null;
    }

    public static String extractNetworkPortion(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        int len = phoneNumber.length();
        StringBuilder ret = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = phoneNumber.charAt(i);
            int digit = Character.digit(c, 10);
            if (digit != -1) {
                ret.append(digit);
            } else if (c == '+') {
                if (ret.length() == 0) {
                    ret.append(c);
                }
            } else if (isDialable(c)) {
                ret.append(c);
            } else if (isStartsPostDial(c)) {
                break;
            }
        }
        return ret.toString();
    }

    public static String stripSeparators(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        int len = phoneNumber.length();
        StringBuilder ret = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = phoneNumber.charAt(i);
            int digit = Character.digit(c, 10);
            if (digit != -1) {
                ret.append(digit);
            } else if (isNonSeparator(c)) {
                ret.append(c);
            }
        }
        return ret.toString();
    }

    public static String extractPostDialPortion(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        int trimIndex;
        int s = phoneNumber.length();
        for (trimIndex = 0; trimIndex < s; trimIndex++) {
            char c = phoneNumber.charAt(trimIndex);
            if (isStartsPostDial(c)) {
                break;
            }
        }
        StringBuilder ret = new StringBuilder();
        for (; trimIndex < s; trimIndex++) {
            char c = phoneNumber.charAt(trimIndex);
            if (isNonSeparator(c)) {
                ret.append(c);
            }
        }
        return ret.toString();
    }

    public static boolean compare(String a, String b) {
        return compareLoosely(a, b);
    }

    public static boolean compare(Context context, String a, String b) {
        return compareLoosely(a, b);
    }

    private static boolean compareLoosely(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.length() == 0 || b.length() == 0) {
            return false;
        }
        int ia = indexOfLastNetworkChar(a);
        int ib = indexOfLastNetworkChar(b);
        int matched = 0;
        int numNonDialableCharsInA = 0;
        int numNonDialableCharsInB = 0;

        while (ia >= 0 && ib >= 0) {
            char ca = a.charAt(ia);
            boolean skipCmp = false;
            if (!isDialable(ca)) {
                ia--;
                skipCmp = true;
                numNonDialableCharsInA++;
            }
            char cb = b.charAt(ib);
            if (!isDialable(cb)) {
                ib--;
                skipCmp = true;
                numNonDialableCharsInB++;
            }
            if (!skipCmp) {
                if (cb != ca && ca != WILD && cb != WILD) {
                    break;
                }
                ia--;
                ib--;
                matched++;
            }
        }

        if (matched < MIN_MATCH) {
            int effectiveALen = a.length() - numNonDialableCharsInA;
            int effectiveBLen = b.length() - numNonDialableCharsInB;
            if (effectiveALen == effectiveBLen && effectiveALen == matched) {
                return true;
            }
            return false;
        }

        if (matched >= MIN_MATCH && (ia < 0 || ib < 0)) {
            return true;
        }
        if (matchIntlPrefix(a, ia + 1) && matchIntlPrefix(b, ib + 1)) {
            return true;
        }
        if (matchTrunkPrefix(a, ia + 1) && matchIntlPrefixAndCC(b, ib + 1)) {
            return true;
        }
        if (matchTrunkPrefix(b, ib + 1) && matchIntlPrefixAndCC(a, ia + 1)) {
            return true;
        }
        return false;
    }

    private static int indexOfLastNetworkChar(String a) {
        int origLength = a.length();
        int pIndex = minPositive(a.indexOf(PAUSE), a.indexOf(WAIT));
        if (pIndex < 0) {
            return origLength - 1;
        }
        return pIndex - 1;
    }

    private static int minPositive(int a, int b) {
        if (a >= 0 && b >= 0) {
            return (a < b) ? a : b;
        } else if (a >= 0) {
            return a;
        } else if (b >= 0) {
            return b;
        }
        return -1;
    }

    private static boolean matchIntlPrefix(String a, int len) {
        int state = 0;
        for (int i = 0; i < len; i++) {
            char c = a.charAt(i);
            switch (state) {
                case 0:
                    if (c == '+') state = 1;
                    else if (c == '0') state = 2;
                    else if (isNonSeparator(c)) return false;
                    break;
                case 2:
                    if (c == '0') state = 3;
                    else if (c == '1') state = 4;
                    else if (isNonSeparator(c)) return false;
                    break;
                case 4:
                    if (c == '1') state = 5;
                    else if (isNonSeparator(c)) return false;
                    break;
                default:
                    if (isNonSeparator(c)) return false;
                    break;
            }
        }
        return state == 1 || state == 3 || state == 5;
    }

    private static boolean matchTrunkPrefix(String a, int len) {
        boolean found = false;
        for (int i = 0; i < len; i++) {
            char c = a.charAt(i);
            if (c == '0' && !found) {
                found = true;
            } else if (isNonSeparator(c)) {
                return false;
            }
        }
        return found;
    }

    private static boolean matchIntlPrefixAndCC(String a, int len) {
        int state = 0;
        for (int i = 0; i < len; i++) {
            char c = a.charAt(i);
            switch (state) {
                case 0:
                    if (c == '+') state = 1;
                    else if (c == '0') state = 2;
                    else if (isNonSeparator(c)) return false;
                    break;
                case 2:
                    if (c == '0') state = 3;
                    else if (c == '1') state = 4;
                    else if (isNonSeparator(c)) return false;
                    break;
                case 4:
                    if (c == '1') state = 5;
                    else if (isNonSeparator(c)) return false;
                    break;
                case 1:
                case 3:
                case 5:
                    if (isISODigit(c)) state = 6;
                    else if (isNonSeparator(c)) return false;
                    break;
                case 6:
                case 7:
                    if (isISODigit(c)) state++;
                    else if (isNonSeparator(c)) return false;
                    break;
                default:
                    if (isNonSeparator(c)) return false;
                    break;
            }
        }
        return state == 6 || state == 7 || state == 8;
    }

    public static String toCallerIDMinMatch(String phoneNumber) {
        String np = extractNetworkPortion(phoneNumber);
        return internalGetStrippedReversed(np, MIN_MATCH);
    }

    public static String getStrippedReversed(String phoneNumber) {
        String np = extractNetworkPortion(phoneNumber);
        if (np == null) {
            return null;
        }
        return internalGetStrippedReversed(np, np.length());
    }

    private static String internalGetStrippedReversed(String np, int numDigits) {
        if (np == null) {
            return null;
        }
        StringBuilder ret = new StringBuilder(numDigits);
        int length = np.length();
        for (int i = length - 1; i >= 0 && (length - i) <= numDigits; i--) {
            char c = np.charAt(i);
            ret.append(c);
        }
        return ret.toString();
    }

    public static String stringFromStringAndTOA(String s, int TOA) {
        if (s == null) {
            return null;
        }
        if (TOA == TOA_International && s.length() > 0 && s.charAt(0) != '+') {
            return "+" + s;
        }
        return s;
    }

    public static int toaFromString(String s) {
        if (s != null && s.length() > 0 && s.charAt(0) == '+') {
            return TOA_International;
        }
        return TOA_Unknown;
    }

    @Deprecated
    public static String calledPartyBCDToString(byte[] bytes, int offset, int length) {
        return calledPartyBCDToString(bytes, offset, length, BCD_EXTENDED_TYPE_EF_ADN);
    }

    public static String calledPartyBCDToString(
            byte[] bytes, int offset, int length, int bcdExtendedType) {
        boolean prependPlus = false;
        if (bytes == null || length < 2 || offset + length > bytes.length) {
            return "";
        }
        if ((bytes[offset] & 0xf0) == (TOA_International & 0xf0)) {
            prependPlus = true;
        }
        String ret = calledPartyBCDFragmentToString(bytes, offset + 1, length - 1, bcdExtendedType);
        if (prependPlus && ret.length() == 0) {
            return "";
        }
        if (prependPlus) {
            int plusIdx = ret.indexOf('+');
            if (plusIdx >= 0) {
                ret = ret.substring(0, plusIdx) + ret.substring(plusIdx + 1);
            }
            ret = "+" + ret;
        }
        return ret;
    }

    @Deprecated
    public static String calledPartyBCDFragmentToString(byte[] bytes, int offset, int length) {
        return calledPartyBCDFragmentToString(bytes, offset, length, BCD_EXTENDED_TYPE_EF_ADN);
    }

    public static String calledPartyBCDFragmentToString(
            byte[] bytes, int offset, int length, int bcdExtendedType) {
        if (bytes == null || length <= 0 || offset < 0 || offset + length > bytes.length) {
            return "";
        }
        StringBuilder ret = new StringBuilder(length * 2);
        for (int i = offset; i < offset + length; i++) {
            char c = bcdToChar((byte) (bytes[i] & 0x0f), bcdExtendedType);
            if (c == 0) {
                break;
            }
            ret.append(c);
            byte b = (byte) ((bytes[i] >> 4) & 0x0f);
            if (b == 0x0f && i + 1 == offset + length) {
                break;
            }
            c = bcdToChar(b, bcdExtendedType);
            if (c == 0) {
                break;
            }
            ret.append(c);
        }
        return ret.toString();
    }

    private static char bcdToChar(byte b, int bcdExtendedType) {
        if (b < 0xa) {
            return (char) ('0' + b);
        }
        if (bcdExtendedType == BCD_EXTENDED_TYPE_EF_ADN) {
            switch (b) {
                case 0xa: return '*';
                case 0xb: return '#';
                case 0xc: return PAUSE;
                case 0xd: return WILD;
                default: return 0;
            }
        } else if (bcdExtendedType == BCD_EXTENDED_TYPE_CALLED_PARTY) {
            switch (b) {
                case 0xa: return '*';
                case 0xb: return '#';
                case 0xc: return 'a';
                case 0xd: return 'b';
                case 0xe: return 'c';
                default: return 0;
            }
        }
        return 0;
    }

    private static int charToBCD(char c, int bcdExtendedType) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (bcdExtendedType == BCD_EXTENDED_TYPE_EF_ADN) {
            if (c == '*') return 0xa;
            if (c == '#') return 0xb;
            if (c == PAUSE) return 0xc;
            if (c == WILD) return 0xd;
            if (c == '+') return -1;
        } else {
            if (c == '*') return 0xa;
            if (c == '#') return 0xb;
            if (c == 'a') return 0xc;
            if (c == 'b') return 0xd;
            if (c == 'c') return 0xe;
        }
        throw new RuntimeException("invalid char for BCD " + c);
    }

    public static boolean isWellFormedSmsAddress(String address) {
        String networkPortion = extractNetworkPortion(address);
        return (!(networkPortion.equals("+") || TextUtils.isEmpty(networkPortion)))
                && isDialable(networkPortion);
    }

    private static boolean isDialable(String address) {
        for (int i = 0, count = address.length(); i < count; i++) {
            if (!isDialable(address.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isGlobalPhoneNumber(String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return false;
        }
        Matcher match = GLOBAL_PHONE_NUMBER_PATTERN.matcher(phoneNumber);
        return match.matches();
    }

    public static byte[] networkPortionToCalledPartyBCD(String s) {
        String networkPortion = extractNetworkPortion(s);
        return numberToCalledPartyBCDHelper(networkPortion, false, BCD_EXTENDED_TYPE_EF_ADN);
    }

    public static byte[] networkPortionToCalledPartyBCDWithLength(String s) {
        String networkPortion = extractNetworkPortion(s);
        return numberToCalledPartyBCDHelper(networkPortion, true, BCD_EXTENDED_TYPE_EF_ADN);
    }

    @Deprecated
    public static byte[] numberToCalledPartyBCD(String number) {
        return numberToCalledPartyBCD(number, BCD_EXTENDED_TYPE_EF_ADN);
    }

    public static byte[] numberToCalledPartyBCD(String number, int bcdExtendedType) {
        return numberToCalledPartyBCDHelper(number, false, bcdExtendedType);
    }

    private static byte[] numberToCalledPartyBCDHelper(
            String number, boolean includeLength, int bcdExtendedType) {
        if (TextUtils.isEmpty(number)) {
            return null;
        }
        int numberLenReal = number.length();
        int numberLenEffective = numberLenReal;
        boolean hasPlus = number.indexOf('+') != -1;
        if (hasPlus) {
            numberLenEffective--;
        }
        if (numberLenEffective == 0) {
            return null;
        }
        int resultLen = (numberLenEffective + 1) / 2;
        int extraBytes = includeLength ? 2 : 1;
        resultLen += extraBytes;

        byte[] result = new byte[resultLen];
        int digitCount = 0;
        for (int i = 0; i < numberLenReal; i++) {
            char c = number.charAt(i);
            if (c == '+') {
                continue;
            }
            int shift = ((digitCount & 0x01) == 1) ? 4 : 0;
            result[extraBytes + (digitCount >> 1)] |=
                    (byte) ((charToBCD(c, bcdExtendedType) & 0x0f) << shift);
            digitCount++;
        }
        if ((digitCount & 0x01) == 1) {
            result[extraBytes + (digitCount >> 1)] |= (byte) 0xf0;
        }
        int offset = 0;
        if (includeLength) {
            result[offset++] = (byte) (resultLen - 1);
        }
        result[offset] = (byte) (hasPlus ? TOA_International : TOA_Unknown);
        return result;
    }

    @Deprecated
    public static String formatNumber(String source) {
        if (source == null) {
            return null;
        }
        SpannableString text = new SpannableString(source);
        Editable editable = Editable.Factory.getInstance().newEditable(text);
        formatNumber(editable, getFormatTypeForLocale(Locale.getDefault()));
        return editable.toString();
    }

    @Deprecated
    public static int getFormatTypeForLocale(Locale locale) {
        if (locale == null) {
            return FORMAT_UNKNOWN;
        }
        String country = locale.getCountry();
        return getFormatTypeFromCountryCode(country);
    }

    private static int getFormatTypeFromCountryCode(String country) {
        if (country == null) {
            return FORMAT_UNKNOWN;
        }
        for (int i = 0; i < NANP_COUNTRIES.length; i++) {
            if (NANP_COUNTRIES[i].equalsIgnoreCase(country)) {
                return FORMAT_NANP;
            }
        }
        if ("JP".equalsIgnoreCase(country)) {
            return FORMAT_JAPAN;
        }
        return FORMAT_UNKNOWN;
    }

    @Deprecated
    public static void formatNumber(Editable text, int defaultFormattingType) {
        if (text == null || text.length() < 2) {
            return;
        }
        int formatType = defaultFormattingType;
        if (text.charAt(0) == '+') {
            if (text.charAt(1) == '1') {
                formatType = FORMAT_NANP;
            } else if (text.length() >= 3 && text.charAt(1) == '8' && text.charAt(2) == '1') {
                formatType = FORMAT_JAPAN;
            } else {
                formatType = FORMAT_UNKNOWN;
            }
        }
        switch (formatType) {
            case FORMAT_NANP:
                formatNanpNumber(text);
                return;
            case FORMAT_JAPAN:
                formatJapaneseNumber(text);
                return;
            default:
                return;
        }
    }

    @Deprecated
    public static void formatNanpNumber(Editable text) {
        if (text == null) {
            return;
        }
        int length = text.length();
        if (length > "+1-nnn-nnn-nnnn".length() || length <= 5) {
            return;
        }
        CharSequence saved = text.subSequence(0, length);

        // Strip dashes first
        int p = 0;
        while (p < text.length()) {
            if (text.charAt(p) == '-') {
                text.delete(p, p + 1);
            } else {
                p++;
            }
        }
        length = text.length();

        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (!(i == 0 && c == '+') && !Character.isDigit(c)) {
                text.replace(0, length, saved);
                return;
            }
        }

        String raw = text.toString();
        String formatted;
        if (raw.startsWith("+1")) {
            String rest = raw.substring(2);
            formatted = "+1-" + formatNanpDigits(rest);
        } else if (raw.startsWith("1") && raw.length() > 1) {
            String rest = raw.substring(1);
            formatted = "1-" + formatNanpDigits(rest);
        } else {
            formatted = formatNanpDigits(raw);
        }
        text.replace(0, length, formatted);
    }

    private static String formatNanpDigits(String digits) {
        int n = digits.length();
        if (n <= 3) {
            return digits;
        }
        if (n <= 7) {
            return digits.substring(0, 3) + "-" + digits.substring(3);
        }
        if (n <= 10) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
        }
        return digits;
    }

    @Deprecated
    public static void formatJapaneseNumber(Editable text) {
        if (text == null || text.length() < 4) {
            return;
        }
        String stripped = normalizeNumber(text.toString());
        if (stripped.startsWith("+81") && stripped.length() >= 11 && stripped.length() <= 13) {
            String local = stripped.substring(3);
            String out = "+81-" + local.substring(0, 2) + "-" + local.substring(2, 6) + "-" + local.substring(6);
            text.replace(0, text.length(), out);
        } else if (stripped.startsWith("0") && stripped.length() == 11) {
            String out = stripped.substring(0, 3) + "-" + stripped.substring(3, 7) + "-" + stripped.substring(7);
            text.replace(0, text.length(), out);
        } else if (stripped.startsWith("0") && stripped.length() == 10) {
            String out = stripped.substring(0, 2) + "-" + stripped.substring(2, 6) + "-" + stripped.substring(6);
            text.replace(0, text.length(), out);
        }
    }

    public static String formatNumberToE164(String phoneNumber, String defaultCountryIso) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return null;
        }
        String normalized = normalizeNumber(convertKeypadLettersToDigits(phoneNumber));
        if (TextUtils.isEmpty(normalized)) {
            return null;
        }
        if (normalized.startsWith("+")) {
            String digits = normalized.substring(1);
            if (digits.length() >= 7 && digits.length() <= 15 && isAllDigits(digits)) {
                return "+" + digits;
            }
            return null;
        }
        if (defaultCountryIso == null) {
            return null;
        }
        String iso = defaultCountryIso.toUpperCase(Locale.US);
        String cc = COUNTRY_CALLING_CODES.get(iso);
        if (cc == null) {
            return null;
        }
        if ("1".equals(cc)) {
            if (normalized.length() == 10 && isAllDigits(normalized)
                    && normalized.charAt(0) >= '2' && normalized.charAt(3) >= '2') {
                return "+1" + normalized;
            }
            if (normalized.length() == 11 && normalized.charAt(0) == '1' && isAllDigits(normalized)
                    && normalized.charAt(1) >= '2' && normalized.charAt(4) >= '2') {
                return "+" + normalized;
            }
            return null;
        }
        String nat = normalized.startsWith("0") ? normalized.substring(1) : normalized;
        if (nat.length() >= 6 && (cc.length() + nat.length()) <= 15 && isAllDigits(nat)) {
            return "+" + cc + nat;
        }
        return null;
    }

    private static boolean isAllDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!isISODigit(s.charAt(i))) {
                return false;
            }
        }
        return s.length() > 0;
    }

    public static String formatNumberToRFC3966(String phoneNumber, String defaultCountryIso) {
        String e164 = formatNumberToE164(phoneNumber, defaultCountryIso);
        if (e164 != null) {
            if (e164.startsWith("+1") && e164.length() == 12) {
                return "tel:+1-" + e164.substring(2, 5) + "-" + e164.substring(5, 8)
                        + "-" + e164.substring(8);
            }
            return "tel:" + e164;
        }
        if (TextUtils.isEmpty(phoneNumber)) {
            return null;
        }
        String norm = normalizeNumber(phoneNumber);
        return TextUtils.isEmpty(norm) ? null : ("tel:" + norm);
    }

    public static String formatNumber(String phoneNumber, String defaultCountryIso) {
        if (phoneNumber == null) {
            return null;
        }
        if (phoneNumber.startsWith("#") || phoneNumber.startsWith("*")) {
            return phoneNumber;
        }
        int fmt = getFormatTypeFromCountryCode(defaultCountryIso);
        String stripped = normalizeNumber(convertKeypadLettersToDigits(phoneNumber));
        if (fmt == FORMAT_NANP || stripped.startsWith("+1")) {
            if (stripped.startsWith("+1") && stripped.length() == 12) {
                return "+1 " + stripped.substring(2, 5) + "-" + stripped.substring(5, 8)
                        + "-" + stripped.substring(8);
            }
            if (stripped.length() == 11 && stripped.startsWith("1")) {
                return "1 (" + stripped.substring(1, 4) + ") " + stripped.substring(4, 7)
                        + "-" + stripped.substring(7);
            }
            if (stripped.length() == 10) {
                return "(" + stripped.substring(0, 3) + ") " + stripped.substring(3, 6)
                        + "-" + stripped.substring(6);
            }
            if (stripped.length() == 7) {
                return stripped.substring(0, 3) + "-" + stripped.substring(3);
            }
        }
        Editable ed = Editable.Factory.getInstance().newEditable(phoneNumber);
        formatNumber(ed, fmt);
        return ed.toString();
    }

    public static String formatNumber(
            String phoneNumber, String phoneNumberE164, String defaultCountryIso) {
        if (phoneNumber == null) {
            return null;
        }
        if (phoneNumberE164 != null && phoneNumberE164.startsWith("+")) {
            String formatted = formatNumber(phoneNumberE164, defaultCountryIso);
            if (formatted != null) {
                return formatted;
            }
        }
        return formatNumber(phoneNumber, defaultCountryIso);
    }

    public static String normalizeNumber(String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int len = phoneNumber.length();
        for (int i = 0; i < len; i++) {
            char c = phoneNumber.charAt(i);
            int digit = Character.digit(c, 10);
            if (digit != -1) {
                sb.append(digit);
            } else if (sb.length() == 0 && c == '+') {
                sb.append(c);
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                return normalizeNumber(convertKeypadLettersToDigits(phoneNumber));
            }
        }
        return sb.toString();
    }

    public static String replaceUnicodeDigits(String number) {
        if (number == null) {
            return null;
        }
        StringBuilder normalizedDigits = new StringBuilder(number.length());
        for (int i = 0; i < number.length(); i++) {
            char c = number.charAt(i);
            int digit = Character.digit(c, 10);
            if (digit != -1) {
                normalizedDigits.append(digit);
            } else {
                normalizedDigits.append(c);
            }
        }
        return normalizedDigits.toString();
    }

    @Deprecated
    public static boolean isEmergencyNumber(String number) {
        if (TextUtils.isEmpty(number)) {
            return false;
        }
        String stripped = extractNetworkPortion(number);
        return "112".equals(stripped)
                || "911".equals(stripped)
                || "000".equals(stripped)
                || "08".equals(stripped)
                || "110".equals(stripped)
                || "118".equals(stripped)
                || "119".equals(stripped)
                || "999".equals(stripped);
    }

    @Deprecated
    public static boolean isLocalEmergencyNumber(Context context, String number) {
        return isEmergencyNumber(number);
    }

    public static boolean isVoiceMailNumber(String number) {
        return false;
    }

    public static String convertKeypadLettersToDigits(String input) {
        if (input == null) {
            return input;
        }
        int len = input.length();
        if (len == 0) {
            return input;
        }
        char[] out = input.toCharArray();
        for (int i = 0; i < len; i++) {
            char c = out[i];
            out[i] = (char) KEYPAD_MAP.get(c, c);
        }
        return new String(out);
    }

    public static CharSequence createTtsSpannable(CharSequence phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        Spannable spannable = Spannable.Factory.getInstance().newSpannable(phoneNumber);
        addTtsSpan(spannable, 0, spannable.length());
        return spannable;
    }

    public static void addTtsSpan(Spannable s, int start, int endExclusive) {
        if (s == null) {
            return;
        }
        TtsSpan span = createTtsSpan(s.subSequence(start, endExclusive).toString());
        if (span != null) {
            s.setSpan(span, start, endExclusive, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    public static TtsSpan createTtsSpan(String phoneNumberString) {
        if (phoneNumberString == null) {
            return null;
        }
        TtsSpan.TelephoneBuilder builder = new TtsSpan.TelephoneBuilder();
        String trimmed = phoneNumberString.trim();
        if (trimmed.startsWith("+1") && trimmed.length() > 2) {
            builder.setCountryCode("1");
            builder.setNumberParts(splitDigitsWithSpaces(trimmed.substring(2)));
        } else if (trimmed.startsWith("+") && trimmed.length() > 3) {
            String digits = extractNetworkPortion(trimmed.substring(1));
            if (digits.length() > 2) {
                builder.setCountryCode(digits.substring(0, 2));
                builder.setNumberParts(splitDigitsWithSpaces(digits.substring(2)));
            } else {
                builder.setNumberParts(splitDigitsWithSpaces(digits));
            }
        } else {
            builder.setNumberParts(splitDigitsWithSpaces(extractNetworkPortion(trimmed)));
        }
        String postDial = extractPostDialPortion(trimmed);
        if (!TextUtils.isEmpty(postDial) && postDial.length() > 1) {
            builder.setExtension(postDial.substring(1));
        }
        return builder.build();
    }

    private static String splitDigitsWithSpaces(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c) || c == '*' || c == '#') {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static boolean areSamePhoneNumber(
            String number1, String number2, String defaultCountryIso) {
        if (number1 == null || number2 == null) {
            return false;
        }
        String e1 = formatNumberToE164(number1, defaultCountryIso);
        String e2 = formatNumberToE164(number2, defaultCountryIso);
        if (e1 != null && e2 != null) {
            return e1.equals(e2);
        }
        return compare(number1, number2);
    }

    public static boolean isWpsCallNumber(String number) {
        if (TextUtils.isEmpty(number)) {
            return false;
        }
        return number.startsWith("*272") || number.startsWith("#31#*272") || number.startsWith("*31#*272");
    }
}
