// SPDX-License-Identifier: GPL-3.0-only
// Android NDK libicu.so: unversioned ICU4C C ABI wrappers forwarding directly
// to the host/bundled versioned ICU4C libraries (libicuuc + libicui18n).
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
#include <unicode/utypes.h>
#include <unicode/uversion.h>

#if !U_DISABLE_RENAMING

extern "C" {

// --- Version & error ---
#undef u_getVersion
void u_getVersion(UVersionInfo versionArray) {
    U_ICU_ENTRY_POINT_RENAME(u_getVersion)(versionArray);
}

#undef u_getUnicodeVersion
void u_getUnicodeVersion(UVersionInfo versionArray) {
    U_ICU_ENTRY_POINT_RENAME(u_getUnicodeVersion)(versionArray);
}

#undef u_errorName
const char* u_errorName(UErrorCode code) {
    return U_ICU_ENTRY_POINT_RENAME(u_errorName)(code);
}

// --- Character properties & case ---
#undef u_tolower
UChar32 u_tolower(UChar32 c) {
    return U_ICU_ENTRY_POINT_RENAME(u_tolower)(c);
}

#undef u_toupper
UChar32 u_toupper(UChar32 c) {
    return U_ICU_ENTRY_POINT_RENAME(u_toupper)(c);
}

#undef u_totitle
UChar32 u_totitle(UChar32 c) {
    return U_ICU_ENTRY_POINT_RENAME(u_totitle)(c);
}

#undef u_foldCase
UChar32 u_foldCase(UChar32 c, uint32_t options) {
    return U_ICU_ENTRY_POINT_RENAME(u_foldCase)(c, options);
}

#undef u_charType
int8_t u_charType(UChar32 c) {
    return U_ICU_ENTRY_POINT_RENAME(u_charType)(c);
}

#undef u_isalpha
UBool u_isalpha(UChar32 c) {
    return U_ICU_ENTRY_POINT_RENAME(u_isalpha)(c);
}

#undef u_isdigit
UBool u_isdigit(UChar32 c) {
    return U_ICU_ENTRY_POINT_RENAME(u_isdigit)(c);
}

#undef u_isalnum
UBool u_isalnum(UChar32 c) {
    return U_ICU_ENTRY_POINT_RENAME(u_isalnum)(c);
}

#undef u_isspace
UBool u_isspace(UChar32 c) {
    return U_ICU_ENTRY_POINT_RENAME(u_isspace)(c);
}

#undef u_isWhitespace
UBool u_isWhitespace(UChar32 c) {
    return U_ICU_ENTRY_POINT_RENAME(u_isWhitespace)(c);
}

#undef u_isUWhiteSpace
UBool u_isUWhiteSpace(UChar32 c) {
    return U_ICU_ENTRY_POINT_RENAME(u_isUWhiteSpace)(c);
}

#undef u_isdefined
UBool u_isdefined(UChar32 c) {
    return U_ICU_ENTRY_POINT_RENAME(u_isdefined)(c);
}

#undef u_charDirection
UCharDirection u_charDirection(UChar32 c) {
    return U_ICU_ENTRY_POINT_RENAME(u_charDirection)(c);
}

#undef u_getIntPropertyValue
int32_t u_getIntPropertyValue(UChar32 c, UProperty which) {
    return U_ICU_ENTRY_POINT_RENAME(u_getIntPropertyValue)(c, which);
}

#undef u_hasBinaryProperty
UBool u_hasBinaryProperty(UChar32 c, UProperty which) {
    return U_ICU_ENTRY_POINT_RENAME(u_hasBinaryProperty)(c, which);
}

// --- UTF-16 & UTF-8 strings ---
#undef u_strToUpper
int32_t u_strToUpper(UChar* dest, int32_t destCapacity, const UChar* src, int32_t srcLength,
                     const char* locale, UErrorCode* pErrorCode) {
    return U_ICU_ENTRY_POINT_RENAME(u_strToUpper)(dest, destCapacity, src, srcLength, locale,
                                                  pErrorCode);
}

#undef u_strToLower
int32_t u_strToLower(UChar* dest, int32_t destCapacity, const UChar* src, int32_t srcLength,
                     const char* locale, UErrorCode* pErrorCode) {
    return U_ICU_ENTRY_POINT_RENAME(u_strToLower)(dest, destCapacity, src, srcLength, locale,
                                                  pErrorCode);
}

#undef u_strFoldCase
int32_t u_strFoldCase(UChar* dest, int32_t destCapacity, const UChar* src, int32_t srcLength,
                      uint32_t options, UErrorCode* pErrorCode) {
    return U_ICU_ENTRY_POINT_RENAME(u_strFoldCase)(dest, destCapacity, src, srcLength, options,
                                                   pErrorCode);
}

#undef u_strFromUTF8
UChar* u_strFromUTF8(UChar* dest, int32_t destCapacity, int32_t* pDestLength, const char* src,
                     int32_t srcLength, UErrorCode* pErrorCode) {
    return U_ICU_ENTRY_POINT_RENAME(u_strFromUTF8)(dest, destCapacity, pDestLength, src, srcLength,
                                                   pErrorCode);
}

#undef u_strToUTF8
char* u_strToUTF8(char* dest, int32_t destCapacity, int32_t* pDestLength, const UChar* src,
                  int32_t srcLength, UErrorCode* pErrorCode) {
    return U_ICU_ENTRY_POINT_RENAME(u_strToUTF8)(dest, destCapacity, pDestLength, src, srcLength,
                                                 pErrorCode);
}

#undef u_strlen
int32_t u_strlen(const UChar* s) {
    return U_ICU_ENTRY_POINT_RENAME(u_strlen)(s);
}

#undef u_strcmp
int32_t u_strcmp(const UChar* s1, const UChar* s2) {
    return U_ICU_ENTRY_POINT_RENAME(u_strcmp)(s1, s2);
}

#undef u_strCaseCompare
int32_t u_strCaseCompare(const UChar* s1, int32_t length1, const UChar* s2, int32_t length2,
                         uint32_t options, UErrorCode* pErrorCode) {
    return U_ICU_ENTRY_POINT_RENAME(u_strCaseCompare)(s1, length1, s2, length2, options,
                                                      pErrorCode);
}

// --- BreakIterator ---
#undef ubrk_open
UBreakIterator* ubrk_open(UBreakIteratorType type, const char* locale, const UChar* text,
                          int32_t textLength, UErrorCode* status) {
    return U_ICU_ENTRY_POINT_RENAME(ubrk_open)(type, locale, text, textLength, status);
}

#undef ubrk_close
void ubrk_close(UBreakIterator* bi) {
    U_ICU_ENTRY_POINT_RENAME(ubrk_close)(bi);
}

#undef ubrk_setText
void ubrk_setText(UBreakIterator* bi, const UChar* text, int32_t textLength, UErrorCode* status) {
    U_ICU_ENTRY_POINT_RENAME(ubrk_setText)(bi, text, textLength, status);
}

#undef ubrk_first
int32_t ubrk_first(UBreakIterator* bi) {
    return U_ICU_ENTRY_POINT_RENAME(ubrk_first)(bi);
}

#undef ubrk_last
int32_t ubrk_last(UBreakIterator* bi) {
    return U_ICU_ENTRY_POINT_RENAME(ubrk_last)(bi);
}

#undef ubrk_next
int32_t ubrk_next(UBreakIterator* bi) {
    return U_ICU_ENTRY_POINT_RENAME(ubrk_next)(bi);
}

#undef ubrk_previous
int32_t ubrk_previous(UBreakIterator* bi) {
    return U_ICU_ENTRY_POINT_RENAME(ubrk_previous)(bi);
}

#undef ubrk_current
int32_t ubrk_current(const UBreakIterator* bi) {
    return U_ICU_ENTRY_POINT_RENAME(ubrk_current)(bi);
}

#undef ubrk_following
int32_t ubrk_following(UBreakIterator* bi, int32_t offset) {
    return U_ICU_ENTRY_POINT_RENAME(ubrk_following)(bi, offset);
}

#undef ubrk_preceding
int32_t ubrk_preceding(UBreakIterator* bi, int32_t offset) {
    return U_ICU_ENTRY_POINT_RENAME(ubrk_preceding)(bi, offset);
}

// --- Converter ---
#undef ucnv_open
UConverter* ucnv_open(const char* converterName, UErrorCode* err) {
    return U_ICU_ENTRY_POINT_RENAME(ucnv_open)(converterName, err);
}

#undef ucnv_close
void ucnv_close(UConverter* converter) {
    U_ICU_ENTRY_POINT_RENAME(ucnv_close)(converter);
}

#undef ucnv_toUChars
int32_t ucnv_toUChars(UConverter* cnv, UChar* dest, int32_t destCapacity, const char* src,
                      int32_t srcLength, UErrorCode* pErrorCode) {
    return U_ICU_ENTRY_POINT_RENAME(ucnv_toUChars)(cnv, dest, destCapacity, src, srcLength,
                                                   pErrorCode);
}

#undef ucnv_fromUChars
int32_t ucnv_fromUChars(UConverter* cnv, char* dest, int32_t destCapacity, const UChar* src,
                        int32_t srcLength, UErrorCode* pErrorCode) {
    return U_ICU_ENTRY_POINT_RENAME(ucnv_fromUChars)(cnv, dest, destCapacity, src, srcLength,
                                                     pErrorCode);
}

#undef ucnv_getName
const char* ucnv_getName(const UConverter* converter, UErrorCode* err) {
    return U_ICU_ENTRY_POINT_RENAME(ucnv_getName)(converter, err);
}

// --- Collator ---
#undef ucol_open
UCollator* ucol_open(const char* loc, UErrorCode* status) {
    return U_ICU_ENTRY_POINT_RENAME(ucol_open)(loc, status);
}

#undef ucol_close
void ucol_close(UCollator* coll) {
    U_ICU_ENTRY_POINT_RENAME(ucol_close)(coll);
}

#undef ucol_strcoll
UCollationResult ucol_strcoll(const UCollator* coll, const UChar* source, int32_t sourceLength,
                              const UChar* target, int32_t targetLength) {
    return U_ICU_ENTRY_POINT_RENAME(ucol_strcoll)(coll, source, sourceLength, target, targetLength);
}

#undef ucol_strcollUTF8
UCollationResult ucol_strcollUTF8(const UCollator* coll, const char* source, int32_t sourceLength,
                                  const char* target, int32_t targetLength, UErrorCode* status) {
    return U_ICU_ENTRY_POINT_RENAME(ucol_strcollUTF8)(coll, source, sourceLength, target,
                                                      targetLength, status);
}

#undef ucol_setStrength
void ucol_setStrength(UCollator* coll, UCollationStrength newStrength) {
    U_ICU_ENTRY_POINT_RENAME(ucol_setStrength)(coll, newStrength);
}

#undef ucol_getStrength
UCollationStrength ucol_getStrength(const UCollator* coll) {
    return U_ICU_ENTRY_POINT_RENAME(ucol_getStrength)(coll);
}

#undef ucol_setAttribute
void ucol_setAttribute(UCollator* coll, UColAttribute attr, UColAttributeValue value,
                       UErrorCode* status) {
    U_ICU_ENTRY_POINT_RENAME(ucol_setAttribute)(coll, attr, value, status);
}

#undef ucol_getAttribute
UColAttributeValue ucol_getAttribute(const UCollator* coll, UColAttribute attr,
                                     UErrorCode* status) {
    return U_ICU_ENTRY_POINT_RENAME(ucol_getAttribute)(coll, attr, status);
}

// --- DateFormat ---
#undef udat_open
UDateFormat* udat_open(UDateFormatStyle timeStyle, UDateFormatStyle dateStyle, const char* locale,
                       const UChar* tzID, int32_t tzIDLength, const UChar* pattern,
                       int32_t patternLength, UErrorCode* status) {
    return U_ICU_ENTRY_POINT_RENAME(udat_open)(timeStyle, dateStyle, locale, tzID, tzIDLength,
                                               pattern, patternLength, status);
}

#undef udat_close
void udat_close(UDateFormat* format) {
    U_ICU_ENTRY_POINT_RENAME(udat_close)(format);
}

#undef udat_format
int32_t udat_format(const UDateFormat* format, UDate dateToFormat, UChar* result,
                    int32_t resultLength, UFieldPosition* position, UErrorCode* status) {
    return U_ICU_ENTRY_POINT_RENAME(udat_format)(format, dateToFormat, result, resultLength,
                                                 position, status);
}

#undef udat_parse
UDate udat_parse(const UDateFormat* format, const UChar* text, int32_t textLength,
                 int32_t* parsePos, UErrorCode* status) {
    return U_ICU_ENTRY_POINT_RENAME(udat_parse)(format, text, textLength, parsePos, status);
}

// --- Locale ---
#undef uloc_getDefault
const char* uloc_getDefault(void) {
    return U_ICU_ENTRY_POINT_RENAME(uloc_getDefault)();
}

#undef uloc_getLanguage
int32_t uloc_getLanguage(const char* localeID, char* language, int32_t languageCapacity,
                         UErrorCode* err) {
    return U_ICU_ENTRY_POINT_RENAME(uloc_getLanguage)(localeID, language, languageCapacity, err);
}

#undef uloc_getCountry
int32_t uloc_getCountry(const char* localeID, char* country, int32_t countryCapacity,
                        UErrorCode* err) {
    return U_ICU_ENTRY_POINT_RENAME(uloc_getCountry)(localeID, country, countryCapacity, err);
}

#undef uloc_getScript
int32_t uloc_getScript(const char* localeID, char* script, int32_t scriptCapacity,
                       UErrorCode* err) {
    return U_ICU_ENTRY_POINT_RENAME(uloc_getScript)(localeID, script, scriptCapacity, err);
}

#undef uloc_canonicalize
int32_t uloc_canonicalize(const char* localeID, char* name, int32_t nameCapacity, UErrorCode* err) {
    return U_ICU_ENTRY_POINT_RENAME(uloc_canonicalize)(localeID, name, nameCapacity, err);
}

#undef uloc_toLanguageTag
int32_t uloc_toLanguageTag(const char* localeID, char* langtag, int32_t langtagCapacity,
                           UBool strict, UErrorCode* err) {
    return U_ICU_ENTRY_POINT_RENAME(uloc_toLanguageTag)(localeID, langtag, langtagCapacity, strict,
                                                        err);
}

#undef uloc_forLanguageTag
int32_t uloc_forLanguageTag(const char* langtag, char* localeID, int32_t localeIDCapacity,
                            int32_t* parsedLength, UErrorCode* err) {
    return U_ICU_ENTRY_POINT_RENAME(uloc_forLanguageTag)(langtag, localeID, localeIDCapacity,
                                                         parsedLength, err);
}

// --- Normalizer2 ---
#undef unorm2_getNFCInstance
const UNormalizer2* unorm2_getNFCInstance(UErrorCode* pErrorCode) {
    return U_ICU_ENTRY_POINT_RENAME(unorm2_getNFCInstance)(pErrorCode);
}

#undef unorm2_getNFDInstance
const UNormalizer2* unorm2_getNFDInstance(UErrorCode* pErrorCode) {
    return U_ICU_ENTRY_POINT_RENAME(unorm2_getNFDInstance)(pErrorCode);
}

#undef unorm2_getNFKCInstance
const UNormalizer2* unorm2_getNFKCInstance(UErrorCode* pErrorCode) {
    return U_ICU_ENTRY_POINT_RENAME(unorm2_getNFKCInstance)(pErrorCode);
}

#undef unorm2_getNFKDInstance
const UNormalizer2* unorm2_getNFKDInstance(UErrorCode* pErrorCode) {
    return U_ICU_ENTRY_POINT_RENAME(unorm2_getNFKDInstance)(pErrorCode);
}

#undef unorm2_normalize
int32_t unorm2_normalize(const UNormalizer2* norm2, const UChar* src, int32_t length, UChar* dest,
                         int32_t capacity, UErrorCode* pErrorCode) {
    return U_ICU_ENTRY_POINT_RENAME(unorm2_normalize)(norm2, src, length, dest, capacity,
                                                      pErrorCode);
}

#undef unorm2_isNormalized
UBool unorm2_isNormalized(const UNormalizer2* norm2, const UChar* s, int32_t length,
                          UErrorCode* pErrorCode) {
    return U_ICU_ENTRY_POINT_RENAME(unorm2_isNormalized)(norm2, s, length, pErrorCode);
}

// --- NumberFormat ---
#undef unum_open
UNumberFormat* unum_open(UNumberFormatStyle style, const UChar* pattern, int32_t patternLength,
                         const char* locale, UParseError* parseErr, UErrorCode* status) {
    return U_ICU_ENTRY_POINT_RENAME(unum_open)(style, pattern, patternLength, locale, parseErr,
                                               status);
}

#undef unum_close
void unum_close(UNumberFormat* fmt) {
    U_ICU_ENTRY_POINT_RENAME(unum_close)(fmt);
}

#undef unum_formatInt64
int32_t unum_formatInt64(const UNumberFormat* fmt, int64_t number, UChar* result,
                         int32_t resultLength, UFieldPosition* pos, UErrorCode* status) {
    return U_ICU_ENTRY_POINT_RENAME(unum_formatInt64)(fmt, number, result, resultLength, pos,
                                                      status);
}

#undef unum_formatDouble
int32_t unum_formatDouble(const UNumberFormat* fmt, double number, UChar* result,
                          int32_t resultLength, UFieldPosition* pos, UErrorCode* status) {
    return U_ICU_ENTRY_POINT_RENAME(unum_formatDouble)(fmt, number, result, resultLength, pos,
                                                       status);
}

#undef unum_parseDouble
double unum_parseDouble(const UNumberFormat* fmt, const UChar* text, int32_t textLength,
                        int32_t* parsePos, UErrorCode* status) {
    return U_ICU_ENTRY_POINT_RENAME(unum_parseDouble)(fmt, text, textLength, parsePos, status);
}

// --- BiDi ---
#undef ubidi_open
UBiDi* ubidi_open(void) {
    return U_ICU_ENTRY_POINT_RENAME(ubidi_open)();
}

#undef ubidi_close
void ubidi_close(UBiDi* pBiDi) {
    U_ICU_ENTRY_POINT_RENAME(ubidi_close)(pBiDi);
}

#undef ubidi_setPara
void ubidi_setPara(UBiDi* pBiDi, const UChar* text, int32_t length, UBiDiLevel paraLevel,
                   UBiDiLevel* embeddingLevels, UErrorCode* pErrorCode) {
    U_ICU_ENTRY_POINT_RENAME(ubidi_setPara)(pBiDi, text, length, paraLevel, embeddingLevels,
                                            pErrorCode);
}

#undef ubidi_getDirection
UBiDiDirection ubidi_getDirection(const UBiDi* pBiDi) {
    return U_ICU_ENTRY_POINT_RENAME(ubidi_getDirection)(pBiDi);
}

#undef ubidi_getParaLevel
UBiDiLevel ubidi_getParaLevel(const UBiDi* pBiDi) {
    return U_ICU_ENTRY_POINT_RENAME(ubidi_getParaLevel)(pBiDi);
}

#undef ubidi_countRuns
int32_t ubidi_countRuns(UBiDi* pBiDi, UErrorCode* pErrorCode) {
    return U_ICU_ENTRY_POINT_RENAME(ubidi_countRuns)(pBiDi, pErrorCode);
}

#undef ubidi_getVisualRun
UBiDiDirection ubidi_getVisualRun(UBiDi* pBiDi, int32_t runIndex, int32_t* pLogicalStart,
                                  int32_t* pLength) {
    return U_ICU_ENTRY_POINT_RENAME(ubidi_getVisualRun)(pBiDi, runIndex, pLogicalStart, pLength);
}

// --- Script ---
#undef uscript_getScript
UScriptCode uscript_getScript(UChar32 codepoint, UErrorCode* err) {
    return U_ICU_ENTRY_POINT_RENAME(uscript_getScript)(codepoint, err);
}

#undef uscript_getShortName
const char* uscript_getShortName(UScriptCode scriptCode) {
    return U_ICU_ENTRY_POINT_RENAME(uscript_getShortName)(scriptCode);
}

// --- UText ---
#undef utext_openUTF8
UText* utext_openUTF8(UText* ut, const char* s, int64_t length, UErrorCode* status) {
    return U_ICU_ENTRY_POINT_RENAME(utext_openUTF8)(ut, s, length, status);
}

#undef utext_openUChars
UText* utext_openUChars(UText* ut, const UChar* s, int64_t length, UErrorCode* status) {
    return U_ICU_ENTRY_POINT_RENAME(utext_openUChars)(ut, s, length, status);
}

#undef utext_close
UText* utext_close(UText* ut) {
    return U_ICU_ENTRY_POINT_RENAME(utext_close)(ut);
}

// --- Transliterator ---
#undef utrans_openU
UTransliterator* utrans_openU(const UChar* id, int32_t idLength, UTransDirection dir,
                              const UChar* rules, int32_t rulesLength, UParseError* parseError,
                              UErrorCode* pErrorCode) {
    return U_ICU_ENTRY_POINT_RENAME(utrans_openU)(id, idLength, dir, rules, rulesLength, parseError,
                                                  pErrorCode);
}

#undef utrans_close
void utrans_close(UTransliterator* trans) {
    U_ICU_ENTRY_POINT_RENAME(utrans_close)(trans);
}

#undef utrans_transUChars
void utrans_transUChars(const UTransliterator* trans, UChar* text, int32_t* textLength,
                        int32_t textCapacity, int32_t start, int32_t* limit, UErrorCode* status) {
    U_ICU_ENTRY_POINT_RENAME(utrans_transUChars)(trans, text, textLength, textCapacity, start,
                                                 limit, status);
}

// --- Calendar ---
#undef ucal_open
UCalendar* ucal_open(const UChar* zoneID, int32_t len, const char* locale, UCalendarType type,
                     UErrorCode* status) {
    return U_ICU_ENTRY_POINT_RENAME(ucal_open)(zoneID, len, locale, type, status);
}

#undef ucal_close
void ucal_close(UCalendar* cal) {
    U_ICU_ENTRY_POINT_RENAME(ucal_close)(cal);
}

#undef ucal_getNow
UDate ucal_getNow(void) {
    return U_ICU_ENTRY_POINT_RENAME(ucal_getNow)();
}

#undef ucal_get
int32_t ucal_get(const UCalendar* cal, UCalendarDateFields field, UErrorCode* status) {
    return U_ICU_ENTRY_POINT_RENAME(ucal_get)(cal, field, status);
}

#undef ucal_set
void ucal_set(UCalendar* cal, UCalendarDateFields field, int32_t value) {
    U_ICU_ENTRY_POINT_RENAME(ucal_set)(cal, field, value);
}

} // extern "C"

#endif // !U_DISABLE_RENAMING
