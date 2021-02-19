package nz.co.mirality.colony4cc.peripheral;

import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class PocketColonyPeripheral extends ColonyPeripheral {
    private World world;
    private BlockPos pos = BlockPos.ZERO;
    private Entity tracking;

    public void setEntity(Entity entity) {
        if (entity != null) {
            this.world = entity.getEntityWorld();
            this.pos = entity.getPosition();
            this.tracking = entity;
        }
        this.securityCheck(entity);
    }

    @Override
    public World getWorld() {
        return this.world;
    }

    @Override
    public BlockPos getPos() {
        return this.pos;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return this == other;
    }

    @Override
    public Object getTarget() {
        return this.tracking;
    }
}
