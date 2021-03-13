package nz.co.mirality.colony4cc.peripheral;

import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
}
