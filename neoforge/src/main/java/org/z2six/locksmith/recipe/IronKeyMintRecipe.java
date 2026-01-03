// MainFile: neoforge/src/main/java/org/z2six/locksmith/recipe/IronKeyMintRecipe.java
package org.z2six.locksmith.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.item.IronKeyItem;
import org.z2six.locksmith.registry.ModItems;
import org.z2six.locksmith.registry.ModRecipeSerializers;
import org.z2six.locksmith.util.ItemStackDataUtil;

/**
 * Mint/copy recipe:
 * Input:  1x registered iron_key + 1x unregistered iron_key, placed horizontally adjacent.
 *         Registered must be LEFT of unregistered.
 *
 * Output: 1x copied registered iron_key (non-stackable).
 *
 * Consumption:
 * - Unregistered key is consumed.
 * - Registered key is NOT consumed; it remains in the grid.
 *
 * Role rules:
 * - Output is always ROLE_COPIED.
 * - The source key is returned unchanged EXCEPT:
 *   - If the source key is NOT ROLE_COPIED, we mark it ROLE_MASTER for tooltip clarity.
 *   - If the source key IS ROLE_COPIED, we DO NOT promote it to master.
 *
 * This uses getRemainingItems to return the source key back into the grid (bucket-like behavior).
 */
public class IronKeyMintRecipe extends CustomRecipe {

    private static final Logger LOG = Constants.LOG;

    public IronKeyMintRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        try {
            if (input == null || input.isEmpty()) return false;

            int w = input.width();
            int h = input.height();
            if (w < 2 || h < 1) return false;

            // Must be exactly 2 ingredients (2 non-empty stacks total)
            if (input.ingredientCount() != 2) return false;

            // Find the registered key and unregistered key positions
            Pos registeredPos = null;
            Pos blankPos = null;

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    ItemStack s = safeGet(input, x, y);
                    if (s.isEmpty()) continue;

                    if (!s.is(ModItems.KEY_IRON.get())) {
                        // Only our keys are allowed in the grid for this recipe.
                        return false;
                    }

                    boolean reg = IronKeyItem.isRegistered(s);
                    if (reg) {
                        if (registeredPos != null) return false; // more than one registered key
                        registeredPos = new Pos(x, y);
                    } else {
                        if (blankPos != null) return false; // more than one blank key
                        blankPos = new Pos(x, y);
                    }
                }
            }

            if (registeredPos == null || blankPos == null) return false;

            // Must be horizontal adjacent, registered on the left of blank.
            if (registeredPos.y != blankPos.y) return false;
            if (blankPos.x != registeredPos.x + 1) return false;

            // Additional sanity: registered key must have a non-blank hash
            ItemStack regStack = safeGet(input, registeredPos.x, registeredPos.y);
            String hash = IronKeyItem.getHashOrEmpty(regStack);
            if (hash == null || hash.isBlank()) return false;

            return true;
        } catch (Throwable t) {
            LOG.warn("[Locksmith][IronKeyMintRecipe] matches failed (non-fatal).", t);
            return false;
        }
    }

    /**
     * NeoForge/MC 1.21.x signature expects HolderLookup.Provider (not RegistryAccess).
     */
    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        try {
            if (input == null || input.isEmpty()) return ItemStack.EMPTY;

            Pos registeredPos = findRegisteredPos(input);
            if (registeredPos == null) return ItemStack.EMPTY;

            ItemStack regStack = safeGet(input, registeredPos.x, registeredPos.y);
            if (regStack.isEmpty() || !regStack.is(ModItems.KEY_IRON.get())) return ItemStack.EMPTY;
            if (!IronKeyItem.isRegistered(regStack)) return ItemStack.EMPTY;

            String hash = IronKeyItem.getHashOrEmpty(regStack);
            String by = IronKeyItem.getRegisteredByOrEmpty(regStack);

            if (hash == null || hash.isBlank()) return ItemStack.EMPTY;
            if (by == null) by = "";

            ItemStack out = new ItemStack(ModItems.KEY_IRON.get(), 1);

            boolean wroteHash = ItemStackDataUtil.putString(out, IronKeyItem.DATA_KEY_HASH, hash);
            boolean wroteBy = ItemStackDataUtil.putString(out, IronKeyItem.DATA_REGISTERED_BY, by);
            boolean wroteRole = ItemStackDataUtil.putString(out, IronKeyItem.DATA_KEY_ROLE, IronKeyItem.ROLE_COPIED);

            if (!wroteHash || !wroteBy || !wroteRole) {
                LOG.warn("[Locksmith][IronKeyMintRecipe] assemble: failed writing data (non-fatal). wroteHash={}, wroteBy={}, wroteRole={}",
                        wroteHash, wroteBy, wroteRole);
                return ItemStack.EMPTY;
            }

            if (LOG.isDebugEnabled()) {
                String srcRole = IronKeyItem.getRoleOrEmpty(regStack);
                LOG.debug("[Locksmith][IronKeyMintRecipe] assemble: produced copied key (hashLen={}, by='{}', sourceRole='{}').",
                        hash.length(), by, (srcRole == null ? "" : srcRole));
            }

            return out;
        } catch (Throwable t) {
            LOG.error("[Locksmith][IronKeyMintRecipe] assemble failed (non-fatal).", t);
            return ItemStack.EMPTY;
        }
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);

        try {
            if (input == null || input.isEmpty()) return remaining;

            int w = input.width();
            int h = input.height();

            Pos registeredPos = null;
            Pos blankPos = null;

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    ItemStack s = safeGet(input, x, y);
                    if (s.isEmpty()) continue;

                    if (!s.is(ModItems.KEY_IRON.get())) {
                        // Shouldn't happen if matches() is respected, but stay defensive.
                        continue;
                    }

                    if (IronKeyItem.isRegistered(s)) {
                        registeredPos = new Pos(x, y);
                    } else {
                        blankPos = new Pos(x, y);
                    }
                }
            }

            // Return the registered key back, consume blank key.
            if (registeredPos != null) {
                ItemStack reg = safeGet(input, registeredPos.x, registeredPos.y);
                ItemStack returned = reg.copy();
                returned.setCount(1);

                // IMPORTANT FIX:
                // Do NOT promote copied keys to master.
                // Only mark as master if the role is blank/unknown or already master.
                String role = null;
                try {
                    role = IronKeyItem.getRoleOrEmpty(returned);
                } catch (Throwable t) {
                    role = "";
                    LOG.warn("[Locksmith][IronKeyMintRecipe] getRemainingItems: failed reading role (non-fatal).", t);
                }
                if (role == null) role = "";

                boolean isCopied = IronKeyItem.ROLE_COPIED.equalsIgnoreCase(role);
                boolean isMaster = IronKeyItem.ROLE_MASTER.equalsIgnoreCase(role);

                if (!isCopied) {
                    // If it’s already master, keep it. If blank/unknown, upgrade to master for clarity.
                    if (!isMaster) {
                        boolean wroteRole = ItemStackDataUtil.putString(returned, IronKeyItem.DATA_KEY_ROLE, IronKeyItem.ROLE_MASTER);
                        if (!wroteRole) {
                            LOG.warn("[Locksmith][IronKeyMintRecipe] getRemainingItems: failed to mark master role (non-fatal).");
                        } else if (LOG.isDebugEnabled()) {
                            LOG.debug("[Locksmith][IronKeyMintRecipe] getRemainingItems: marked source key as MASTER (prevRole='{}').", role);
                        }
                    } else if (LOG.isDebugEnabled()) {
                        LOG.debug("[Locksmith][IronKeyMintRecipe] getRemainingItems: source key already MASTER; leaving unchanged.");
                    }
                } else {
                    // Copied key stays copied.
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("[Locksmith][IronKeyMintRecipe] getRemainingItems: source key is COPIED; not promoting to MASTER.");
                    }
                }

                int idx = registeredPos.y * w + registeredPos.x;
                if (idx >= 0 && idx < remaining.size()) {
                    remaining.set(idx, returned);
                }
            }

            if (blankPos != null) {
                // blank gets consumed: leave EMPTY in its slot
                int idx = blankPos.y * w + blankPos.x;
                if (idx >= 0 && idx < remaining.size()) {
                    remaining.set(idx, ItemStack.EMPTY);
                }
            }

        } catch (Throwable t) {
            LOG.error("[Locksmith][IronKeyMintRecipe] getRemainingItems failed (non-fatal).", t);
        }

        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 2 && height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.IRON_KEY_MINT.get();
    }

    private static ItemStack safeGet(CraftingInput input, int x, int y) {
        try {
            ItemStack s = input.getItem(x, y);
            return s == null ? ItemStack.EMPTY : s;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    private static Pos findRegisteredPos(CraftingInput input) {
        try {
            int w = input.width();
            int h = input.height();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    ItemStack s = safeGet(input, x, y);
                    if (s.isEmpty()) continue;
                    if (!s.is(ModItems.KEY_IRON.get())) continue;
                    if (IronKeyItem.isRegistered(s)) return new Pos(x, y);
                }
            }
            return null;
        } catch (Throwable t) {
            LOG.warn("[Locksmith][IronKeyMintRecipe] findRegisteredPos failed (non-fatal).", t);
            return null;
        }
    }

    private record Pos(int x, int y) {
    }
}
