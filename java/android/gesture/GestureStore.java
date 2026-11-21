// SPDX-License-Identifier: GPL-3.0-only
package android.gesture;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GestureStore {
    public static final int SEQUENCE_INVARIANT = 1;
    public static final int SEQUENCE_SENSITIVE = 2;

    public static final int ORIENTATION_INVARIANT = 1;
    public static final int ORIENTATION_SENSITIVE = 2;

    static final int ORIENTATION_SENSITIVE_4 = 4;
    static final int ORIENTATION_SENSITIVE_8 = 8;

    private static final short FILE_FORMAT_VERSION = 1;
    private static final int SEQUENCE_SAMPLE_SIZE = 16;
    private static final int PATCH_SAMPLE_SIZE = 16;

    private static final float[] ORIENTATIONS = new float[] {
            0.0f,
            (float) (Math.PI / 4.0),
            (float) (Math.PI / 2.0),
            (float) (Math.PI * 3.0 / 4.0),
            (float) Math.PI,
            -0.0f,
            (float) (-Math.PI / 4.0),
            (float) (-Math.PI / 2.0),
            (float) (-Math.PI * 3.0 / 4.0),
            (float) -Math.PI
    };

    private static final Comparator<Prediction> PREDICTION_COMPARATOR =
            new Comparator<Prediction>() {
                @Override
                public int compare(Prediction object1, Prediction object2) {
                    double score1 = object1.score;
                    double score2 = object2.score;
                    if (score1 > score2) {
                        return -1;
                    } else if (score1 < score2) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
            };

    private int mSequenceType = SEQUENCE_SENSITIVE;
    private int mOrientationStyle = ORIENTATION_SENSITIVE;

    private final HashMap<String, ArrayList<Gesture>> mNamedGestures =
            new HashMap<String, ArrayList<Gesture>>();
    private boolean mChanged = false;

    public GestureStore() {
    }

    public void setOrientationStyle(int style) {
        mOrientationStyle = style;
    }

    public int getOrientationStyle() {
        return mOrientationStyle;
    }

    public void setSequenceType(int type) {
        mSequenceType = type;
    }

    public int getSequenceType() {
        return mSequenceType;
    }

    public Set<String> getGestureEntries() {
        return mNamedGestures.keySet();
    }

    public ArrayList<Prediction> recognize(Gesture gesture) {
        ArrayList<Prediction> predictions = new ArrayList<Prediction>();
        if (gesture == null || gesture.getStrokesCount() == 0) {
            return predictions;
        }
        float[] queryVector = computeFeatureVector(mSequenceType, mOrientationStyle, gesture);

        for (Map.Entry<String, ArrayList<Gesture>> entry : mNamedGestures.entrySet()) {
            String name = entry.getKey();
            ArrayList<Gesture> examples = entry.getValue();
            if (examples == null || examples.isEmpty()) {
                continue;
            }
            double bestWeight = -1.0;
            final int count = examples.size();
            for (int i = 0; i < count; i++) {
                Gesture sample = examples.get(i);
                if (sample == null || sample.getStrokesCount() == 0) {
                    continue;
                }
                float[] sampleVector =
                        computeFeatureVector(mSequenceType, mOrientationStyle, sample);
                if (sampleVector.length != queryVector.length) {
                    continue;
                }
                double distance;
                if (mSequenceType == SEQUENCE_SENSITIVE) {
                    if (mOrientationStyle == ORIENTATION_INVARIANT) {
                        distance = GestureUtils.minimumCosineDistance(
                                sampleVector, queryVector, ORIENTATION_INVARIANT);
                    } else if (mOrientationStyle == ORIENTATION_SENSITIVE) {
                        distance = GestureUtils.cosineDistance(sampleVector, queryVector);
                    } else {
                        distance = GestureUtils.minimumCosineDistance(
                                sampleVector, queryVector, mOrientationStyle);
                    }
                } else {
                    distance = GestureUtils.squaredEuclideanDistance(sampleVector, queryVector);
                }

                double weight;
                if (distance == 0.0) {
                    weight = Double.MAX_VALUE;
                } else {
                    weight = 1.0 / distance;
                }
                if (weight > bestWeight) {
                    bestWeight = weight;
                }
            }
            if (bestWeight >= 0.0) {
                predictions.add(new Prediction(name, bestWeight));
            }
        }

        Collections.sort(predictions, PREDICTION_COMPARATOR);
        return predictions;
    }

    private static float[] computeFeatureVector(
            int sequenceType, int orientationType, Gesture gesture) {
        if (sequenceType == SEQUENCE_SENSITIVE) {
            return temporalSampler(orientationType, gesture);
        } else {
            if (orientationType == ORIENTATION_INVARIANT) {
                return rotationNormalizedSpatialSampler(gesture);
            }
            return GestureUtils.spatialSampling(gesture, PATCH_SAMPLE_SIZE, false);
        }
    }

    private static float[] rotationNormalizedSpatialSampler(Gesture gesture) {
        ArrayList<GestureStroke> strokes = gesture.getStrokes();
        if (strokes.isEmpty() || strokes.get(0).points.length < 2) {
            return GestureUtils.spatialSampling(gesture, PATCH_SAMPLE_SIZE, false);
        }
        OrientedBoundingBox obb = GestureUtils.computeOrientedBoundingBox(strokes.get(0).points);
        float radians = (float) (-Math.toRadians(obb.orientation));
        Gesture normalized = new Gesture();
        for (int i = 0; i < strokes.size(); i++) {
            float[] pts = strokes.get(i).points.clone();
            GestureUtils.translate(pts, -obb.centerX, -obb.centerY);
            GestureUtils.rotate(pts, radians);
            ArrayList<GesturePoint> gp = new ArrayList<GesturePoint>(pts.length / 2);
            for (int j = 0; j < pts.length; j += 2) {
                gp.add(new GesturePoint(pts[j], pts[j + 1], j));
            }
            normalized.addStroke(new GestureStroke(gp));
        }
        return GestureUtils.spatialSampling(normalized, PATCH_SAMPLE_SIZE, false);
    }

    private static float[] temporalSampler(int orientationType, Gesture gesture) {
        float[] pts = GestureUtils.temporalSampling(
                gesture.getStrokes().get(0), SEQUENCE_SAMPLE_SIZE);
        float[] center = GestureUtils.computeCentroid(pts);
        float orientation = (float) Math.atan2(pts[1] - center[1], pts[0] - center[0]);

        float adjustment = -orientation;
        if (orientationType == ORIENTATION_SENSITIVE) {
            adjustment = 0.0f;
        } else if (orientationType != ORIENTATION_INVARIANT) {
            int count = ORIENTATIONS.length;
            for (int i = 0; i < count; i++) {
                float delta = ORIENTATIONS[i] - orientation;
                if (Math.abs(delta) < Math.abs(adjustment)) {
                    adjustment = delta;
                }
            }
        }

        GestureUtils.translate(pts, -center[0], -center[1]);
        if (adjustment != 0.0f) {
            GestureUtils.rotate(pts, adjustment);
        }

        float sum = 0.0f;
        int size = pts.length;
        for (int i = 0; i < size; i++) {
            sum += pts[i] * pts[i];
        }
        float magnitude = (float) Math.sqrt(sum);
        if (magnitude > 0.0f) {
            for (int i = 0; i < size; i++) {
                pts[i] /= magnitude;
            }
        }
        return pts;
    }

    public void addGesture(String entryName, Gesture gesture) {
        if (entryName == null || entryName.length() == 0 || gesture == null) {
            return;
        }
        ArrayList<Gesture> gestures = mNamedGestures.get(entryName);
        if (gestures == null) {
            gestures = new ArrayList<Gesture>();
            mNamedGestures.put(entryName, gestures);
        }
        gestures.add(gesture);
        mChanged = true;
    }

    public void removeGesture(String entryName, Gesture gesture) {
        ArrayList<Gesture> gestures = mNamedGestures.get(entryName);
        if (gestures == null || gesture == null) {
            return;
        }
        boolean removed = gestures.remove(gesture);
        if (!removed) {
            for (int i = 0; i < gestures.size(); i++) {
                if (gestures.get(i).getID() == gesture.getID()) {
                    gestures.remove(i);
                    removed = true;
                    break;
                }
            }
        }
        if (gestures.isEmpty()) {
            mNamedGestures.remove(entryName);
        }
        if (removed) {
            mChanged = true;
        }
    }

    public void removeEntry(String entryName) {
        if (mNamedGestures.remove(entryName) != null) {
            mChanged = true;
        }
    }

    public ArrayList<Gesture> getGestures(String entryName) {
        ArrayList<Gesture> gestures = mNamedGestures.get(entryName);
        if (gestures != null) {
            return new ArrayList<Gesture>(gestures);
        } else {
            return null;
        }
    }

    public boolean hasChanged() {
        return mChanged;
    }

    public void save(OutputStream stream) throws IOException {
        save(stream, false);
    }

    public void save(OutputStream stream, boolean closeStream) throws IOException {
        DataOutputStream out = null;
        try {
            OutputStream target = (stream instanceof BufferedOutputStream)
                    ? stream : new BufferedOutputStream(stream, 32768);
            out = new DataOutputStream(target);
            out.writeShort(FILE_FORMAT_VERSION);
            out.writeInt(mNamedGestures.size());

            for (Map.Entry<String, ArrayList<Gesture>> entry : mNamedGestures.entrySet()) {
                final String key = entry.getKey();
                final ArrayList<Gesture> examples = entry.getValue();
                final int count = examples.size();

                out.writeUTF(key);
                out.writeInt(count);
                for (int i = 0; i < count; i++) {
                    examples.get(i).serialize(out);
                }
            }
            out.flush();
            mChanged = false;
        } finally {
            if (closeStream) {
                GestureUtils.closeStream(out);
            }
        }
    }

    public void load(InputStream stream) throws IOException {
        load(stream, false);
    }

    public void load(InputStream stream, boolean closeStream) throws IOException {
        DataInputStream in = null;
        try {
            InputStream source = (stream instanceof BufferedInputStream)
                    ? stream : new BufferedInputStream(stream, 32768);
            in = new DataInputStream(source);

            final short versionNumber = in.readShort();
            if (versionNumber != FILE_FORMAT_VERSION) {
                throw new IOException("Unsupported GestureStore format version: " + versionNumber);
            }

            mNamedGestures.clear();
            final int entryCount = in.readInt();
            for (int i = 0; i < entryCount; i++) {
                final String key = in.readUTF();
                final int gestureCount = in.readInt();
                final ArrayList<Gesture> gestures = new ArrayList<Gesture>(gestureCount);
                for (int j = 0; j < gestureCount; j++) {
                    final Gesture gesture = Gesture.deserialize(in);
                    gestures.add(gesture);
                }
                mNamedGestures.put(key, gestures);
            }
            mChanged = false;
        } finally {
            if (closeStream) {
                GestureUtils.closeStream(in);
            }
        }
    }
}
