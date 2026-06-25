// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/profile/LockableBlockValidationStatus.java
package org.z2six.locksmith.render.profile;

public enum LockableBlockValidationStatus {
    OK,
    INVALID_ID,
    UNKNOWN_BLOCK,
    DUPLICATE_BLOCK,
    UNSUPPORTED_TYPE,
    TYPE_MISMATCH,
    MISSING_PROPERTIES,
    DISABLED
}
