// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.Energy;
import android.health.connect.datatypes.units.Mass;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class NutritionRecord extends IntervalRecord {
    public static final AggregationType<Mass> BIOTIN_TOTAL = massAgg("biotin");
    public static final AggregationType<Mass> CAFFEINE_TOTAL = massAgg("caffeine");
    public static final AggregationType<Mass> CALCIUM_TOTAL = massAgg("calcium");
    public static final AggregationType<Mass> CHLORIDE_TOTAL = massAgg("chloride");
    public static final AggregationType<Mass> CHOLESTEROL_TOTAL = massAgg("cholesterol");
    public static final AggregationType<Mass> CHROMIUM_TOTAL = massAgg("chromium");
    public static final AggregationType<Mass> COPPER_TOTAL = massAgg("copper");
    public static final AggregationType<Mass> DIETARY_FIBER_TOTAL = massAgg("dietaryFiber");
    public static final AggregationType<Energy> ENERGY_FROM_FAT_TOTAL =
            new AggregationType<Energy>(NutritionRecord.class, "energyFromFat",
                    AggregationType.OP_SUM, AggregationType.KIND_ENERGY);
    public static final AggregationType<Energy> ENERGY_TOTAL =
            new AggregationType<Energy>(NutritionRecord.class, "energy",
                    AggregationType.OP_SUM, AggregationType.KIND_ENERGY);
    public static final AggregationType<Mass> FOLATE_TOTAL = massAgg("folate");
    public static final AggregationType<Mass> FOLIC_ACID_TOTAL = massAgg("folicAcid");
    public static final AggregationType<Mass> IODINE_TOTAL = massAgg("iodine");
    public static final AggregationType<Mass> IRON_TOTAL = massAgg("iron");
    public static final AggregationType<Mass> MAGNESIUM_TOTAL = massAgg("magnesium");
    public static final AggregationType<Mass> MANGANESE_TOTAL = massAgg("manganese");
    public static final AggregationType<Mass> MOLYBDENUM_TOTAL = massAgg("molybdenum");
    public static final AggregationType<Mass> MONOUNSATURATED_FAT_TOTAL = massAgg("monounsaturatedFat");
    public static final AggregationType<Mass> NIACIN_TOTAL = massAgg("niacin");
    public static final AggregationType<Mass> PANTOTHENIC_ACID_TOTAL = massAgg("pantothenicAcid");
    public static final AggregationType<Mass> PHOSPHORUS_TOTAL = massAgg("phosphorus");
    public static final AggregationType<Mass> POLYUNSATURATED_FAT_TOTAL = massAgg("polyunsaturatedFat");
    public static final AggregationType<Mass> POTASSIUM_TOTAL = massAgg("potassium");
    public static final AggregationType<Mass> PROTEIN_TOTAL = massAgg("protein");
    public static final AggregationType<Mass> RIBOFLAVIN_TOTAL = massAgg("riboflavin");
    public static final AggregationType<Mass> SATURATED_FAT_TOTAL = massAgg("saturatedFat");
    public static final AggregationType<Mass> SELENIUM_TOTAL = massAgg("selenium");
    public static final AggregationType<Mass> SODIUM_TOTAL = massAgg("sodium");
    public static final AggregationType<Mass> SUGAR_TOTAL = massAgg("sugar");
    public static final AggregationType<Mass> THIAMIN_TOTAL = massAgg("thiamin");
    public static final AggregationType<Mass> TOTAL_CARBOHYDRATE_TOTAL = massAgg("totalCarbohydrate");
    public static final AggregationType<Mass> TOTAL_FAT_TOTAL = massAgg("totalFat");
    public static final AggregationType<Mass> TRANS_FAT_TOTAL = massAgg("transFat");
    public static final AggregationType<Mass> UNSATURATED_FAT_TOTAL = massAgg("unsaturatedFat");
    public static final AggregationType<Mass> VITAMIN_A_TOTAL = massAgg("vitaminA");
    public static final AggregationType<Mass> VITAMIN_B12_TOTAL = massAgg("vitaminB12");
    public static final AggregationType<Mass> VITAMIN_B6_TOTAL = massAgg("vitaminB6");
    public static final AggregationType<Mass> VITAMIN_C_TOTAL = massAgg("vitaminC");
    public static final AggregationType<Mass> VITAMIN_D_TOTAL = massAgg("vitaminD");
    public static final AggregationType<Mass> VITAMIN_E_TOTAL = massAgg("vitaminE");
    public static final AggregationType<Mass> VITAMIN_K_TOTAL = massAgg("vitaminK");
    public static final AggregationType<Mass> ZINC_TOTAL = massAgg("zinc");

    private static AggregationType<Mass> massAgg(String metric) {
        return new AggregationType<Mass>(NutritionRecord.class, metric,
                AggregationType.OP_SUM, AggregationType.KIND_MASS);
    }

    private final int mMealType;
    private final String mMealName;
    private final Mass mUnsaturatedFat;
    private final Mass mPotassium;
    private final Mass mThiamin;
    private final Mass mTransFat;
    private final Mass mManganese;
    private final Energy mEnergyFromFat;
    private final Mass mCaffeine;
    private final Mass mDietaryFiber;
    private final Mass mSelenium;
    private final Mass mVitaminB6;
    private final Mass mProtein;
    private final Mass mChloride;
    private final Mass mCholesterol;
    private final Mass mCopper;
    private final Mass mIodine;
    private final Mass mVitaminB12;
    private final Mass mZinc;
    private final Mass mRiboflavin;
    private final Energy mEnergy;
    private final Mass mMolybdenum;
    private final Mass mPhosphorus;
    private final Mass mChromium;
    private final Mass mTotalFat;
    private final Mass mCalcium;
    private final Mass mVitaminC;
    private final Mass mVitaminE;
    private final Mass mBiotin;
    private final Mass mVitaminD;
    private final Mass mNiacin;
    private final Mass mMagnesium;
    private final Mass mTotalCarbohydrate;
    private final Mass mVitaminK;
    private final Mass mPolyunsaturatedFat;
    private final Mass mSaturatedFat;
    private final Mass mSodium;
    private final Mass mFolate;
    private final Mass mMonounsaturatedFat;
    private final Mass mPantothenicAcid;
    private final Mass mIron;
    private final Mass mVitaminA;
    private final Mass mFolicAcid;
    private final Mass mSugar;

    NutritionRecord(Builder b) {
        super(b.mMetadata, b.mStartTime, b.mStartZoneOffset, b.mEndTime, b.mEndZoneOffset);
        mMealType = b.mMealType;
        mMealName = b.mMealName;
        mUnsaturatedFat = b.mUnsaturatedFat;
        mPotassium = b.mPotassium;
        mThiamin = b.mThiamin;
        mTransFat = b.mTransFat;
        mManganese = b.mManganese;
        mEnergyFromFat = b.mEnergyFromFat;
        mCaffeine = b.mCaffeine;
        mDietaryFiber = b.mDietaryFiber;
        mSelenium = b.mSelenium;
        mVitaminB6 = b.mVitaminB6;
        mProtein = b.mProtein;
        mChloride = b.mChloride;
        mCholesterol = b.mCholesterol;
        mCopper = b.mCopper;
        mIodine = b.mIodine;
        mVitaminB12 = b.mVitaminB12;
        mZinc = b.mZinc;
        mRiboflavin = b.mRiboflavin;
        mEnergy = b.mEnergy;
        mMolybdenum = b.mMolybdenum;
        mPhosphorus = b.mPhosphorus;
        mChromium = b.mChromium;
        mTotalFat = b.mTotalFat;
        mCalcium = b.mCalcium;
        mVitaminC = b.mVitaminC;
        mVitaminE = b.mVitaminE;
        mBiotin = b.mBiotin;
        mVitaminD = b.mVitaminD;
        mNiacin = b.mNiacin;
        mMagnesium = b.mMagnesium;
        mTotalCarbohydrate = b.mTotalCarbohydrate;
        mVitaminK = b.mVitaminK;
        mPolyunsaturatedFat = b.mPolyunsaturatedFat;
        mSaturatedFat = b.mSaturatedFat;
        mSodium = b.mSodium;
        mFolate = b.mFolate;
        mMonounsaturatedFat = b.mMonounsaturatedFat;
        mPantothenicAcid = b.mPantothenicAcid;
        mIron = b.mIron;
        mVitaminA = b.mVitaminA;
        mFolicAcid = b.mFolicAcid;
        mSugar = b.mSugar;
    }

    public int getMealType() { return mMealType; }
    public String getMealName() { return mMealName; }
    public Mass getUnsaturatedFat() { return mUnsaturatedFat; }
    public Mass getPotassium() { return mPotassium; }
    public Mass getThiamin() { return mThiamin; }
    public Mass getTransFat() { return mTransFat; }
    public Mass getManganese() { return mManganese; }
    public Energy getEnergyFromFat() { return mEnergyFromFat; }
    public Mass getCaffeine() { return mCaffeine; }
    public Mass getDietaryFiber() { return mDietaryFiber; }
    public Mass getSelenium() { return mSelenium; }
    public Mass getVitaminB6() { return mVitaminB6; }
    public Mass getProtein() { return mProtein; }
    public Mass getChloride() { return mChloride; }
    public Mass getCholesterol() { return mCholesterol; }
    public Mass getCopper() { return mCopper; }
    public Mass getIodine() { return mIodine; }
    public Mass getVitaminB12() { return mVitaminB12; }
    public Mass getZinc() { return mZinc; }
    public Mass getRiboflavin() { return mRiboflavin; }
    public Energy getEnergy() { return mEnergy; }
    public Mass getMolybdenum() { return mMolybdenum; }
    public Mass getPhosphorus() { return mPhosphorus; }
    public Mass getChromium() { return mChromium; }
    public Mass getTotalFat() { return mTotalFat; }
    public Mass getCalcium() { return mCalcium; }
    public Mass getVitaminC() { return mVitaminC; }
    public Mass getVitaminE() { return mVitaminE; }
    public Mass getBiotin() { return mBiotin; }
    public Mass getVitaminD() { return mVitaminD; }
    public Mass getNiacin() { return mNiacin; }
    public Mass getMagnesium() { return mMagnesium; }
    public Mass getTotalCarbohydrate() { return mTotalCarbohydrate; }
    public Mass getVitaminK() { return mVitaminK; }
    public Mass getPolyunsaturatedFat() { return mPolyunsaturatedFat; }
    public Mass getSaturatedFat() { return mSaturatedFat; }
    public Mass getSodium() { return mSodium; }
    public Mass getFolate() { return mFolate; }
    public Mass getMonounsaturatedFat() { return mMonounsaturatedFat; }
    public Mass getPantothenicAcid() { return mPantothenicAcid; }
    public Mass getIron() { return mIron; }
    public Mass getVitaminA() { return mVitaminA; }
    public Mass getFolicAcid() { return mFolicAcid; }
    public Mass getSugar() { return mSugar; }

    private static void addMass(Mass mass, AggregationType.Accumulator acc) {
        if (mass != null) {
            acc.addDouble(mass.getInGrams());
        }
    }

    @Override
    public void shirazCollectSamples(String metric, AggregationType.Accumulator acc) {
        if ("energy".equals(metric) && mEnergy != null) acc.addDouble(mEnergy.getInCalories());
        else if ("energyFromFat".equals(metric) && mEnergyFromFat != null) acc.addDouble(mEnergyFromFat.getInCalories());
        else if ("biotin".equals(metric)) addMass(mBiotin, acc);
        else if ("caffeine".equals(metric)) addMass(mCaffeine, acc);
        else if ("calcium".equals(metric)) addMass(mCalcium, acc);
        else if ("chloride".equals(metric)) addMass(mChloride, acc);
        else if ("cholesterol".equals(metric)) addMass(mCholesterol, acc);
        else if ("chromium".equals(metric)) addMass(mChromium, acc);
        else if ("copper".equals(metric)) addMass(mCopper, acc);
        else if ("dietaryFiber".equals(metric)) addMass(mDietaryFiber, acc);
        else if ("folate".equals(metric)) addMass(mFolate, acc);
        else if ("folicAcid".equals(metric)) addMass(mFolicAcid, acc);
        else if ("iodine".equals(metric)) addMass(mIodine, acc);
        else if ("iron".equals(metric)) addMass(mIron, acc);
        else if ("magnesium".equals(metric)) addMass(mMagnesium, acc);
        else if ("manganese".equals(metric)) addMass(mManganese, acc);
        else if ("molybdenum".equals(metric)) addMass(mMolybdenum, acc);
        else if ("monounsaturatedFat".equals(metric)) addMass(mMonounsaturatedFat, acc);
        else if ("niacin".equals(metric)) addMass(mNiacin, acc);
        else if ("pantothenicAcid".equals(metric)) addMass(mPantothenicAcid, acc);
        else if ("phosphorus".equals(metric)) addMass(mPhosphorus, acc);
        else if ("polyunsaturatedFat".equals(metric)) addMass(mPolyunsaturatedFat, acc);
        else if ("potassium".equals(metric)) addMass(mPotassium, acc);
        else if ("protein".equals(metric)) addMass(mProtein, acc);
        else if ("riboflavin".equals(metric)) addMass(mRiboflavin, acc);
        else if ("saturatedFat".equals(metric)) addMass(mSaturatedFat, acc);
        else if ("selenium".equals(metric)) addMass(mSelenium, acc);
        else if ("sodium".equals(metric)) addMass(mSodium, acc);
        else if ("sugar".equals(metric)) addMass(mSugar, acc);
        else if ("thiamin".equals(metric)) addMass(mThiamin, acc);
        else if ("totalCarbohydrate".equals(metric)) addMass(mTotalCarbohydrate, acc);
        else if ("totalFat".equals(metric)) addMass(mTotalFat, acc);
        else if ("transFat".equals(metric)) addMass(mTransFat, acc);
        else if ("unsaturatedFat".equals(metric)) addMass(mUnsaturatedFat, acc);
        else if ("vitaminA".equals(metric)) addMass(mVitaminA, acc);
        else if ("vitaminB12".equals(metric)) addMass(mVitaminB12, acc);
        else if ("vitaminB6".equals(metric)) addMass(mVitaminB6, acc);
        else if ("vitaminC".equals(metric)) addMass(mVitaminC, acc);
        else if ("vitaminD".equals(metric)) addMass(mVitaminD, acc);
        else if ("vitaminE".equals(metric)) addMass(mVitaminE, acc);
        else if ("vitaminK".equals(metric)) addMass(mVitaminK, acc);
        else if ("zinc".equals(metric)) addMass(mZinc, acc);
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object)) return false;
        NutritionRecord t = (NutritionRecord) object;
        return mMealType == t.mMealType
                && Objects.equals(mMealName, t.mMealName)
                && Objects.equals(mUnsaturatedFat, t.mUnsaturatedFat)
                && Objects.equals(mPotassium, t.mPotassium)
                && Objects.equals(mThiamin, t.mThiamin)
                && Objects.equals(mTransFat, t.mTransFat)
                && Objects.equals(mManganese, t.mManganese)
                && Objects.equals(mEnergyFromFat, t.mEnergyFromFat)
                && Objects.equals(mCaffeine, t.mCaffeine)
                && Objects.equals(mDietaryFiber, t.mDietaryFiber)
                && Objects.equals(mSelenium, t.mSelenium)
                && Objects.equals(mVitaminB6, t.mVitaminB6)
                && Objects.equals(mProtein, t.mProtein)
                && Objects.equals(mChloride, t.mChloride)
                && Objects.equals(mCholesterol, t.mCholesterol)
                && Objects.equals(mCopper, t.mCopper)
                && Objects.equals(mIodine, t.mIodine)
                && Objects.equals(mVitaminB12, t.mVitaminB12)
                && Objects.equals(mZinc, t.mZinc)
                && Objects.equals(mRiboflavin, t.mRiboflavin)
                && Objects.equals(mEnergy, t.mEnergy)
                && Objects.equals(mMolybdenum, t.mMolybdenum)
                && Objects.equals(mPhosphorus, t.mPhosphorus)
                && Objects.equals(mChromium, t.mChromium)
                && Objects.equals(mTotalFat, t.mTotalFat)
                && Objects.equals(mCalcium, t.mCalcium)
                && Objects.equals(mVitaminC, t.mVitaminC)
                && Objects.equals(mVitaminE, t.mVitaminE)
                && Objects.equals(mBiotin, t.mBiotin)
                && Objects.equals(mVitaminD, t.mVitaminD)
                && Objects.equals(mNiacin, t.mNiacin)
                && Objects.equals(mMagnesium, t.mMagnesium)
                && Objects.equals(mTotalCarbohydrate, t.mTotalCarbohydrate)
                && Objects.equals(mVitaminK, t.mVitaminK)
                && Objects.equals(mPolyunsaturatedFat, t.mPolyunsaturatedFat)
                && Objects.equals(mSaturatedFat, t.mSaturatedFat)
                && Objects.equals(mSodium, t.mSodium)
                && Objects.equals(mFolate, t.mFolate)
                && Objects.equals(mMonounsaturatedFat, t.mMonounsaturatedFat)
                && Objects.equals(mPantothenicAcid, t.mPantothenicAcid)
                && Objects.equals(mIron, t.mIron)
                && Objects.equals(mVitaminA, t.mVitaminA)
                && Objects.equals(mFolicAcid, t.mFolicAcid)
                && Objects.equals(mSugar, t.mSugar);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mMealType, mMealName, mEnergy, mEnergyFromFat,
                mProtein, mTotalCarbohydrate, mTotalFat, mSugar, mSodium);
    }

    public static final class Builder {
        final Metadata mMetadata;
        final Instant mStartTime;
        final Instant mEndTime;
        ZoneOffset mStartZoneOffset;
        ZoneOffset mEndZoneOffset;
        int mMealType = MealType.MEAL_TYPE_UNKNOWN;
        String mMealName;
        Mass mUnsaturatedFat;
        Mass mPotassium;
        Mass mThiamin;
        Mass mTransFat;
        Mass mManganese;
        Energy mEnergyFromFat;
        Mass mCaffeine;
        Mass mDietaryFiber;
        Mass mSelenium;
        Mass mVitaminB6;
        Mass mProtein;
        Mass mChloride;
        Mass mCholesterol;
        Mass mCopper;
        Mass mIodine;
        Mass mVitaminB12;
        Mass mZinc;
        Mass mRiboflavin;
        Energy mEnergy;
        Mass mMolybdenum;
        Mass mPhosphorus;
        Mass mChromium;
        Mass mTotalFat;
        Mass mCalcium;
        Mass mVitaminC;
        Mass mVitaminE;
        Mass mBiotin;
        Mass mVitaminD;
        Mass mNiacin;
        Mass mMagnesium;
        Mass mTotalCarbohydrate;
        Mass mVitaminK;
        Mass mPolyunsaturatedFat;
        Mass mSaturatedFat;
        Mass mSodium;
        Mass mFolate;
        Mass mMonounsaturatedFat;
        Mass mPantothenicAcid;
        Mass mIron;
        Mass mVitaminA;
        Mass mFolicAcid;
        Mass mSugar;

        public Builder(Metadata metadata, Instant startTime, Instant endTime) {
            mMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
            mStartTime = Objects.requireNonNull(startTime, "startTime must not be null");
            mEndTime = Objects.requireNonNull(endTime, "endTime must not be null");
        }

        public Builder setStartZoneOffset(ZoneOffset startZoneOffset) {
            mStartZoneOffset = Objects.requireNonNull(startZoneOffset, "startZoneOffset must not be null");
            return this;
        }

        public Builder setEndZoneOffset(ZoneOffset endZoneOffset) {
            mEndZoneOffset = Objects.requireNonNull(endZoneOffset, "endZoneOffset must not be null");
            return this;
        }

        public Builder clearStartZoneOffset() { mStartZoneOffset = null; return this; }
        public Builder clearEndZoneOffset() { mEndZoneOffset = null; return this; }
        public Builder setUnsaturatedFat(Mass v) { mUnsaturatedFat = v; return this; }
        public Builder setPotassium(Mass v) { mPotassium = v; return this; }
        public Builder setThiamin(Mass v) { mThiamin = v; return this; }
        public Builder setMealType(int v) { mMealType = v; return this; }
        public Builder setTransFat(Mass v) { mTransFat = v; return this; }
        public Builder setManganese(Mass v) { mManganese = v; return this; }
        public Builder setEnergyFromFat(Energy v) { mEnergyFromFat = v; return this; }
        public Builder setCaffeine(Mass v) { mCaffeine = v; return this; }
        public Builder setDietaryFiber(Mass v) { mDietaryFiber = v; return this; }
        public Builder setSelenium(Mass v) { mSelenium = v; return this; }
        public Builder setVitaminB6(Mass v) { mVitaminB6 = v; return this; }
        public Builder setProtein(Mass v) { mProtein = v; return this; }
        public Builder setChloride(Mass v) { mChloride = v; return this; }
        public Builder setCholesterol(Mass v) { mCholesterol = v; return this; }
        public Builder setCopper(Mass v) { mCopper = v; return this; }
        public Builder setIodine(Mass v) { mIodine = v; return this; }
        public Builder setVitaminB12(Mass v) { mVitaminB12 = v; return this; }
        public Builder setZinc(Mass v) { mZinc = v; return this; }
        public Builder setRiboflavin(Mass v) { mRiboflavin = v; return this; }
        public Builder setEnergy(Energy v) { mEnergy = v; return this; }
        public Builder setMolybdenum(Mass v) { mMolybdenum = v; return this; }
        public Builder setPhosphorus(Mass v) { mPhosphorus = v; return this; }
        public Builder setChromium(Mass v) { mChromium = v; return this; }
        public Builder setTotalFat(Mass v) { mTotalFat = v; return this; }
        public Builder setCalcium(Mass v) { mCalcium = v; return this; }
        public Builder setVitaminC(Mass v) { mVitaminC = v; return this; }
        public Builder setVitaminE(Mass v) { mVitaminE = v; return this; }
        public Builder setBiotin(Mass v) { mBiotin = v; return this; }
        public Builder setVitaminD(Mass v) { mVitaminD = v; return this; }
        public Builder setNiacin(Mass v) { mNiacin = v; return this; }
        public Builder setMagnesium(Mass v) { mMagnesium = v; return this; }
        public Builder setTotalCarbohydrate(Mass v) { mTotalCarbohydrate = v; return this; }
        public Builder setVitaminK(Mass v) { mVitaminK = v; return this; }
        public Builder setPolyunsaturatedFat(Mass v) { mPolyunsaturatedFat = v; return this; }
        public Builder setSaturatedFat(Mass v) { mSaturatedFat = v; return this; }
        public Builder setSodium(Mass v) { mSodium = v; return this; }
        public Builder setFolate(Mass v) { mFolate = v; return this; }
        public Builder setMonounsaturatedFat(Mass v) { mMonounsaturatedFat = v; return this; }
        public Builder setPantothenicAcid(Mass v) { mPantothenicAcid = v; return this; }
        public Builder setMealName(String v) { mMealName = v; return this; }
        public Builder setIron(Mass v) { mIron = v; return this; }
        public Builder setVitaminA(Mass v) { mVitaminA = v; return this; }
        public Builder setFolicAcid(Mass v) { mFolicAcid = v; return this; }
        public Builder setSugar(Mass v) { mSugar = v; return this; }

        public NutritionRecord build() {
            return new NutritionRecord(this);
        }
    }
}
