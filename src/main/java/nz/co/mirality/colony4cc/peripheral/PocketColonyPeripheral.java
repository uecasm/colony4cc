package nz.co.mirality.colony4cc.peripheral;

import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import nz.co.mirality.colony4cc.Colony4CC;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PocketColonyPeripheral extends ColonyPeripheral {
    private World world;
    private BlockPos pos = BlockPos.ZERO;
    private Entity tracking;

    public void setEntity(Entity entity) {
        if (entity != null) {
            this.world = entity.getCommandSenderWorld();
            this.pos = entity.blockPosition();
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

    @Override
    @Nullable
    protected IItemHandler getInventory(@Nullable final Direction side)
    {
        if (tracking == null) return null;

        return tracking.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side).orElse(null);
    }

    @Override
    protected boolean consumeFuel(@Nonnull final IItemHandler handler, @Nonnull final Item fuel, final int count)
    {
        if (Colony4CC.CONFIG.getFreeCreativePocketPlayerCost() &&
                tracking instanceof ServerPlayerEntity &&
                ((ServerPlayerEntity) tracking).isCreative()) {
            return true;
        }

        return super.consumeFuel(handler, fuel, count);
    }
}
