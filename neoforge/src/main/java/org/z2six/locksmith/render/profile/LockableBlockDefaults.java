// MainFile: neoforge/src/main/java/org/z2six/locksmith/render/profile/LockableBlockDefaults.java
package org.z2six.locksmith.render.profile;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class LockableBlockDefaults {
    private LockableBlockDefaults() {
    }

    public static List<LockableBlockEntry> create() {
        ArrayList<LockableBlockEntry> out = new ArrayList<>();
        LockTransform door = LockTransform.doorDefault();
        String[] doors = {
                "minecraft:oak_door", "minecraft:spruce_door", "minecraft:birch_door",
                "minecraft:jungle_door", "minecraft:acacia_door", "minecraft:dark_oak_door",
                "minecraft:mangrove_door", "minecraft:cherry_door", "minecraft:bamboo_door",
                "minecraft:crimson_door", "minecraft:warped_door"
        };
        for (String id : doors) {
            out.add(new LockableBlockEntry(requireId(id), LockTargetType.DOOR, door, true, true));
        }

        LockTransform chest = LockTransform.chestDefault();
        out.add(new LockableBlockEntry(requireId("minecraft:chest"), LockTargetType.CHEST, chest, true, true));
        out.add(new LockableBlockEntry(requireId("minecraft:trapped_chest"), LockTargetType.CHEST, chest, true, true));

        LockTransform stoneButton = new LockTransform(
                -0.015D,
                0.05D,
                0.375D,
                0.0F,
                180.0F,
                0.0F,
                0.75F,
                0.0D,
                0.0D,
                0.0D
        );
        out.add(new LockableBlockEntry(requireId("minecraft:stone_button"), LockTargetType.GENERIC, stoneButton, true, true));
        return out;
    }

    private static ResourceLocation requireId(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid default block id: " + id);
        }
        return parsed;
    }
}
