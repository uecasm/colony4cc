package nz.co.mirality.colony4cc.item;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

public class BaseBlockItem extends BlockItem {
    public BaseBlockItem(@Nonnull final Block type, @Nonnull final Item.Properties props) {
        super(type, props);
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }
}
