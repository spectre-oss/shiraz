// SPDX-License-Identifier: GPL-3.0-only
package android.view.animation;
public abstract class BaseInterpolator implements Interpolator { private int configurations; public int getChangingConfiguration(){return configurations;} public void setChangingConfiguration(int value){configurations=value;} }
