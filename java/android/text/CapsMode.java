// Adapted from AOSP android-8.1.0_r81 frameworks/base, commit
// 7e7ea0991020d15cdb69c5da933c282c92f884ef, TextUtils.getCapsMode.
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
final class CapsMode {
 static final int CAP_MODE_CHARACTERS=0x1000,CAP_MODE_WORDS=0x2000,CAP_MODE_SENTENCES=0x4000;
    public static int getCapsMode(CharSequence cs, int off, int reqModes) {
        if (off < 0) {
            return 0;
        }

        int i;
        char c;
        int mode = 0;

        if ((reqModes&CAP_MODE_CHARACTERS) != 0) {
            mode |= CAP_MODE_CHARACTERS;
        }
        if ((reqModes&(CAP_MODE_WORDS|CAP_MODE_SENTENCES)) == 0) {
            return mode;
        }

        // Back over allowed opening punctuation.

        for (i = off; i > 0; i--) {
            c = cs.charAt(i - 1);

            if (c != '"' && c != '\'' &&
                Character.getType(c) != Character.START_PUNCTUATION) {
                break;
            }
        }

        // Start of paragraph, with optional whitespace.

        int j = i;
        while (j > 0 && ((c = cs.charAt(j - 1)) == ' ' || c == '\t')) {
            j--;
        }
        if (j == 0 || cs.charAt(j - 1) == '\n') {
            return mode | CAP_MODE_WORDS;
        }

        // Or start of word if we are that style.

        if ((reqModes&CAP_MODE_SENTENCES) == 0) {
            if (i != j) mode |= CAP_MODE_WORDS;
            return mode;
        }

        // There must be a space if not the start of paragraph.

        if (i == j) {
            return mode;
        }

        // Back over allowed closing punctuation.

        for (; j > 0; j--) {
            c = cs.charAt(j - 1);

            if (c != '"' && c != '\'' &&
                Character.getType(c) != Character.END_PUNCTUATION) {
                break;
            }
        }

        if (j > 0) {
            c = cs.charAt(j - 1);

            if (c == '.' || c == '?' || c == '!') {
                // Do not capitalize if the word ends with a period but
                // also contains a period, in which case it is an abbreviation.

                if (c == '.') {
                    for (int k = j - 2; k >= 0; k--) {
                        c = cs.charAt(k);

                        if (c == '.') {
                            return mode;
                        }

                        if (!Character.isLetter(c)) {
                            break;
                        }
                    }
                }

                return mode | CAP_MODE_SENTENCES;
            }
        }

        return mode;
    }
}
