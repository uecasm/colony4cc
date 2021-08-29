package nz.co.mirality.colony4cc.peripheral;

import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import nz.co.mirality.colony4cc.block.PeripheralTile;

import javax.annotation.Nullable;

public class TileColonyPeripheral extends ColonyPeripheral {
    public TileColonyPeripheral(PeripheralTile tile) {
        this.tile = tile;
    }

    private final PeripheralTile tile;

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        if (other instanceof TileColonyPeripheral) {
            return this.tile == ((TileColonyPeripheral) other).tile;
        }
        return false;
    }

    @Nullable
    @Override
    public Object getTarget() {
        return this.tile;
    }

    @Nullable
    @Override
    public World getWorld() {
        return this.tile.getLevel();
    }

    @Override
    public BlockPos getPos() {
        return this.tile.getBlockPos();
    }

    @Override
    @Nullable
    protected IItemHandler getInventory(@Nullable Direction side)
    {
        if (side == null) side = Direction.UP;
        final BlockPos pos = getPos().relative(side);
        final TileEntity target = getWorld().getBlockEntity(pos);
        if (target == null) return null;

        return target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side.getOpposite()).orElse(null);
    }
}
