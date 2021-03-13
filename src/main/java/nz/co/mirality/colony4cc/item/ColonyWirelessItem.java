package nz.co.mirality.colony4cc.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import nz.co.mirality.colony4cc.Colony4CC;

import javax.annotation.Nonnull;

public class ColonyWirelessItem extends Item {
    private final boolean advanced;

    private static final ResourceLocation WIRELESS_NORMAL = new ResourceLocation(Colony4CC.CC_MOD_ID, "wireless_modem_normal");
    private static final ResourceLocation WIRELESS_ADVANCED = new ResourceLocation(Colony4CC.CC_MOD_ID, "wireless_modem_advanced");

    public ColonyWirelessItem(boolean advanced, Item.Properties properties) {
        super(properties);
        this.advanced = advanced;
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> use(@Nonnull World world, @Nonnull PlayerEntity player, @Nonnull Hand hand) {
        if (player.isShiftKeyDown() && hand == Hand.MAIN_HAND) {
            ItemStack item = player.getItemInHand(hand);
            if (item.getCount() == 1 && item.getItem() == this) {
                ItemStack modem = new ItemStack(ForgeRegistries.ITEMS.getValue(this.advanced ? WIRELESS_ADVANCED : WIRELESS_NORMAL));
                ItemHandlerHelper.giveItemToPlayer(player, modem);
                return ActionResult.success(new ItemStack(Colony4CC.PERIPHERAL_ITEM.get()));
            }
        }

        return super.use(world, player, hand);
    }
}
