// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/profile/LockTargetType.java
package org.z2six.locksmith.render.profile;

/**
 * What kind of block the lock is attached to.
 * Currently only DOOR is actually used in DoorLockRenderer, but the type is
 * serialized to forwards-compat future targets (chests, etc.).
 */
public enum LockTargetType {
    DOOR((byte) 0),
    CHEST((byte) 1),
    GENERIC((byte) 2);

    public final byte id;

    LockTargetType(byte id) {
        this.id = id;
    }

    public static LockTargetType fromId(byte id) {
        for (LockTargetType t : values()) {
            if (t.id == id) return t;
        }
        return DOOR;
    }

    public static LockTargetType fromString(String s) {
        if (s == null) return DOOR;
        String lower = s.trim().toLowerCase();
        return switch (lower) {
            case "door" -> DOOR;
            case "chest" -> CHEST;
            default -> GENERIC;
        };
    }
}
