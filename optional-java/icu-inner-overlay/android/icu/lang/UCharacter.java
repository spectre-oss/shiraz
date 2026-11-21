// SPDX-License-Identifier: GPL-3.0-only
package android.icu.lang;

/**
 * Overlay holder providing API 35 inner interfaces of {@code android.icu.lang.UCharacter}
 * whose outer class is supplied by {@code core-libart}.
 */
public final class UCharacter {
    private UCharacter() {
    }

    public interface IndicPositionalCategory {
        int NA = 0;
        int BOTTOM = 1;
        int BOTTOM_AND_LEFT = 2;
        int BOTTOM_AND_RIGHT = 3;
        int LEFT = 4;
        int LEFT_AND_RIGHT = 5;
        int OVERSTRUCK = 6;
        int RIGHT = 7;
        int TOP = 8;
        int TOP_AND_BOTTOM = 9;
        int TOP_AND_BOTTOM_AND_RIGHT = 10;
        int TOP_AND_LEFT = 11;
        int TOP_AND_LEFT_AND_RIGHT = 12;
        int TOP_AND_RIGHT = 13;
        int VISUAL_ORDER_LEFT = 14;
        int TOP_AND_BOTTOM_AND_LEFT = 15;
    }

    public interface IndicSyllabicCategory {
        int OTHER = 0;
        int AVAGRAHA = 1;
        int BINDU = 2;
        int BRAHMI_JOINING_NUMBER = 3;
        int CANTILLATION_MARK = 4;
        int CONSONANT = 5;
        int CONSONANT_DEAD = 6;
        int CONSONANT_FINAL = 7;
        int CONSONANT_HEAD_LETTER = 8;
        int CONSONANT_INITIAL_POSTFIXED = 9;
        int CONSONANT_KILLER = 10;
        int CONSONANT_MEDIAL = 11;
        int CONSONANT_PLACEHOLDER = 12;
        int CONSONANT_PRECEDING_REPHA = 13;
        int CONSONANT_PREFIXED = 14;
        int CONSONANT_SUBJOINED = 15;
        int CONSONANT_SUCCEEDING_REPHA = 16;
        int CONSONANT_WITH_STACKER = 17;
        int GEMINATION_MARK = 18;
        int INVISIBLE_STACKER = 19;
        int JOINER = 20;
        int MODIFYING_LETTER = 21;
        int NON_JOINER = 22;
        int NUKTA = 23;
        int NUMBER = 24;
        int NUMBER_JOINER = 25;
        int PURE_KILLER = 26;
        int REGISTER_SHIFTER = 27;
        int SYLLABLE_MODIFIER = 28;
        int TONE_LETTER = 29;
        int TONE_MARK = 30;
        int VIRAMA = 31;
        int VISARGA = 32;
        int VOWEL = 33;
        int VOWEL_DEPENDENT = 34;
        int VOWEL_INDEPENDENT = 35;
    }

    public interface VerticalOrientation {
        int ROTATED = 0;
        int TRANSFORMED_ROTATED = 1;
        int TRANSFORMED_UPRIGHT = 2;
        int UPRIGHT = 3;
    }
}
