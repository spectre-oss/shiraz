// SPDX-License-Identifier: GPL-3.0-only
package android.gesture;

import android.graphics.RectF;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public final class GestureUtils {
    private static final float SCALING_THRESHOLD = 0.26f;
    private static final float NONUNIFORM_SCALE = (float) Math.sqrt(2.0);

    private GestureUtils() {
    }

    static void closeStream(Closeable stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static float[] spatialSampling(Gesture gesture, int bitmapSize) {
        return spatialSampling(gesture, bitmapSize, false);
    }

    public static float[] spatialSampling(Gesture gesture, int bitmapSize, boolean keepAspectRatio) {
        if (bitmapSize <= 0) {
            throw new IllegalArgumentException("bitmapSize must be > 0");
        }
        final float targetPatchSize = bitmapSize - 1;
        float[] sample = new float[bitmapSize * bitmapSize];
        Arrays.fill(sample, 0.0f);

        RectF rect = gesture.getBoundingBox();
        final float gestureWidth = rect.width();
        final float gestureHeight = rect.height();
        float sx = gestureWidth > 0.0f ? targetPatchSize / gestureWidth : 1.0f;
        float sy = gestureHeight > 0.0f ? targetPatchSize / gestureHeight : 1.0f;

        if (keepAspectRatio) {
            float scale;
            if (gestureWidth > 0.0f && gestureHeight > 0.0f) {
                scale = sx < sy ? sx : sy;
            } else if (gestureWidth > 0.0f) {
                scale = sx;
            } else if (gestureHeight > 0.0f) {
                scale = sy;
            } else {
                scale = 1.0f;
            }
            sx = scale;
            sy = scale;
        } else if (gestureWidth > 0.0f && gestureHeight > 0.0f) {
            float aspectRatio = gestureWidth / gestureHeight;
            if (aspectRatio > 1.0f) {
                aspectRatio = 1.0f / aspectRatio;
            }
            if (aspectRatio < SCALING_THRESHOLD) {
                float scale = sx < sy ? sx : sy;
                sx = scale;
                sy = scale;
            } else {
                if (sx > sy) {
                    float scale = sy * NONUNIFORM_SCALE;
                    if (scale < sx) {
                        sx = scale;
                    }
                } else {
                    float scale = sx * NONUNIFORM_SCALE;
                    if (scale < sy) {
                        sy = scale;
                    }
                }
            }
        } else if (gestureWidth > 0.0f) {
            sy = sx;
        } else if (gestureHeight > 0.0f) {
            sx = sy;
        }

        float preDx = -rect.centerX();
        float preDy = -rect.centerY();
        float postDx = targetPatchSize / 2.0f;
        float postDy = targetPatchSize / 2.0f;

        final ArrayList<GestureStroke> strokes = gesture.getStrokes();
        final int count = strokes.size();

        for (int index = 0; index < count; index++) {
            final GestureStroke stroke = strokes.get(index);
            float[] strokepoints = stroke.points;
            int size = strokepoints.length;
            if (size < 2) {
                continue;
            }
            final float[] pts = new float[size];
            for (int i = 0; i < size; i += 2) {
                pts[i] = (strokepoints[i] + preDx) * sx + postDx;
                pts[i + 1] = (strokepoints[i + 1] + preDy) * sy + postDy;
            }

            float segmentEndX = -1.0f;
            float segmentEndY = -1.0f;
            for (int i = 0; i < size; i += 2) {
                float segmentStartX = pts[i] < 0.0f ? 0.0f : pts[i];
                float segmentStartY = pts[i + 1] < 0.0f ? 0.0f : pts[i + 1];
                if (segmentStartX > targetPatchSize) {
                    segmentStartX = targetPatchSize;
                }
                if (segmentStartY > targetPatchSize) {
                    segmentStartY = targetPatchSize;
                }
                plot(segmentStartX, segmentStartY, sample, bitmapSize);

                if (segmentEndX != -1.0f) {
                    if (segmentEndX > segmentStartX) {
                        float xpos = (float) Math.ceil(segmentStartX);
                        float slope = (segmentEndY - segmentStartY) / (segmentEndX - segmentStartX);
                        while (xpos < segmentEndX) {
                            float ypos = slope * (xpos - segmentStartX) + segmentStartY;
                            plot(xpos, ypos, sample, bitmapSize);
                            xpos++;
                        }
                    } else if (segmentEndX < segmentStartX) {
                        float xpos = (float) Math.ceil(segmentEndX);
                        float slope = (segmentEndY - segmentStartY) / (segmentEndX - segmentStartX);
                        while (xpos < segmentStartX) {
                            float ypos = slope * (xpos - segmentStartX) + segmentStartY;
                            plot(xpos, ypos, sample, bitmapSize);
                            xpos++;
                        }
                    }

                    if (segmentEndY > segmentStartY) {
                        float ypos = (float) Math.ceil(segmentStartY);
                        float invertSlope = (segmentEndX - segmentStartX) / (segmentEndY - segmentStartY);
                        while (ypos < segmentEndY) {
                            float xpos = invertSlope * (ypos - segmentStartY) + segmentStartX;
                            plot(xpos, ypos, sample, bitmapSize);
                            ypos++;
                        }
                    } else if (segmentEndY < segmentStartY) {
                        float ypos = (float) Math.ceil(segmentEndY);
                        float invertSlope = (segmentEndX - segmentStartX) / (segmentEndY - segmentStartY);
                        while (ypos < segmentStartY) {
                            float xpos = invertSlope * (ypos - segmentStartY) + segmentStartX;
                            plot(xpos, ypos, sample, bitmapSize);
                            ypos++;
                        }
                    }
                }
                segmentEndX = segmentStartX;
                segmentEndY = segmentStartY;
            }
        }

        return sample;
    }

    private static void plot(float x, float y, float[] sample, int sampleSize) {
        if (x < 0.0f) x = 0.0f;
        if (y < 0.0f) y = 0.0f;
        int xFloor = (int) Math.floor(x);
        int xCeiling = (int) Math.ceil(x);
        int yFloor = (int) Math.floor(y);
        int yCeiling = (int) Math.ceil(y);
        if (xFloor >= sampleSize) xFloor = sampleSize - 1;
        if (xCeiling >= sampleSize) xCeiling = sampleSize - 1;
        if (yFloor >= sampleSize) yFloor = sampleSize - 1;
        if (yCeiling >= sampleSize) yCeiling = sampleSize - 1;

        if (x == xFloor && y == yFloor) {
            int index = yCeiling * sampleSize + xCeiling;
            if (sample[index] < 1.0f) {
                sample[index] = 1.0f;
            }
        } else {
            final double xFloorSq = Math.pow(xFloor - x, 2.0);
            final double yFloorSq = Math.pow(yFloor - y, 2.0);
            final double xCeilingSq = Math.pow(xCeiling - x, 2.0);
            final double yCeilingSq = Math.pow(yCeiling - y, 2.0);
            float topLeft = (float) Math.sqrt(xFloorSq + yFloorSq);
            float topRight = (float) Math.sqrt(xCeilingSq + yFloorSq);
            float btmLeft = (float) Math.sqrt(xFloorSq + yCeilingSq);
            float btmRight = (float) Math.sqrt(xCeilingSq + yCeilingSq);
            float sum = topLeft + topRight + btmLeft + btmRight;

            float value = topLeft / sum;
            int index = yFloor * sampleSize + xFloor;
            if (value > sample[index]) {
                sample[index] = value;
            }

            value = topRight / sum;
            index = yFloor * sampleSize + xCeiling;
            if (value > sample[index]) {
                sample[index] = value;
            }

            value = btmLeft / sum;
            index = yCeiling * sampleSize + xFloor;
            if (value > sample[index]) {
                sample[index] = value;
            }

            value = btmRight / sum;
            index = yCeiling * sampleSize + xCeiling;
            if (value > sample[index]) {
                sample[index] = value;
            }
        }
    }

    public static float[] temporalSampling(GestureStroke stroke, int numPoints) {
        if (numPoints <= 0) {
            throw new IllegalArgumentException("numPoints must be > 0");
        }
        float[] vector = new float[numPoints * 2];
        final float[] pts = stroke.points;
        if (pts.length < 2) {
            return vector;
        }
        if (numPoints == 1) {
            vector[0] = pts[0];
            vector[1] = pts[1];
            return vector;
        }

        final float increment = stroke.length / (numPoints - 1);
        float distanceSoFar = 0.0f;
        float lstPointX = pts[0];
        float lstPointY = pts[1];
        int index = 0;
        float currentPointX = Float.MIN_VALUE;
        float currentPointY = Float.MIN_VALUE;

        vector[index++] = lstPointX;
        vector[index++] = lstPointY;

        int i = 2;
        int count = pts.length;
        while (i < count && index < vector.length) {
            if (currentPointX == Float.MIN_VALUE) {
                currentPointX = pts[i++];
                currentPointY = pts[i++];
            }
            float deltaX = currentPointX - lstPointX;
            float deltaY = currentPointY - lstPointY;
            float distance = (float) Math.hypot(deltaX, deltaY);
            if (increment > 0.0f && distanceSoFar + distance >= increment) {
                float ratio = (increment - distanceSoFar) / distance;
                float nx = lstPointX + ratio * deltaX;
                float ny = lstPointY + ratio * deltaY;
                vector[index++] = nx;
                vector[index++] = ny;
                lstPointX = nx;
                lstPointY = ny;
                distanceSoFar = 0.0f;
            } else {
                lstPointX = currentPointX;
                lstPointY = currentPointY;
                currentPointX = Float.MIN_VALUE;
                currentPointY = Float.MIN_VALUE;
                distanceSoFar += distance;
            }
        }

        for (int j = index; j < vector.length; j += 2) {
            vector[j] = lstPointX;
            vector[j + 1] = lstPointY;
        }
        return vector;
    }

    public static float[] computeCentroid(float[] points) {
        float centerX = 0.0f;
        float centerY = 0.0f;
        int count = points.length;
        if (count < 2) {
            return new float[] { 0.0f, 0.0f };
        }
        int numPoints = count / 2;
        for (int i = 0; i < numPoints * 2; i += 2) {
            centerX += points[i];
            centerY += points[i + 1];
        }
        return new float[] { centerX / numPoints, centerY / numPoints };
    }

    public static float[][] computeCoVariance(float[] points) {
        float[][] array = new float[2][2];
        int count = points.length / 2;
        if (count == 0) {
            return array;
        }
        float[] center = computeCentroid(points);
        float cx = center[0];
        float cy = center[1];
        for (int i = 0; i < count * 2; i += 2) {
            float x = points[i] - cx;
            float y = points[i + 1] - cy;
            array[0][0] += x * x;
            array[0][1] += x * y;
            array[1][1] += y * y;
        }
        array[0][0] /= count;
        array[0][1] /= count;
        array[1][0] = array[0][1];
        array[1][1] /= count;
        return array;
    }

    public static float computeTotalLength(float[] points) {
        float sum = 0.0f;
        int count = points.length - 4;
        for (int i = 0; i <= count; i += 2) {
            float dx = points[i + 2] - points[i];
            float dy = points[i + 3] - points[i + 1];
            sum += (float) Math.hypot(dx, dy);
        }
        return sum;
    }

    public static float computeStraightness(float[] points) {
        float totalLen = computeTotalLength(points);
        return computeStraightness(points, totalLen);
    }

    public static float computeStraightness(float[] points, float totalLen) {
        if (totalLen <= 0.0f || points.length < 4) {
            return 0.0f;
        }
        float dx = points[points.length - 2] - points[0];
        float dy = points[points.length - 1] - points[1];
        return (float) Math.hypot(dx, dy) / totalLen;
    }

    public static float squaredEuclideanDistance(float[] vector1, float[] vector2) {
        float squaredDistance = 0.0f;
        int size = Math.min(vector1.length, vector2.length);
        if (size == 0) {
            return 0.0f;
        }
        for (int i = 0; i < size; i++) {
            float difference = vector1[i] - vector2[i];
            squaredDistance += difference * difference;
        }
        return squaredDistance / size;
    }

    public static float cosineDistance(float[] vector1, float[] vector2) {
        int len = Math.min(vector1.length, vector2.length);
        double dot = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        for (int i = 0; i < len; i++) {
            dot += (double) vector1[i] * vector2[i];
            norm1 += (double) vector1[i] * vector1[i];
            norm2 += (double) vector2[i] * vector2[i];
        }
        if (norm1 <= 0.0 || norm2 <= 0.0) {
            return (float) (Math.PI / 2.0);
        }
        double cos = dot / Math.sqrt(norm1 * norm2);
        if (cos > 1.0) cos = 1.0;
        if (cos < -1.0) cos = -1.0;
        return (float) Math.acos(cos);
    }

    public static float minimumCosineDistance(float[] vector1, float[] vector2, int numOrientations) {
        final int len = Math.min(vector1.length, vector2.length);
        double a = 0.0;
        double b = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        for (int i = 0; i + 1 < len; i += 2) {
            double x1 = vector1[i];
            double y1 = vector1[i + 1];
            double x2 = vector2[i];
            double y2 = vector2[i + 1];
            a += x1 * x2 + y1 * y2;
            b += x1 * y2 - y1 * x2;
            norm1 += x1 * x1 + y1 * y1;
            norm2 += x2 * x2 + y2 * y2;
        }
        double denom = Math.sqrt(norm1 * norm2);
        if (denom <= 0.0) {
            return (float) (Math.PI / 2.0);
        }
        a /= denom;
        b /= denom;
        if (a != 0.0) {
            final double tan = b / a;
            final double angle = Math.atan(tan);
            if (numOrientations > 2 && Math.abs(angle) >= Math.PI / numOrientations) {
                double cos = a;
                if (cos > 1.0) cos = 1.0;
                if (cos < -1.0) cos = -1.0;
                return (float) Math.acos(cos);
            } else {
                final double cosine = Math.cos(angle);
                final double sine = cosine * tan;
                double val = a * cosine + b * sine;
                if (val > 1.0) val = 1.0;
                if (val < -1.0) val = -1.0;
                return (float) Math.acos(val);
            }
        } else {
            return (float) (Math.PI / 2.0);
        }
    }

    public static OrientedBoundingBox computeOrientedBoundingBox(
            ArrayList<GesturePoint> originalPoints) {
        final int count = originalPoints.size();
        float[] points = new float[count * 2];
        for (int i = 0; i < count; i++) {
            GesturePoint point = originalPoints.get(i);
            int index = i * 2;
            points[index] = point.x;
            points[index + 1] = point.y;
        }
        float[] meanVector = computeCentroid(points);
        return computeOrientedBoundingBox(points, meanVector);
    }

    public static OrientedBoundingBox computeOrientedBoundingBox(float[] originalPoints) {
        int size = originalPoints.length;
        float[] points = new float[size];
        System.arraycopy(originalPoints, 0, points, 0, size);
        float[] meanVector = computeCentroid(points);
        return computeOrientedBoundingBox(points, meanVector);
    }

    private static OrientedBoundingBox computeOrientedBoundingBox(float[] points, float[] centroid) {
        if (points.length < 2) {
            return new OrientedBoundingBox(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        }
        translate(points, -centroid[0], -centroid[1]);

        float[][] array = computeCoVariance(points);
        float[] targetVector = computeOrientation(array);

        float angle;
        if (targetVector[0] == 0.0f && targetVector[1] == 0.0f) {
            angle = -90.0f;
        } else {
            angle = (float) Math.atan2(targetVector[1], targetVector[0]);
            rotate(points, -angle);
        }

        float minx = Float.MAX_VALUE;
        float miny = Float.MAX_VALUE;
        float maxx = -Float.MAX_VALUE;
        float maxy = -Float.MAX_VALUE;
        int count = points.length;
        for (int i = 0; i < count; i += 2) {
            if (points[i] < minx) {
                minx = points[i];
            }
            if (points[i] > maxx) {
                maxx = points[i];
            }
            if (points[i + 1] < miny) {
                miny = points[i + 1];
            }
            if (points[i + 1] > maxy) {
                maxy = points[i + 1];
            }
        }

        float degAngle;
        if (targetVector[0] == 0.0f && targetVector[1] == 0.0f) {
            degAngle = -90.0f;
            return new OrientedBoundingBox(degAngle,
                    centroid[0], centroid[1], maxx - minx, maxy - miny);
        } else {
            degAngle = (float) (angle * 180.0 / Math.PI);
            float localCx = (minx + maxx) * 0.5f;
            float localCy = (miny + maxy) * 0.5f;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float worldCx = localCx * cos - localCy * sin + centroid[0];
            float worldCy = localCx * sin + localCy * cos + centroid[1];
            return new OrientedBoundingBox(degAngle,
                    worldCx, worldCy, maxx - minx, maxy - miny);
        }
    }

    private static float[] computeOrientation(float[][] covarianceMatrix) {
        float[] targetVector = new float[2];
        float a = covarianceMatrix[0][0];
        float b = covarianceMatrix[0][1];
        float d = covarianceMatrix[1][1];
        if (b == 0.0f) {
            if (a > d) {
                targetVector[0] = 1.0f;
                targetVector[1] = 0.0f;
            } else if (d > a) {
                targetVector[0] = 0.0f;
                targetVector[1] = 1.0f;
            } else {
                targetVector[0] = 0.0f;
                targetVector[1] = 0.0f;
            }
        } else {
            double trace = (double) a + d;
            double det = (double) a * d - (double) b * b;
            double disc = Math.max(0.0, trace * trace * 0.25 - det);
            double eigenvalue = trace * 0.5 + Math.sqrt(disc);
            targetVector[0] = 1.0f;
            targetVector[1] = (float) ((eigenvalue - a) / b);
        }
        return targetVector;
    }

    public static float[] translate(float[] points, float dx, float dy) {
        int size = points.length;
        for (int i = 0; i < size; i += 2) {
            points[i] += dx;
            points[i + 1] += dy;
        }
        return points;
    }

    public static float[] rotate(float[] points, float angle) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        int size = points.length;
        for (int i = 0; i < size; i += 2) {
            float x = points[i] * cos - points[i + 1] * sin;
            float y = points[i] * sin + points[i + 1] * cos;
            points[i] = x;
            points[i + 1] = y;
        }
        return points;
    }

    public static float[] scale(float[] points, float sx, float sy) {
        int size = points.length;
        for (int i = 0; i < size; i += 2) {
            points[i] *= sx;
            points[i + 1] *= sy;
        }
        return points;
    }
}
