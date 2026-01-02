// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/profile/LockTargetType.java
package org.z2six.locksmith.render.profile;

import org.slf4j.Logger;
import org.z2six.locksmith.Constants;

/**
 * What kind of lockable target a profile applies to.
 * We start with DOOR now, later we'll add CHEST logic without changing the wire format.
 */
public enum LockTargetType {
    DOOR((byte) 1),
    CHEST((byte) 2);

    private static final Logger LOG = Constants.LOG;

    public final byte id;

    LockTargetType(byte id) {
        this.id = id;
    }

    public static LockTargetType fromId(byte id) {
        for (LockTargetType t : values()) {
            if (t.id == id) return t;
        }
        LOG.warn("[Locksmith][LockTargetType] Unknown type id={} - defaulting to DOOR", id);
        return DOOR;
    }

    public static LockTargetType fromString(String s) {
        if (s == null) return DOOR;
        String v = s.trim().toLowerCase();
        return switch (v) {
            case "door" -> DOOR;
            case "chest" -> CHEST;
            default -> {
                LOG.warn("[Locksmith][LockTargetType] Unknown type string='{}' - defaulting to DOOR", s);
                yield DOOR;
            }
        };
    }

    public String toConfigString() {
        return switch (this) {
            case DOOR -> "door";
            case CHEST -> "chest";
        };
    }
}
