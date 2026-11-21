// SPDX-License-Identifier: GPL-3.0-only
package android.icu.text;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Represents all the display options that are supported by CLDR such as grammatical case and noun class. */
public final class DisplayOptions {
    public enum GrammaticalCase {
        UNDEFINED("undefined"),
        ABLATIVE("ablative"),
        ACCUSATIVE("accusative"),
        COMITATIVE("comitative"),
        DATIVE("dative"),
        ERGATIVE("ergative"),
        GENITIVE("genitive"),
        INSTRUMENTAL("instrumental"),
        LOCATIVE("locative"),
        LOCATIVE_COPULATIVE("locative_copulative"),
        NOMINATIVE("nominative"),
        OBLIQUE("oblique"),
        PREPOSITIONAL("prepositional"),
        SOCIATIVE("sociative"),
        VOCATIVE("vocative");

        public static final List<GrammaticalCase> VALUES =
                Collections.unmodifiableList(Arrays.asList(values()));

        private final String mIdentifier;

        GrammaticalCase(String identifier) {
            mIdentifier = identifier;
        }

        public final String getIdentifier() {
            return mIdentifier;
        }

        public static final GrammaticalCase fromIdentifier(String identifier) {
            if (identifier == null) return UNDEFINED;
            for (int i = 0; i < VALUES.size(); i++) {
                GrammaticalCase v = VALUES.get(i);
                if (v.mIdentifier.equals(identifier)) return v;
            }
            return UNDEFINED;
        }
    }

    public enum NounClass {
        UNDEFINED("undefined"),
        OTHER("other"),
        NEUTER("neuter"),
        FEMININE("feminine"),
        MASCULINE("masculine"),
        ANIMATE("animate"),
        INANIMATE("inanimate"),
        PERSONAL("personal"),
        COMMON("common");

        public static final List<NounClass> VALUES =
                Collections.unmodifiableList(Arrays.asList(values()));

        private final String mIdentifier;

        NounClass(String identifier) {
            mIdentifier = identifier;
        }

        public final String getIdentifier() {
            return mIdentifier;
        }

        public static final NounClass fromIdentifier(String identifier) {
            if (identifier == null) return UNDEFINED;
            for (int i = 0; i < VALUES.size(); i++) {
                NounClass v = VALUES.get(i);
                if (v.mIdentifier.equals(identifier)) return v;
            }
            return UNDEFINED;
        }
    }

    public enum PluralCategory {
        UNDEFINED("undefined"),
        ZERO("zero"),
        ONE("one"),
        TWO("two"),
        FEW("few"),
        MANY("many"),
        OTHER("other");

        public static final List<PluralCategory> VALUES =
                Collections.unmodifiableList(Arrays.asList(values()));

        private final String mIdentifier;

        PluralCategory(String identifier) {
            mIdentifier = identifier;
        }

        public final String getIdentifier() {
            return mIdentifier;
        }

        public static final PluralCategory fromIdentifier(String identifier) {
            if (identifier == null) return UNDEFINED;
            for (int i = 0; i < VALUES.size(); i++) {
                PluralCategory v = VALUES.get(i);
                if (v.mIdentifier.equals(identifier)) return v;
            }
            return UNDEFINED;
        }
    }

    public enum Capitalization {
        UNDEFINED,
        BEGINNING_OF_SENTENCE,
        MIDDLE_OF_SENTENCE,
        STANDALONE,
        UI_LIST_OR_MENU;

        public static final List<Capitalization> VALUES =
                Collections.unmodifiableList(Arrays.asList(values()));
    }

    public enum NameStyle {
        UNDEFINED,
        STANDARD_NAMES,
        DIALECT_NAMES;

        public static final List<NameStyle> VALUES =
                Collections.unmodifiableList(Arrays.asList(values()));
    }

    public enum DisplayLength {
        UNDEFINED,
        LENGTH_FULL,
        LENGTH_SHORT;

        public static final List<DisplayLength> VALUES =
                Collections.unmodifiableList(Arrays.asList(values()));
    }

    public enum SubstituteHandling {
        UNDEFINED,
        SUBSTITUTE,
        NO_SUBSTITUTE;

        public static final List<SubstituteHandling> VALUES =
                Collections.unmodifiableList(Arrays.asList(values()));
    }

    private final GrammaticalCase mGrammaticalCase;
    private final NounClass mNounClass;
    private final PluralCategory mPluralCategory;
    private final Capitalization mCapitalization;
    private final NameStyle mNameStyle;
    private final DisplayLength mDisplayLength;
    private final SubstituteHandling mSubstituteHandling;

    private DisplayOptions(Builder b) {
        mGrammaticalCase = b.mGrammaticalCase;
        mNounClass = b.mNounClass;
        mPluralCategory = b.mPluralCategory;
        mCapitalization = b.mCapitalization;
        mNameStyle = b.mNameStyle;
        mDisplayLength = b.mDisplayLength;
        mSubstituteHandling = b.mSubstituteHandling;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copyToBuilder() {
        return new Builder(this);
    }

    public GrammaticalCase getGrammaticalCase() { return mGrammaticalCase; }
    public NounClass getNounClass() { return mNounClass; }
    public PluralCategory getPluralCategory() { return mPluralCategory; }
    public Capitalization getCapitalization() { return mCapitalization; }
    public NameStyle getNameStyle() { return mNameStyle; }
    public DisplayLength getDisplayLength() { return mDisplayLength; }
    public SubstituteHandling getSubstituteHandling() { return mSubstituteHandling; }

    public static class Builder {
        GrammaticalCase mGrammaticalCase = GrammaticalCase.UNDEFINED;
        NounClass mNounClass = NounClass.UNDEFINED;
        PluralCategory mPluralCategory = PluralCategory.UNDEFINED;
        Capitalization mCapitalization = Capitalization.UNDEFINED;
        NameStyle mNameStyle = NameStyle.UNDEFINED;
        DisplayLength mDisplayLength = DisplayLength.UNDEFINED;
        SubstituteHandling mSubstituteHandling = SubstituteHandling.UNDEFINED;

        Builder() {
        }

        Builder(DisplayOptions src) {
            mGrammaticalCase = src.mGrammaticalCase;
            mNounClass = src.mNounClass;
            mPluralCategory = src.mPluralCategory;
            mCapitalization = src.mCapitalization;
            mNameStyle = src.mNameStyle;
            mDisplayLength = src.mDisplayLength;
            mSubstituteHandling = src.mSubstituteHandling;
        }

        public Builder setGrammaticalCase(GrammaticalCase grammaticalCase) {
            mGrammaticalCase = grammaticalCase != null ? grammaticalCase : GrammaticalCase.UNDEFINED;
            return this;
        }

        public Builder setNounClass(NounClass nounClass) {
            mNounClass = nounClass != null ? nounClass : NounClass.UNDEFINED;
            return this;
        }

        public Builder setPluralCategory(PluralCategory pluralCategory) {
            mPluralCategory = pluralCategory != null ? pluralCategory : PluralCategory.UNDEFINED;
            return this;
        }

        public Builder setCapitalization(Capitalization capitalization) {
            mCapitalization = capitalization != null ? capitalization : Capitalization.UNDEFINED;
            return this;
        }

        public Builder setNameStyle(NameStyle nameStyle) {
            mNameStyle = nameStyle != null ? nameStyle : NameStyle.UNDEFINED;
            return this;
        }

        public Builder setDisplayLength(DisplayLength displayLength) {
            mDisplayLength = displayLength != null ? displayLength : DisplayLength.UNDEFINED;
            return this;
        }

        public Builder setSubstituteHandling(SubstituteHandling substituteHandling) {
            mSubstituteHandling = substituteHandling != null ? substituteHandling : SubstituteHandling.UNDEFINED;
            return this;
        }

        public DisplayOptions build() {
            return new DisplayOptions(this);
        }
    }
}
