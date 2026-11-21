// SPDX-License-Identifier: GPL-3.0-only
// Behavioral unit tests for Milestone N5: liblog.so (18/18) and libicu.so (91/91).
#include "shiraz/ndk/log.h"

#include <cassert>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#include <unicode/ubidi.h>
#include <unicode/ubrk.h>
#include <unicode/ucal.h>
#include <unicode/uchar.h>
#include <unicode/ucnv.h>
#include <unicode/ucol.h>
#include <unicode/udat.h>
#include <unicode/uloc.h>
#include <unicode/unorm2.h>
#include <unicode/unum.h>
#include <unicode/uscript.h>
#include <unicode/ustring.h>
#include <unicode/utext.h>
#include <unicode/utrans.h>
#include <unicode/uversion.h>

namespace {

struct CapturedLog {
    int32_t bufferId;
    int32_t priority;
    std::string tag;
    std::string message;
};

std::vector<CapturedLog> gCaptured;
std::string gLastAbort;

void testLogger(const struct __android_log_message* msg) {
    gCaptured.push_back({msg->buffer_id, msg->priority, msg->tag ? msg->tag : "",
                         msg->message ? msg->message : ""});
}

void testAborter(const char* msg) {
    gLastAbort = msg ? msg : "";
}

void testLibLog() {
    gCaptured.clear();
    __android_log_set_logger(testLogger);
    __android_log_set_default_tag("ShirazDefault");
    const int32_t prevPrio = __android_log_set_minimum_priority(ANDROID_LOG_INFO);
    assert(__android_log_get_minimum_priority() == ANDROID_LOG_INFO);
    assert(__android_log_is_loggable(ANDROID_LOG_DEBUG, "Tag", ANDROID_LOG_VERBOSE) == 0);
    assert(__android_log_is_loggable(ANDROID_LOG_WARN, "Tag", ANDROID_LOG_VERBOSE) == 1);
    assert(__android_log_is_loggable_len(ANDROID_LOG_INFO, "Tag", 3, ANDROID_LOG_ERROR) == 1);

    // Filtered out (DEBUG < INFO)
    __android_log_write(ANDROID_LOG_DEBUG, "Tag", "hidden");
    assert(gCaptured.empty());

    // Logged with custom tag & formatted print
    __android_log_print(ANDROID_LOG_INFO, "MyTag", "hello %d", 42);
    assert(gCaptured.size() == 1);
    assert(gCaptured[0].tag == "MyTag");
    assert(gCaptured[0].message == "hello 42");

    // Default tag when tag is nullptr
    __android_log_buf_print(LOG_ID_SYSTEM, ANDROID_LOG_WARN, nullptr, "sys=%s", "ok");
    assert(gCaptured.size() == 2);
    assert(gCaptured[1].bufferId == LOG_ID_SYSTEM);
    assert(gCaptured[1].tag == "ShirazDefault");
    assert(gCaptured[1].message == "sys=ok");

    // Assert routes through custom aborter without crashing test runner
    __android_log_set_aborter(testAborter);
    __android_log_assert("x > 0", "AssertTag", "failed %d", 7);
    assert(gLastAbort == "failed 7");
    assert(gCaptured.size() == 3 && gCaptured[2].priority == ANDROID_LOG_FATAL);

    __android_log_set_aborter(__android_log_default_aborter);
    __android_log_set_logger(__android_log_stderr_logger);
    __android_log_set_minimum_priority(prevPrio);
}

void testLibIcu() {
    UVersionInfo ver{};
    u_getVersion(ver);
    assert(ver[0] >= 60);
    UVersionInfo uver{};
    u_getUnicodeVersion(uver);
    assert(uver[0] >= 10);
    assert(std::strcmp(u_errorName(U_ZERO_ERROR), "U_ZERO_ERROR") == 0);

    // Character properties & case
    assert(u_tolower('A') == 'a');
    assert(u_toupper('z') == 'Z');
    assert(u_totitle('a') == 'A');
    assert(u_foldCase(0x00DF, U_FOLD_CASE_DEFAULT) == 0x00DF);
    assert(u_isalpha('A') && u_isdigit('9') && u_isalnum('5'));
    assert(u_isspace(' ') && u_isWhitespace(' ') && u_isUWhiteSpace(' '));
    assert(u_isdefined(0x0041));
    assert(u_charType('A') == U_UPPERCASE_LETTER);
    assert(u_charDirection('A') == U_LEFT_TO_RIGHT);
    assert(u_hasBinaryProperty('A', UCHAR_ALPHABETIC));
    assert(u_getIntPropertyValue('A', UCHAR_GENERAL_CATEGORY) == U_UPPERCASE_LETTER);

    // UTF-8 <-> UTF-16 & case
    UErrorCode err = U_ZERO_ERROR;
    UChar u16[64] = {};
    int32_t u16Len = 0;
    u_strFromUTF8(u16, 64, &u16Len, "caf\xC3\xA9", -1, &err);
    assert(U_SUCCESS(err) && u16Len == 4 && u_strlen(u16) == 4);

    UChar upper[64] = {};
    const int32_t upLen = u_strToUpper(upper, 64, u16, u16Len, "fr", &err);
    assert(U_SUCCESS(err) && upLen == 4);
    assert(u_strCaseCompare(u16, u16Len, upper, upLen, U_FOLD_CASE_DEFAULT, &err) == 0);

    char utf8Out[64] = {};
    int32_t utf8Len = 0;
    u_strToUTF8(utf8Out, 64, &utf8Len, upper, upLen, &err);
    assert(U_SUCCESS(err) && std::string(utf8Out) == "CAF\xC3\x89");

    // Normalizer2 NFC vs NFD
    const UNormalizer2* nfd = unorm2_getNFDInstance(&err);
    const UNormalizer2* nfc = unorm2_getNFCInstance(&err);
    assert(U_SUCCESS(err) && nfd && nfc);
    UChar decomp[64] = {};
    const int32_t dLen = unorm2_normalize(nfd, u16, u16Len, decomp, 64, &err);
    assert(U_SUCCESS(err) && dLen == 5); // 'e' + combining acute
    assert(unorm2_isNormalized(nfd, decomp, dLen, &err));
    assert(!unorm2_isNormalized(nfc, decomp, dLen, &err));

    // Locale
    assert(uloc_getDefault() != nullptr);
    char lang[16] = {}, country[16] = {}, tag[32] = {};
    uloc_getLanguage("en_US", lang, sizeof(lang), &err);
    uloc_getCountry("en_US", country, sizeof(country), &err);
    uloc_toLanguageTag("en_US", tag, sizeof(tag), true, &err);
    assert(U_SUCCESS(err) && std::strcmp(lang, "en") == 0 && std::strcmp(country, "US") == 0 &&
           std::strcmp(tag, "en-US") == 0);

    // Collator
    UCollator* coll = ucol_open("en_US", &err);
    assert(U_SUCCESS(err) && coll);
    ucol_setStrength(coll, UCOL_PRIMARY);
    assert(ucol_getStrength(coll) == UCOL_PRIMARY);
    assert(ucol_strcollUTF8(coll, "apple", -1, "banana", -1, &err) == UCOL_LESS);
    ucol_close(coll);

    // BreakIterator
    UChar words[32] = {};
    int32_t wLen = 0;
    u_strFromUTF8(words, 32, &wLen, "hello world", -1, &err);
    UBreakIterator* bi = ubrk_open(UBRK_WORD, "en_US", words, wLen, &err);
    assert(U_SUCCESS(err) && bi);
    assert(ubrk_first(bi) == 0);
    assert(ubrk_next(bi) == 5);
    assert(ubrk_current(bi) == 5);
    assert(ubrk_last(bi) == 11);
    ubrk_close(bi);

    // Converter
    UConverter* cnv = ucnv_open("UTF-8", &err);
    assert(U_SUCCESS(err) && cnv);
    assert(ucnv_getName(cnv, &err) != nullptr);
    UChar convOut[16] = {};
    assert(ucnv_toUChars(cnv, convOut, 16, "Hi", 2, &err) == 2 && U_SUCCESS(err));
    ucnv_close(cnv);

    // NumberFormat
    UNumberFormat* nf = unum_open(UNUM_DECIMAL, nullptr, 0, "en_US", nullptr, &err);
    assert(U_SUCCESS(err) && nf);
    UChar numBuf[32] = {};
    const int32_t numLen = unum_formatInt64(nf, 12345, numBuf, 32, nullptr, &err);
    assert(U_SUCCESS(err) && numLen > 0);
    const double parsed = unum_parseDouble(nf, numBuf, numLen, nullptr, &err);
    assert(U_SUCCESS(err) && std::fabs(parsed - 12345.0) < 1e-6);
    unum_close(nf);

    // BiDi
    UBiDi* bidi = ubidi_open();
    assert(bidi != nullptr);
    ubidi_setPara(bidi, words, wLen, UBIDI_DEFAULT_LTR, nullptr, &err);
    assert(U_SUCCESS(err) && ubidi_getDirection(bidi) == UBIDI_LTR);
    assert(ubidi_countRuns(bidi, &err) == 1);
    ubidi_close(bidi);

    // Script & UText & Calendar
    const UScriptCode sc = uscript_getScript(0x0627, &err); // Arabic Alef
    assert(U_SUCCESS(err) && std::strcmp(uscript_getShortName(sc), "Arab") == 0);

    UText* ut = utext_openUTF8(nullptr, "shiraz", -1, &err);
    assert(U_SUCCESS(err) && ut);
    utext_close(ut);

    UCalendar* cal = ucal_open(nullptr, 0, "en_US", UCAL_GREGORIAN, &err);
    assert(U_SUCCESS(err) && cal);
    ucal_set(cal, UCAL_YEAR, 2026);
    assert(ucal_get(cal, UCAL_YEAR, &err) == 2026 && U_SUCCESS(err));
    assert(ucal_getNow() > 0);
    ucal_close(cal);

    // Transliterator ("Any-Latin; Latin-ASCII")
    UChar tid[32] = {};
    int32_t tidLen = 0;
    u_strFromUTF8(tid, 32, &tidLen, "Latin-ASCII", -1, &err);
    UTransliterator* tr = utrans_openU(tid, tidLen, UTRANS_FORWARD, nullptr, 0, nullptr, &err);
    assert(U_SUCCESS(err) && tr);
    UChar tbuf[32] = {};
    int32_t tLen = 0;
    u_strFromUTF8(tbuf, 32, &tLen, "caf\xC3\xA9", -1, &err);
    int32_t limit = tLen;
    utrans_transUChars(tr, tbuf, &tLen, 32, 0, &limit, &err);
    assert(U_SUCCESS(err));
    char ascii[32] = {};
    u_strToUTF8(ascii, 32, nullptr, tbuf, tLen, &err);
    assert(std::string(ascii) == "cafe");
    utrans_close(tr);
}

} // namespace

int main() {
    testLibLog();
    testLibIcu();
    std::printf("ndk_log_icu: ALL TESTS PASSED\n");
    return 0;
}
