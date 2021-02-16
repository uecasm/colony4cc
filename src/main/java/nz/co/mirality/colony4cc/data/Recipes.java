package nz.co.mirality.colony4cc.data;

import dan200.computercraft.api.pocket.IPocketUpgrade;
import dan200.computercraft.data.RecipeWrapper;
import dan200.computercraft.shared.PocketUpgrades;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.pocket.items.PocketComputerItemFactory;
import dan200.computercraft.shared.util.ImpostorRecipe;
import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.IFinishedRecipe;
import net.minecraft.data.RecipeProvider;
import net.minecraft.data.ShapedRecipeBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IItemProvider;
import net.minecraft.util.ResourceLocation;
import nz.co.mirality.colony4cc.Colony4CC;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.function.Consumer;

public class Recipes extends RecipeProvider {
    public Recipes(DataGenerator generator) { super(generator); }

    @Override
    protected void registerRecipes(@Nonnull Consumer<IFinishedRecipe> add) {
        for (ComputerFamily family : ComputerFamily.values()) {
            ItemStack base = PocketComputerItemFactory.create(-1, null, -1, family, null);
            if (base.isEmpty()) continue;

            String nameId = family.name().toLowerCase(Locale.ROOT);

            for (IPocketUpgrade upgrade : PocketUpgrades.getUpgrades()) {
                if (!upgrade.getUpgradeID().getNamespace().equals(Colony4CC.ID)) continue;

                ItemStack result = PocketComputerItemFactory.create(-1, null, -1, family, upgrade);
                ShapedRecipeBuilder
                        .shapedRecipe(result.getItem())
                        .setGroup(String.format("%s:pocket_%s", Colony4CC.CC_MOD_ID, nameId))
                        .patternLine("#")
                        .patternLine("P")
                        .key('#', base.getItem())
                        .key('P', upgrade.getCraftingItem().getItem())
                        .addCriterion("has_items",
                                inventoryChange(base.getItem(), upgrade.getCraftingItem().getItem()))
                        .build(
                                RecipeWrapper.wrap(ImpostorRecipe.SERIALIZER, add, result.getTag()),
                                new ResourceLocation(Colony4CC.ID, String.format("pocket_%s/%s",
                                        nameId, upgrade.getUpgradeID().getPath()))
                        );
            }
        }
    }

    private static InventoryChangeTrigger.Instance inventoryChange(IItemProvider... stack)
    {
        return InventoryChangeTrigger.Instance.forItems(stack);
    }
}
