// SPDX-License-Identifier: GPL-3.0-only
package android.gesture;

public class Prediction {
    public final String name;
    public double score;

    Prediction(String label, double predictionScore) {
        name = label;
        score = predictionScore;
    }

    @Override
    public String toString() {
        return name;
    }
}
