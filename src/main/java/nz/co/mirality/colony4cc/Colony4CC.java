package nz.co.mirality.colony4cc;

import dan200.computercraft.shared.PocketUpgrades;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.common.util.Lazy;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import nz.co.mirality.colony4cc.block.PeripheralBlock;
import nz.co.mirality.colony4cc.block.PeripheralTile;
import nz.co.mirality.colony4cc.item.BaseBlockItem;
import nz.co.mirality.colony4cc.pocket.PocketColony;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

@Mod(Colony4CC.ID)
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Colony4CC {
    public static final String ID = "colony4cc";
    public static final String NAME = "MineColonies for ComputerCraft";

    public static final String CC_MOD_ID = "computercraft";
    public static final String COLONY_MOD_ID = "minecolonies";

    public static final String PERIPHERAL_ID = "colony_peripheral";
    public static final String PERIPHERAL_NAME = "colony";

    public static final Logger LOGGER = LogManager.getLogger();

    public static final Supplier<ItemGroup> GROUP = Lazy.of(Colony4CC::getComputerCraftGroup);

    private static final DeferredRegister<Block> BLOCKS
            = DeferredRegister.create(ForgeRegistries.BLOCKS, ID);
    private static final DeferredRegister<Item> ITEMS
            = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    private static final DeferredRegister<TileEntityType<?>> TILES
            = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, ID);

    public static final RegistryObject<PeripheralBlock> PERIPHERAL_BLOCK
            = BLOCKS.register(PERIPHERAL_ID, PeripheralBlock::new);
    public static final RegistryObject<BaseBlockItem> PERIPHERAL_ITEM
            = ITEMS.register(PERIPHERAL_ID,
                () -> new BaseBlockItem(PERIPHERAL_BLOCK.get(), new Item.Properties().group(GROUP.get())));
    public static final RegistryObject<TileEntityType<?>> PERIPHERAL_TILE
            = TILES.register(PERIPHERAL_ID,
                () -> TileEntityType.Builder.create(PeripheralTile::new).build(null));

    public Colony4CC() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        BLOCKS.register(bus);
        ITEMS.register(bus);
        TILES.register(bus);
    }

    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent e) {
        registerComputerUpgrades();
    }

    public static void registerComputerUpgrades() {
        PocketUpgrades.register(new PocketColony());
    }

    private static ItemGroup getComputerCraftGroup() {
        for (ItemGroup group : ItemGroup.GROUPS) {
            if (group.getPath().equals(CC_MOD_ID)) return group;
        }

        // couldn't find it for some reason...
        return ItemGroup.MISC;
    }
}
