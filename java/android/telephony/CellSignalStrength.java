// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

public abstract class CellSignalStrength {
    public static final int SIGNAL_STRENGTH_NONE_OR_UNKNOWN = 0;
    public static final int SIGNAL_STRENGTH_POOR = 1;
    public static final int SIGNAL_STRENGTH_MODERATE = 2;
    public static final int SIGNAL_STRENGTH_GOOD = 3;
    public static final int SIGNAL_STRENGTH_GREAT = 4;

    CellSignalStrength() {
    }

    public abstract int getLevel();

    public abstract int getAsuLevel();

    public abstract int getDbm();

    public abstract int hashCode();

    public abstract boolean equals(Object o);
}
