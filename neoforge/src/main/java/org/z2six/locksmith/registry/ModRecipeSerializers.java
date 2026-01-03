// neoforge/src/main/java/org/z2six/locksmith/registry/ModRecipeSerializers.java
package org.z2six.locksmith.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.z2six.locksmith.Constants;
import org.z2six.locksmith.recipe.IronKeyMintRecipe;

public final class ModRecipeSerializers {

    private static final Logger LOG = Constants.LOG;

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Constants.MOD_ID);

    /**
     * Custom crafting recipe:
     * - Requires 1 registered iron key + 1 unregistered iron key placed horizontally (registered on the left).
     * - Consumes only the unregistered key.
     * - Leaves the registered key in the grid (marked as Master).
     * - Outputs a copied registered key (marked as Copied).
     */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<?>> IRON_KEY_MINT =
            RECIPE_SERIALIZERS.register("iron_key_mint", () -> {
                try {
                    LOG.debug("[Locksmith][ModRecipeSerializers] Creating SimpleCraftingRecipeSerializer for IronKeyMintRecipe.");
                } catch (Throwable ignored) {
                }
                return new SimpleCraftingRecipeSerializer<>(IronKeyMintRecipe::new);
            });

    private ModRecipeSerializers() {
    }
}
