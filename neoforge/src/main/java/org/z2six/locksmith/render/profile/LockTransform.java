// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/profile/LockTransform.java
package org.z2six.locksmith.render.profile;

public record LockTransform(
        double offsetX,
        double offsetY,
        double offsetZ,
        float rotX,
        float rotY,
        float rotZ,
        float scale,
        double hingeNudgeLeft,
        double hingeNudgeRight,
        double doubleNudgeX
) {
    public static LockTransform doorDefault() {
        return new LockTransform(-0.05D, 0.5D, -0.5D, 0.0F, 0.0F, 0.0F, 0.75F, 0.18D, 0.325D, 0.0D);
    }

    public static LockTransform chestDefault() {
        return new LockTransform(-0.025D, 0.05D, 0.45D, 0.0F, 180.0F, 0.0F, 0.75F, 0.0D, 0.0D, -0.5D);
    }

    public static LockTransform genericDefault() {
        return new LockTransform(0.0D, 0.5D, 0.55D, 0.0F, 180.0F, 0.0F, 0.75F, 0.0D, 0.0D, 0.0D);
    }

    public LockTransform clamped() {
        return new LockTransform(
                clamp(offsetX, -4.0D, 4.0D),
                clamp(offsetY, -4.0D, 4.0D),
                clamp(offsetZ, -4.0D, 4.0D),
                (float) clamp(rotX, -360.0D, 360.0D),
                (float) clamp(rotY, -360.0D, 360.0D),
                (float) clamp(rotZ, -360.0D, 360.0D),
                (float) clamp(scale, 0.05D, 8.0D),
                clamp(hingeNudgeLeft, -4.0D, 4.0D),
                clamp(hingeNudgeRight, -4.0D, 4.0D),
                clamp(doubleNudgeX, -4.0D, 4.0D)
        );
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return min;
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
