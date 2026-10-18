package com.arcaneterminal.compat.jei;

import com.arcaneterminal.ArcaneTerminal;
import com.arcaneterminal.registry.ATRegistry;
import com.leclowndu93150.thaumaturge.compat.jei.category.ArcaneWorkbenchCategory;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Shows the terminal as a station for arcane and crafting recipes, and makes JEI's "+" button fill
 * the grid and crystal slots from the storage network and the player's inventory.
 */
@JeiPlugin
public final class ArcaneTerminalJeiPlugin implements IModPlugin {
    private static final Identifier PLUGIN_UID = ArcaneTerminal.id("jei_plugin");

    @Override
    public @NonNull Identifier getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(ArcaneWorkbenchCategory.RECIPE_TYPE, ATRegistry.ARCANE_CRAFTING_TERMINAL_ITEM.get());
        registration.addCraftingStation(RecipeTypes.CRAFTING, ATRegistry.ARCANE_CRAFTING_TERMINAL_ITEM.get());
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        IRecipeTransferHandlerHelper helper = registration.getTransferHelper();
        registration.addRecipeTransferHandler(new TerminalRecipeTransferHandler<>(ArcaneWorkbenchCategory.RECIPE_TYPE, helper), ArcaneWorkbenchCategory.RECIPE_TYPE);
        registration.addRecipeTransferHandler(new TerminalRecipeTransferHandler<>(RecipeTypes.CRAFTING, helper), RecipeTypes.CRAFTING);
    }
}
