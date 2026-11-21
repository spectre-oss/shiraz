// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class PropertyPath implements Iterable<PropertyPath.PathSegment> {
    private final List<PathSegment> mPathList;

    public PropertyPath(List<PathSegment> pathList) {
        Objects.requireNonNull(pathList);
        mPathList = Collections.unmodifiableList(new ArrayList<PathSegment>(pathList));
    }

    public PropertyPath(String path) {
        Objects.requireNonNull(path);
        if (path.isEmpty()) {
            throw new IllegalArgumentException("PropertyPath string cannot be empty");
        }
        List<PathSegment> list = new ArrayList<PathSegment>();
        String[] rawSegments = path.split("\\.", -1);
        for (String seg : rawSegments) {
            if (seg.isEmpty()) {
                throw new IllegalArgumentException("Malformed property path: " + path);
            }
            int openBracket = seg.indexOf('[');
            if (openBracket < 0) {
                if (seg.indexOf(']') >= 0) {
                    throw new IllegalArgumentException("Malformed property path: " + path);
                }
                list.add(PathSegment.create(seg));
            } else {
                if (!seg.endsWith("]") || openBracket == 0) {
                    throw new IllegalArgumentException("Malformed property path: " + path);
                }
                String propName = seg.substring(0, openBracket);
                String idxStr = seg.substring(openBracket + 1, seg.length() - 1);
                try {
                    int idx = Integer.parseInt(idxStr);
                    list.add(PathSegment.create(propName, idx));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Malformed index in path: " + path, e);
                }
            }
        }
        mPathList = Collections.unmodifiableList(list);
    }

    public PathSegment get(int index) {
        return mPathList.get(index);
    }

    public int size() {
        return mPathList.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mPathList.size(); i++) {
            if (i > 0) sb.append('.');
            sb.append(mPathList.get(i).toString());
        }
        return sb.toString();
    }

    @Override
    public Iterator<PathSegment> iterator() {
        return mPathList.iterator();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PropertyPath)) return false;
        PropertyPath that = (PropertyPath) o;
        return Objects.equals(mPathList, that.mPathList);
    }

    @Override
    public int hashCode() {
        return mPathList.hashCode();
    }

    public static class PathSegment {
        public static final int NON_REPEATED_CARDINALITY = -1;

        private final String mPropertyName;
        private final int mPropertyIndex;

        private PathSegment(String propertyName, int propertyIndex) {
            Objects.requireNonNull(propertyName);
            if (propertyName.isEmpty()
                    || propertyName.contains(".")
                    || propertyName.contains("[")
                    || propertyName.contains("]")) {
                throw new IllegalArgumentException("Invalid propertyName: " + propertyName);
            }
            if (propertyIndex != NON_REPEATED_CARDINALITY && propertyIndex < 0) {
                throw new IllegalArgumentException("Invalid propertyIndex: " + propertyIndex);
            }
            mPropertyName = propertyName;
            mPropertyIndex = propertyIndex;
        }

        public static PathSegment create(String propertyName, int propertyIndex) {
            if (propertyIndex < 0) {
                throw new IllegalArgumentException("Invalid propertyIndex: " + propertyIndex);
            }
            return new PathSegment(propertyName, propertyIndex);
        }

        public static PathSegment create(String propertyName) {
            return new PathSegment(propertyName, NON_REPEATED_CARDINALITY);
        }

        public String getPropertyName() {
            return mPropertyName;
        }

        public int getPropertyIndex() {
            return mPropertyIndex;
        }

        @Override
        public String toString() {
            if (mPropertyIndex != NON_REPEATED_CARDINALITY) {
                return mPropertyName + "[" + mPropertyIndex + "]";
            }
            return mPropertyName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PathSegment)) return false;
            PathSegment that = (PathSegment) o;
            return mPropertyIndex == that.mPropertyIndex
                    && Objects.equals(mPropertyName, that.mPropertyName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mPropertyName, Integer.valueOf(mPropertyIndex));
        }
    }
}
