// SPDX-License-Identifier: GPL-3.0-only
package android.util;
/** Attribute access contract; compiled XML adapters are separate. */
public interface AttributeSet {
    int getAttributeCount();
    java.lang.String getAttributeName(int arg0);
    java.lang.String getAttributeValue(int arg0);
    java.lang.String getAttributeValue(java.lang.String arg0, java.lang.String arg1);
    java.lang.String getPositionDescription();
    int getAttributeNameResource(int arg0);
    int getAttributeListValue(java.lang.String arg0, java.lang.String arg1, java.lang.String[] arg2, int arg3);
    boolean getAttributeBooleanValue(java.lang.String arg0, java.lang.String arg1, boolean arg2);
    int getAttributeResourceValue(java.lang.String arg0, java.lang.String arg1, int arg2);
    int getAttributeIntValue(java.lang.String arg0, java.lang.String arg1, int arg2);
    int getAttributeUnsignedIntValue(java.lang.String arg0, java.lang.String arg1, int arg2);
    float getAttributeFloatValue(java.lang.String arg0, java.lang.String arg1, float arg2);
    int getAttributeListValue(int arg0, java.lang.String[] arg1, int arg2);
    boolean getAttributeBooleanValue(int arg0, boolean arg1);
    int getAttributeResourceValue(int arg0, int arg1);
    int getAttributeIntValue(int arg0, int arg1);
    int getAttributeUnsignedIntValue(int arg0, int arg1);
    float getAttributeFloatValue(int arg0, float arg1);
    java.lang.String getIdAttribute();
    java.lang.String getClassAttribute();
    int getIdAttributeResourceValue(int arg0);
    int getStyleAttribute();
}
