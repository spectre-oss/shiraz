// SPDX-License-Identifier: GPL-3.0-only
package android.app.admin;

import java.time.MonthDay;
import java.util.Objects;

public class FreezePeriod {
    private final MonthDay mStart;
    private final MonthDay mEnd;

    public FreezePeriod(MonthDay start, MonthDay end) {
        mStart = Objects.requireNonNull(start, "start");
        mEnd = Objects.requireNonNull(end, "end");
    }

    public MonthDay getStart() {
        return mStart;
    }

    public MonthDay getEnd() {
        return mEnd;
    }

    @Override
    public String toString() {
        return mStart.toString() + " .. " + mEnd.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FreezePeriod)) return false;
        FreezePeriod other = (FreezePeriod) o;
        return Objects.equals(mStart, other.mStart) && Objects.equals(mEnd, other.mEnd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mStart, mEnd);
    }
}
