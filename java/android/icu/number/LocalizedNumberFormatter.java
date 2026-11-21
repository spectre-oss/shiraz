// SPDX-License-Identifier: GPL-3.0-only
package android.icu.number;

import android.icu.text.CompactDecimalFormat;
import android.icu.text.DecimalFormat;
import android.icu.text.DecimalFormatSymbols;
import android.icu.text.DisplayOptions;
import android.icu.text.MeasureFormat;
import android.icu.text.NumberFormat;
import android.icu.text.NumberingSystem;
import android.icu.util.Currency;
import android.icu.util.Measure;
import android.icu.util.MeasureUnit;
import android.icu.util.ULocale;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;

/** A NumberFormatter that has a locale associated with it and executes number formatting. */
public class LocalizedNumberFormatter extends NumberFormatterSettings<LocalizedNumberFormatter> {
    LocalizedNumberFormatter(NumberFormatterSettings<?> parent, int key, Object value) {
        super(parent, key, value);
    }

    @Override
    LocalizedNumberFormatter create(int key, Object value) {
        return new LocalizedNumberFormatter(this, key, value);
    }

    public FormattedNumber format(long input) {
        return formatInternal(BigDecimal.valueOf(input), null);
    }

    public FormattedNumber format(double input) {
        if (Double.isNaN(input) || Double.isInfinite(input)) {
            ULocale uloc = resolveLocale();
            String s = NumberFormat.getInstance(uloc).format(input);
            return new FormattedNumber(s, BigDecimal.ZERO, (MeasureUnit) resolveSetting(KEY_UNIT), DisplayOptions.NounClass.UNDEFINED);
        }
        return formatInternal(BigDecimal.valueOf(input), null);
    }

    public FormattedNumber format(Number input) {
        if (input == null) {
            throw new IllegalArgumentException("Number must not be null");
        }
        if (input instanceof BigDecimal) {
            return formatInternal((BigDecimal) input, null);
        }
        if (input instanceof Double || input instanceof Float) {
            return format(input.doubleValue());
        }
        return formatInternal(new BigDecimal(input.toString()), null);
    }

    public FormattedNumber format(Measure input) {
        if (input == null) {
            throw new IllegalArgumentException("Measure must not be null");
        }
        Number num = input.getNumber();
        BigDecimal bd = num instanceof BigDecimal
                ? (BigDecimal) num
                : BigDecimal.valueOf(num.doubleValue());
        return formatInternal(bd, input.getUnit());
    }

    private String formatJdkFallback(
            BigDecimal value, ULocale uloc, Notation notation, MeasureUnit unit,
            Precision prec, RoundingMode rm) {
        java.util.Locale jloc = uloc != null ? uloc.toLocale() : java.util.Locale.getDefault();
        java.text.NumberFormat jnf;
        if (unit instanceof Currency) {
            jnf = java.text.NumberFormat.getCurrencyInstance(jloc);
            try {
                jnf.setCurrency(java.util.Currency.getInstance(((Currency) unit).getCurrencyCode()));
            } catch (IllegalArgumentException ignored) {
            }
        } else if (notation instanceof ScientificNotation && jnfScientificSupported(jloc)) {
            jnf = new java.text.DecimalFormat("0.######E0", java.text.DecimalFormatSymbols.getInstance(jloc));
        } else {
            jnf = java.text.NumberFormat.getNumberInstance(jloc);
        }
        jnf.setRoundingMode(rm);
        NumberFormatter.GroupingStrategy grp =
                (NumberFormatter.GroupingStrategy) resolveSetting(KEY_GROUPING);
        if (grp == NumberFormatter.GroupingStrategy.OFF) {
            jnf.setGroupingUsed(false);
        } else if (grp != null) {
            jnf.setGroupingUsed(true);
        }
        IntegerWidth iw = (IntegerWidth) resolveSetting(KEY_INTEGER_WIDTH);
        if (iw != null) {
            jnf.setMinimumIntegerDigits(iw.mMinInt);
            if (iw.mMaxInt >= 0) {
                jnf.setMaximumIntegerDigits(iw.mMaxInt);
            }
        }
        if (prec != null) {
            if (prec.mMinFrac >= 0) jnf.setMinimumFractionDigits(prec.mMinFrac);
            if (prec.mMaxFrac >= 0) jnf.setMaximumFractionDigits(prec.mMaxFrac);
            if (prec.mTrailingZeroDisplay == NumberFormatter.TrailingZeroDisplay.HIDE_IF_WHOLE) {
                jnf.setMinimumFractionDigits(0);
            }
        }
        if (jnf instanceof java.text.DecimalFormat
                && resolveSetting(KEY_DECIMAL) == NumberFormatter.DecimalSeparatorDisplay.ALWAYS) {
            ((java.text.DecimalFormat) jnf).setDecimalSeparatorAlwaysShown(true);
        }
        String base = jnf.format(value);
        if (unit != null && !(unit instanceof Currency)
                && resolveSetting(KEY_UNIT_WIDTH) != NumberFormatter.UnitWidth.HIDDEN) {
            return base + " " + unit.getSubtype();
        }
        return base;
    }

    private static boolean jnfScientificSupported(java.util.Locale loc) {
        return loc != null;
    }

    private FormattedNumber formatInternal(BigDecimal rawValue, MeasureUnit overrideUnit) {
        ULocale uloc = resolveLocale();
        Scale scale = (Scale) resolveSetting(KEY_SCALE);
        BigDecimal value = scale != null ? scale.applyTo(rawValue) : rawValue;

        RoundingMode rm = (RoundingMode) resolveSetting(KEY_ROUNDING_MODE);
        if (rm == null) rm = RoundingMode.HALF_EVEN;

        Precision prec = (Precision) resolveSetting(KEY_PRECISION);
        if (prec != null && prec.mIncrement != null) {
            value = value.divide(prec.mIncrement, 0, rm).multiply(prec.mIncrement);
        } else if (prec != null && prec.mMaxFrac >= 0 && prec.mMaxFrac < 999) {
            value = value.setScale(prec.mMaxFrac, rm);
        }

        Notation notation = (Notation) resolveSetting(KEY_NOTATION);
        MeasureUnit unit = overrideUnit != null ? overrideUnit : (MeasureUnit) resolveSetting(KEY_UNIT);
        DisplayOptions disp = (DisplayOptions) resolveSetting(KEY_DISPLAY_OPTIONS);
        DisplayOptions.NounClass noun = disp != null ? disp.getNounClass() : DisplayOptions.NounClass.UNDEFINED;

        String formatted;
        try {
            if (notation instanceof CompactNotation) {
                CompactDecimalFormat.CompactStyle style = ((CompactNotation) notation).mLongStyle
                        ? CompactDecimalFormat.CompactStyle.LONG
                        : CompactDecimalFormat.CompactStyle.SHORT;
                CompactDecimalFormat cdf = CompactDecimalFormat.getInstance(uloc, style);
                formatted = cdf.format(value);
            } else if (unit instanceof Currency) {
                NumberFormat cf = NumberFormat.getCurrencyInstance(uloc);
                cf.setCurrency((Currency) unit);
                applyFormatSettings(cf, uloc, prec, rm);
                formatted = cf.format(value);
            } else {
                NumberFormat nf = notation instanceof ScientificNotation
                        ? NumberFormat.getScientificInstance(uloc)
                        : NumberFormat.getNumberInstance(uloc);
                applyFormatSettings(nf, uloc, prec, rm);
                if (unit != null && resolveSetting(KEY_UNIT_WIDTH) != NumberFormatter.UnitWidth.HIDDEN) {
                    MeasureFormat.FormatWidth fw = MeasureFormat.FormatWidth.SHORT;
                    NumberFormatter.UnitWidth uw = (NumberFormatter.UnitWidth) resolveSetting(KEY_UNIT_WIDTH);
                    if (uw == NumberFormatter.UnitWidth.FULL_NAME) fw = MeasureFormat.FormatWidth.WIDE;
                    else if (uw == NumberFormatter.UnitWidth.NARROW) fw = MeasureFormat.FormatWidth.NARROW;
                    MeasureFormat mf = MeasureFormat.getInstance(uloc, fw, nf);
                    formatted = mf.format(new Measure(value, unit));
                } else {
                    formatted = nf.format(value);
                }
            }
        } catch (LinkageError hostJvmFallback) {
            formatted = formatJdkFallback(value, uloc, notation, unit, prec, rm);
        }

        NumberFormatter.SignDisplay sign = (NumberFormatter.SignDisplay) resolveSetting(KEY_SIGN);
        if ((sign == NumberFormatter.SignDisplay.ALWAYS
                        || sign == NumberFormatter.SignDisplay.ACCOUNTING_ALWAYS)
                && value.signum() >= 0 && !formatted.startsWith("+")) {
            formatted = "+" + formatted;
        } else if ((sign == NumberFormatter.SignDisplay.EXCEPT_ZERO
                        || sign == NumberFormatter.SignDisplay.ACCOUNTING_EXCEPT_ZERO)
                && value.signum() > 0 && !formatted.startsWith("+")) {
            formatted = "+" + formatted;
        } else if (sign == NumberFormatter.SignDisplay.NEVER && formatted.startsWith("-")) {
            formatted = formatted.substring(1);
        }

        return new FormattedNumber(formatted, value, unit, noun);
    }

    private void applyFormatSettings(
            NumberFormat nf, ULocale uloc, Precision prec, RoundingMode rm) {
        nf.setRoundingMode(rm.ordinal());
        Object sym = resolveSetting(KEY_SYMBOLS);
        if (nf instanceof DecimalFormat) {
            DecimalFormat df = (DecimalFormat) nf;
            if (sym instanceof DecimalFormatSymbols) {
                df.setDecimalFormatSymbols((DecimalFormatSymbols) sym);
            } else if (sym instanceof NumberingSystem) {
                ULocale nsLoc = uloc.setKeywordValue("numbers", ((NumberingSystem) sym).getName());
                df.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(nsLoc));
            }
            if (resolveSetting(KEY_DECIMAL) == NumberFormatter.DecimalSeparatorDisplay.ALWAYS) {
                df.setDecimalSeparatorAlwaysShown(true);
            }
        }
        NumberFormatter.GroupingStrategy grp =
                (NumberFormatter.GroupingStrategy) resolveSetting(KEY_GROUPING);
        if (grp == NumberFormatter.GroupingStrategy.OFF) {
            nf.setGroupingUsed(false);
        } else if (grp != null) {
            nf.setGroupingUsed(true);
        }
        IntegerWidth iw = (IntegerWidth) resolveSetting(KEY_INTEGER_WIDTH);
        if (iw != null) {
            nf.setMinimumIntegerDigits(iw.mMinInt);
            if (iw.mMaxInt >= 0) {
                nf.setMaximumIntegerDigits(iw.mMaxInt);
            }
        }
        if (prec != null) {
            if (prec.mMinFrac >= 0) nf.setMinimumFractionDigits(prec.mMinFrac);
            if (prec.mMaxFrac >= 0) nf.setMaximumFractionDigits(prec.mMaxFrac);
            if (prec.mTrailingZeroDisplay == NumberFormatter.TrailingZeroDisplay.HIDE_IF_WHOLE) {
                nf.setMinimumFractionDigits(0);
            }
            if (nf instanceof DecimalFormat && prec.mMinSig >= 1) {
                DecimalFormat df = (DecimalFormat) nf;
                df.setSignificantDigitsUsed(true);
                df.setMinimumSignificantDigits(prec.mMinSig);
                df.setMaximumSignificantDigits(prec.mMaxSig);
            }
        }
    }

    public Format toFormat() {
        return new Format() {
            @Override
            public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
                if (obj instanceof Measure) {
                    toAppendTo.append(LocalizedNumberFormatter.this.format((Measure) obj).toString());
                } else if (obj instanceof Number) {
                    toAppendTo.append(LocalizedNumberFormatter.this.format((Number) obj).toString());
                } else {
                    throw new IllegalArgumentException("Cannot format given Object as a Number");
                }
                return toAppendTo;
            }

            @Override
            public Object parseObject(String source, ParsePosition pos) {
                throw new UnsupportedOperationException("Parsing is not supported by NumberFormatter");
            }
        };
    }
}
