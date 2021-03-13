package nz.co.mirality.colony4cc.data;

import dan200.computercraft.api.pocket.IPocketUpgrade;
import dan200.computercraft.data.RecipeWrapper;
import dan200.computercraft.shared.PocketUpgrades;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.pocket.items.PocketComputerItemFactory;
import dan200.computercraft.shared.util.ImpostorRecipe;
import net.minecraft.advancements.criterion.InventoryChangeTrigger;
import net.minecraft.data.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IItemProvider;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.Tags;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.function.Consumer;

import static com.ldtteam.structurize.items.ModItems.buildTool;
import static com.minecolonies.api.items.ModItems.*;
import static dan200.computercraft.shared.Registry.ModItems.*;
import static nz.co.mirality.colony4cc.Colony4CC.*;

public class Recipes extends RecipeProvider {
    public Recipes(DataGenerator generator) { super(generator); }

    @Override
    protected void buildShapelessRecipes(@Nonnull Consumer<IFinishedRecipe> add) {
        registerColonyPeripheral(add);
        registerPocketUpgrades(add);
    }

    private void registerColonyPeripheral(Consumer<IFinishedRecipe> add) {
        ShapedRecipeBuilder
                .shaped(PERIPHERAL_ITEM.get())
                .pattern("#B#")
                .pattern("CIS")
                .pattern("#M#")
                .define('#', Tags.Items.DUSTS_REDSTONE)
                .define('I', buildTool)
                .define('B', flagBanner)
                .define('C', clipboard)
                .define('S', resourceScroll)
                .define('M', WIRED_MODEM.get())
                .unlockedBy("has_items", inventoryChange(clipboard, resourceScroll))
                .save(add);

        ShapelessRecipeBuilder
                .shapeless(UPGRADE_WIRELESS_NORMAL.get())
                .requires(PERIPHERAL_ITEM.get())
                .requires(WIRELESS_MODEM_NORMAL.get())
                .requires(Tags.Items.SLIMEBALLS)
                .unlockedBy("has_items", inventoryChange(PERIPHERAL_ITEM.get(), WIRELESS_MODEM_NORMAL.get()))
                .save(add);

        ShapelessRecipeBuilder
                .shapeless(UPGRADE_WIRELESS_ADVANCED.get())
                .requires(PERIPHERAL_ITEM.get())
                .requires(WIRELESS_MODEM_ADVANCED.get())
                .requires(Tags.Items.SLIMEBALLS)
                .unlockedBy("has_items", inventoryChange(PERIPHERAL_ITEM.get(), WIRELESS_MODEM_ADVANCED.get()))
                .save(add);
    }

    private void registerPocketUpgrades(Consumer<IFinishedRecipe> add) {
        for (ComputerFamily family : ComputerFamily.values()) {
            ItemStack base = PocketComputerItemFactory.create(-1, null, -1, family, null);
            if (base.isEmpty()) continue;

            String nameId = family.name().toLowerCase(Locale.ROOT);

            for (IPocketUpgrade upgrade : PocketUpgrades.getUpgrades()) {
                if (!upgrade.getUpgradeID().getNamespace().equals(ID)) continue;

                ItemStack result = PocketComputerItemFactory.create(-1, null, -1, family, upgrade);
                ShapedRecipeBuilder
                        .shaped(result.getItem())
                        .group(String.format("%s:pocket_%s", CC_MOD_ID, nameId))
                        .pattern("#")
                        .pattern("P")
                        .define('#', base.getItem())
                        .define('P', upgrade.getCraftingItem().getItem())
                        .unlockedBy("has_items",
                                inventoryChange(base.getItem(), upgrade.getCraftingItem().getItem()))
                        .save(
                                RecipeWrapper.wrap(ImpostorRecipe.SERIALIZER, add, result.getTag()),
                                new ResourceLocation(ID, String.format("pocket_%s/%s",
                                        nameId, upgrade.getUpgradeID().getPath()))
                        );
            }
        }
    }

    private static InventoryChangeTrigger.Instance inventoryChange(IItemProvider... stack)
    {
        return InventoryChangeTrigger.Instance.hasItems(stack);
    }
}
