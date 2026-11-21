// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.content.res.Configuration;
import java.util.Objects;

/** Display override configuration; compatibility scaling is not yet provided. */
public class DisplayAdjustments {
    public static final DisplayAdjustments DEFAULT_DISPLAY_ADJUSTMENTS = new DisplayAdjustments();
    private Configuration configuration;

    public DisplayAdjustments() { }
    public DisplayAdjustments(Configuration value) {
        configuration = value == null ? new Configuration() : new Configuration(value);
    }
    public DisplayAdjustments(DisplayAdjustments other) {
        if (other == null) throw new NullPointerException("display adjustments");
        configuration = other.configuration == null ? new Configuration() : new Configuration(other.configuration);
    }
    public void setConfiguration(Configuration value) {
        if (this == DEFAULT_DISPLAY_ADJUSTMENTS) throw new IllegalArgumentException("Cannot modify default display adjustments");
        if (configuration == null) configuration = new Configuration();
        configuration.setTo(value == null ? new Configuration() : value);
    }
    public Configuration getConfiguration() { return configuration; }
    @Override public boolean equals(Object other) {
        return other instanceof DisplayAdjustments && Objects.equals(configuration, ((DisplayAdjustments) other).configuration);
    }
    @Override public int hashCode() { return 31 * 17 + Objects.hashCode(configuration); }
}
