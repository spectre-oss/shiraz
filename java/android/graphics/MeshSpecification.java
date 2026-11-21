// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

/** Vertex layout, varyings, color space, and AGSL shader specification for a custom Mesh. */
public class MeshSpecification {
    public static final int TYPE_FLOAT = 0;
    public static final int TYPE_FLOAT2 = 1;
    public static final int TYPE_FLOAT3 = 2;
    public static final int TYPE_FLOAT4 = 3;
    public static final int TYPE_UBYTE4 = 4;

    public static final int ALPHA_TYPE_UNKNOWN = 0;
    public static final int ALPHA_TYPE_OPAQUE = 1;
    public static final int ALPHA_TYPE_PREMULTIPLIED = 2;
    public static final int ALPHA_TYPE_UNPREMULTIPLIED = 3;

    public static class Attribute {
        private final int type;
        private final int offset;
        private final String name;

        public Attribute(int type, int offset, String name) {
            if (type < TYPE_FLOAT || type > TYPE_UBYTE4) {
                throw new IllegalArgumentException("Invalid Attribute type: " + type);
            }
            if (offset < 0 || (offset & 3) != 0) {
                throw new IllegalArgumentException("Attribute offset must be non-negative and 4-byte aligned");
            }
            if (name == null) {
                throw new NullPointerException("name");
            }
            this.type = type;
            this.offset = offset;
            this.name = name;
        }

        public int getType() {
            return type;
        }

        public int getOffset() {
            return offset;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "Attribute{type=" + type + ", offset=" + offset + ", name=" + name + "}";
        }
    }

    public static class Varying {
        private final int type;
        private final String name;

        public Varying(int type, String name) {
            if (type < TYPE_FLOAT || type > TYPE_FLOAT4) {
                throw new IllegalArgumentException("Invalid Varying type: " + type);
            }
            if (name == null) {
                throw new NullPointerException("name");
            }
            this.type = type;
            this.name = name;
        }

        public int getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "Varying{type=" + type + ", name=" + name + "}";
        }
    }

    final Attribute[] attributes;
    final int vertexStride;
    final Varying[] varyings;
    final String vertexShader;
    final String fragmentShader;
    final ColorSpace colorSpace;
    final int alphaType;

    private MeshSpecification(
            Attribute[] attributes,
            int vertexStride,
            Varying[] varyings,
            String vertexShader,
            String fragmentShader,
            ColorSpace colorSpace,
            int alphaType) {
        this.attributes = attributes.clone();
        this.vertexStride = vertexStride;
        this.varyings = varyings.clone();
        this.vertexShader = vertexShader;
        this.fragmentShader = fragmentShader;
        this.colorSpace = colorSpace;
        this.alphaType = alphaType;
    }

    public static MeshSpecification make(
            Attribute[] attributes,
            int vertexStride,
            Varying[] varyings,
            String vertexShader,
            String fragmentShader) {
        return make(
                attributes,
                vertexStride,
                varyings,
                vertexShader,
                fragmentShader,
                ColorSpace.get(ColorSpace.Named.SRGB),
                ALPHA_TYPE_PREMULTIPLIED);
    }

    public static MeshSpecification make(
            Attribute[] attributes,
            int vertexStride,
            Varying[] varyings,
            String vertexShader,
            String fragmentShader,
            ColorSpace colorSpace) {
        return make(
                attributes,
                vertexStride,
                varyings,
                vertexShader,
                fragmentShader,
                colorSpace,
                ALPHA_TYPE_PREMULTIPLIED);
    }

    public static MeshSpecification make(
            Attribute[] attributes,
            int vertexStride,
            Varying[] varyings,
            String vertexShader,
            String fragmentShader,
            ColorSpace colorSpace,
            int alphaType) {
        if (attributes == null || varyings == null || vertexShader == null
                || fragmentShader == null || colorSpace == null) {
            throw new NullPointerException("MeshSpecification arguments cannot be null");
        }
        if (attributes.length == 0 || attributes.length > 8) {
            throw new IllegalArgumentException("Attribute count must be in 1..8");
        }
        if (varyings.length > 6) {
            throw new IllegalArgumentException("Varying count must be in 0..6");
        }
        if (vertexStride <= 0 || vertexStride > 1024 || (vertexStride & 3) != 0) {
            throw new IllegalArgumentException("Invalid vertexStride: " + vertexStride);
        }
        if (alphaType < ALPHA_TYPE_UNKNOWN || alphaType > ALPHA_TYPE_UNPREMULTIPLIED) {
            throw new IllegalArgumentException("Invalid alphaType: " + alphaType);
        }
        if (vertexShader.trim().isEmpty() || fragmentShader.trim().isEmpty()) {
            throw new IllegalArgumentException("Vertex and fragment shaders must not be empty");
        }
        return new MeshSpecification(
                attributes, vertexStride, varyings, vertexShader, fragmentShader, colorSpace, alphaType);
    }
}
