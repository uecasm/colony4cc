package nz.co.mirality.colony4cc;

import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static dan200.computercraft.shared.Capabilities.CAPABILITY_PERIPHERAL;

public class PeripheralTile extends TileEntity {
    public PeripheralTile() {
        super(Colony4CC.PERIPHERAL_TILE.get());

        this.peripheral = new ColonyPeripheral(this);
    }

    private final ColonyPeripheral peripheral;
    private LazyOptional<IPeripheral> peripheralCap;

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == CAPABILITY_PERIPHERAL) {
            if (this.peripheralCap == null) {
                this.peripheralCap = LazyOptional.of(() -> this.peripheral);
            }
            return this.peripheralCap.cast();
        }

        return super.getCapability(cap, side);
    }

    @Override
    protected void invalidateCaps() {
        super.invalidateCaps();

        if (this.peripheralCap != null) {
            this.peripheralCap.invalidate();
        }
    }
}
