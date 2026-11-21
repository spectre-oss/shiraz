// Adapted for Shiraz from AOSP android-8.1.0_r81, frameworks/base
// commit 7e7ea0991020d15cdb69c5da933c282c92f884ef.
/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.text;
public interface InputFilter {
    CharSequence filter(CharSequence source,int start,int end,Spanned dest,int dstart,int dend);
    public static class LengthFilter implements InputFilter {
        private final int mMax;

        public LengthFilter(int max) {
            mMax = max;
        }

        public CharSequence filter(CharSequence source, int start, int end, Spanned dest,
                int dstart, int dend) {
            int keep = mMax - (dest.length() - (dend - dstart));
            if (keep <= 0) {
                return "";
            } else if (keep >= end - start) {
                return null; // keep original
            } else {
                keep += start;
                if (Character.isHighSurrogate(source.charAt(keep - 1))) {
                    --keep;
                    if (keep == start) {
                        return "";
                    }
                }
                return source.subSequence(start, keep);
            }
        }

        /**
         * @return the maximum length enforced by this input filter
         */
        public int getMax() {
            return mMax;
        }
    }

    public static class AllCaps implements InputFilter {
        private final java.util.Locale mLocale;

        public AllCaps() {
            mLocale = null;
        }

        public AllCaps(java.util.Locale locale) {
            if (locale == null) {
                throw new NullPointerException("locale");
            }
            mLocale = locale;
        }

        @Override
        public CharSequence filter(
                CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            boolean needUpper = false;
            for (int i = start; i < end; i++) {
                int cp = Character.codePointAt(source, i);
                if (Character.isLowerCase(cp) || Character.isTitleCase(cp)) {
                    needUpper = true;
                    break;
                }
                if (cp > 0xFFFF) {
                    i++;
                }
            }
            if (!needUpper) {
                return null;
            }
            java.util.Locale loc = mLocale != null ? mLocale : java.util.Locale.getDefault();
            String upper = source.subSequence(start, end).toString().toUpperCase(loc);
            if (source instanceof Spanned) {
                SpannableString sp = new SpannableString(upper);
                if (upper.length() == end - start) {
                    TextUtils.copySpansFrom((Spanned) source, start, end, null, sp, 0);
                }
                return sp;
            }
            return upper;
        }
    }
}
