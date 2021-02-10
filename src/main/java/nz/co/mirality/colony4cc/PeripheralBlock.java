package nz.co.mirality.colony4cc;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockReader;
import net.minecraftforge.common.ToolType;

import javax.annotation.Nullable;

public class PeripheralBlock extends Block {
    public PeripheralBlock() {
        super(AbstractBlock.Properties.create(Material.IRON)
            .hardnessAndResistance(2.2f, 11.f)
            .harvestTool(ToolType.PICKAXE).harvestLevel(0)
            .sound(SoundType.METAL));
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return Colony4CC.PERIPHERAL_TILE.get().create();
    }
}
