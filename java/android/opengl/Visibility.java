// SPDX-License-Identifier: GPL-3.0-only
package android.opengl;

/**
 * Pure-Java 3D frustum culling, bounding sphere calculation, and triangle clip visibility tests.
 */
public class Visibility {
    public Visibility() {}

    /**
     * Tests whether an indexed triangle mesh intersects the view frustum defined by {@code ws} (4x4 MVP matrix).
     *
     * @return 2 if completely inside frustum, 1 if partially intersecting, 0 if completely outside.
     */
    public static int visibilityTest(
            float[] ws, int wsOffset,
            float[] positions, int positionsOffset,
            char[] indices, int indicesOffset, int indexCount) {
        if (ws == null || positions == null || indices == null) {
            throw new IllegalArgumentException("Null array argument");
        }
        if (wsOffset < 0 || wsOffset + 16 > ws.length) {
            throw new IllegalArgumentException("wsOffset out of bounds");
        }
        if (indicesOffset < 0 || indexCount < 0 || indicesOffset + indexCount > indices.length) {
            throw new IllegalArgumentException("indices range out of bounds");
        }
        if (indexCount < 3) {
            return 0;
        }
        boolean anyVisible = false;
        boolean allInside = true;
        float[] v = new float[4];
        float[] clip = new float[4];

        for (int t = 0; t + 2 < indexCount; t += 3) {
            int andCodes = 0x3f;
            int orCodes = 0;
            for (int k = 0; k < 3; k++) {
                int vertexIndex = indices[indicesOffset + t + k];
                int base = positionsOffset + vertexIndex * 3;
                if (base < 0 || base + 3 > positions.length) {
                    throw new IllegalArgumentException("positions index out of bounds");
                }
                v[0] = positions[base];
                v[1] = positions[base + 1];
                v[2] = positions[base + 2];
                v[3] = 1.0f;
                Matrix.multiplyMV(clip, 0, ws, wsOffset, v, 0);
                float cx = clip[0];
                float cy = clip[1];
                float cz = clip[2];
                float cw = clip[3];
                int code = 0;
                if (cx < -cw) code |= 1;
                if (cx >  cw) code |= 2;
                if (cy < -cw) code |= 4;
                if (cy >  cw) code |= 8;
                if (cz < -cw) code |= 16;
                if (cz >  cw) code |= 32;
                andCodes &= code;
                orCodes |= code;
            }
            if (andCodes == 0) {
                anyVisible = true;
                if (orCodes != 0) {
                    allInside = false;
                }
            } else {
                allInside = false;
            }
        }
        if (!anyVisible) {
            return 0;
        }
        return allInside ? 2 : 1;
    }

    /**
     * Culls bounding spheres {@code (x, y, z, r)} against the 6 frustum planes extracted from {@code mvp}.
     *
     * @return number of visible sphere indices written into {@code results}.
     */
    public static int frustumCullSpheres(
            float[] mvp, int mvpOffset,
            float[] spheres, int spheresOffset, int spheresCount,
            int[] results, int resultsOffset, int resultsCapacity) {
        if (mvp == null || spheres == null || results == null) {
            throw new IllegalArgumentException("Null array argument");
        }
        if (mvpOffset < 0 || mvpOffset + 16 > mvp.length) {
            throw new IllegalArgumentException("mvpOffset out of bounds");
        }
        if (spheresOffset < 0 || spheresCount < 0 || spheresOffset + spheresCount * 4 > spheres.length) {
            throw new IllegalArgumentException("spheres out of bounds");
        }
        if (resultsOffset < 0 || resultsCapacity < spheresCount || resultsOffset + resultsCapacity > results.length) {
            throw new IllegalArgumentException("results out of bounds");
        }

        // Extract 6 normalized Gribb-Hartmann frustum planes (a, b, c, d) from column-major MVP.
        float[][] planes = new float[6][4];
        // Left: row3 + row0
        setNormalizedPlane(planes[0],
                mvp[mvpOffset + 3]  + mvp[mvpOffset + 0],
                mvp[mvpOffset + 7]  + mvp[mvpOffset + 4],
                mvp[mvpOffset + 11] + mvp[mvpOffset + 8],
                mvp[mvpOffset + 15] + mvp[mvpOffset + 12]);
        // Right: row3 - row0
        setNormalizedPlane(planes[1],
                mvp[mvpOffset + 3]  - mvp[mvpOffset + 0],
                mvp[mvpOffset + 7]  - mvp[mvpOffset + 4],
                mvp[mvpOffset + 11] - mvp[mvpOffset + 8],
                mvp[mvpOffset + 15] - mvp[mvpOffset + 12]);
        // Bottom: row3 + row1
        setNormalizedPlane(planes[2],
                mvp[mvpOffset + 3]  + mvp[mvpOffset + 1],
                mvp[mvpOffset + 7]  + mvp[mvpOffset + 5],
                mvp[mvpOffset + 11] + mvp[mvpOffset + 9],
                mvp[mvpOffset + 15] + mvp[mvpOffset + 13]);
        // Top: row3 - row1
        setNormalizedPlane(planes[3],
                mvp[mvpOffset + 3]  - mvp[mvpOffset + 1],
                mvp[mvpOffset + 7]  - mvp[mvpOffset + 5],
                mvp[mvpOffset + 11] - mvp[mvpOffset + 9],
                mvp[mvpOffset + 15] - mvp[mvpOffset + 13]);
        // Near: row3 + row2
        setNormalizedPlane(planes[4],
                mvp[mvpOffset + 3]  + mvp[mvpOffset + 2],
                mvp[mvpOffset + 7]  + mvp[mvpOffset + 6],
                mvp[mvpOffset + 11] + mvp[mvpOffset + 10],
                mvp[mvpOffset + 15] + mvp[mvpOffset + 14]);
        // Far: row3 - row2
        setNormalizedPlane(planes[5],
                mvp[mvpOffset + 3]  - mvp[mvpOffset + 2],
                mvp[mvpOffset + 7]  - mvp[mvpOffset + 6],
                mvp[mvpOffset + 11] - mvp[mvpOffset + 10],
                mvp[mvpOffset + 15] - mvp[mvpOffset + 14]);

        int visibleCount = 0;
        for (int i = 0; i < spheresCount; i++) {
            int base = spheresOffset + i * 4;
            float x = spheres[base];
            float y = spheres[base + 1];
            float z = spheres[base + 2];
            float r = spheres[base + 3];
            boolean inside = true;
            for (int p = 0; p < 6; p++) {
                float[] pl = planes[p];
                float dist = pl[0] * x + pl[1] * y + pl[2] * z + pl[3];
                if (dist < -r) {
                    inside = false;
                    break;
                }
            }
            if (inside) {
                results[resultsOffset + visibleCount] = i;
                visibleCount++;
            }
        }
        return visibleCount;
    }

    private static void setNormalizedPlane(float[] out, float a, float b, float c, float d) {
        float len = (float) Math.sqrt(a * a + b * b + c * c);
        if (len > 0.0f) {
            float inv = 1.0f / len;
            out[0] = a * inv;
            out[1] = b * inv;
            out[2] = c * inv;
            out[3] = d * inv;
        } else {
            out[0] = a;
            out[1] = b;
            out[2] = c;
            out[3] = d;
        }
    }

    /**
     * Computes a bounding sphere {@code (centerX, centerY, centerZ, radius)} enclosing {@code positionsCount} 3D vertices.
     */
    public static void computeBoundingSphere(
            float[] positions, int positionsOffset, int positionsCount,
            float[] sphere, int sphereOffset) {
        if (positions == null || sphere == null) {
            throw new IllegalArgumentException("Null array argument");
        }
        if (positionsOffset < 0 || positionsCount < 0 || positionsOffset + positionsCount * 3 > positions.length) {
            throw new IllegalArgumentException("positions out of bounds");
        }
        if (sphereOffset < 0 || sphereOffset + 4 > sphere.length) {
            throw new IllegalArgumentException("sphereOffset out of bounds");
        }
        if (positionsCount == 0) {
            sphere[sphereOffset] = 0f;
            sphere[sphereOffset + 1] = 0f;
            sphere[sphereOffset + 2] = 0f;
            sphere[sphereOffset + 3] = 0f;
            return;
        }
        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < positionsCount; i++) {
            int base = positionsOffset + i * 3;
            float x = positions[base];
            float y = positions[base + 1];
            float z = positions[base + 2];
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }
        float cx = (minX + maxX) * 0.5f;
        float cy = (minY + maxY) * 0.5f;
        float cz = (minZ + maxZ) * 0.5f;
        float maxDistSq = 0f;
        for (int i = 0; i < positionsCount; i++) {
            int base = positionsOffset + i * 3;
            float dx = positions[base] - cx;
            float dy = positions[base + 1] - cy;
            float dz = positions[base + 2] - cz;
            float d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > maxDistSq) {
                maxDistSq = d2;
            }
        }
        sphere[sphereOffset] = cx;
        sphere[sphereOffset + 1] = cy;
        sphere[sphereOffset + 2] = cz;
        sphere[sphereOffset + 3] = (float) Math.sqrt(maxDistSq);
    }
}
