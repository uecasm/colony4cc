package nz.co.mirality.colony4cc.integration;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import nz.co.mirality.colony4cc.Colony4CC;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public class ColonyJEI implements IModPlugin {
    @Nonnull
    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(Colony4CC.ID, "jei");
    }

    @Override
    public void registerRecipes(@Nonnull IRecipeRegistration registration) {
        List<ItemStack> upgrades = new ArrayList<>();
        upgrades.add(new ItemStack(Colony4CC.UPGRADE_WIRELESS_NORMAL.get()));
        upgrades.add(new ItemStack(Colony4CC.UPGRADE_WIRELESS_ADVANCED.get()));
        registration.addIngredientInfo(upgrades, VanillaTypes.ITEM, "info.colony4cc.upgrade_merging");
    }
}
