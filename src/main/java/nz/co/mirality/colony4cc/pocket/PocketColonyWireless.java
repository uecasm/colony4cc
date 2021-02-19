package nz.co.mirality.colony4cc.pocket;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.AbstractPocketUpgrade;
import dan200.computercraft.api.pocket.IPocketAccess;
import dan200.computercraft.core.computer.ComputerSide;
import dan200.computercraft.shared.pocket.core.PocketServerComputer;
import dan200.computercraft.shared.pocket.peripherals.PocketModemPeripheral;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import nz.co.mirality.colony4cc.Colony4CC;
import nz.co.mirality.colony4cc.peripheral.PocketColonyPeripheral;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public class PocketColonyWireless extends AbstractPocketUpgrade {
    public static final ComputerSide COLONY_SIDE = ComputerSide.BOTTOM;

    private final boolean advanced;

    public PocketColonyWireless(boolean advanced) {
        super(new ResourceLocation(Colony4CC.ID,
                        advanced ? "pocket_colony_wireless_advanced" : "pocket_colony_wireless_normal"),
                advanced ? Colony4CC.UPGRADE_WIRELESS_ADVANCED.get() : Colony4CC.UPGRADE_WIRELESS_NORMAL.get());

        this.advanced = advanced;
    }

    @Nullable
    @Override
    public IPeripheral createPeripheral(@Nonnull IPocketAccess access) {
        return new PocketModemPeripheral(this.advanced);
    }

    @Nonnull
    public IPeripheral createColonyPeripheral(@Nonnull IPocketAccess access) {
        return new PocketColonyPeripheral();
    }

    @Override
    public void update(@Nonnull IPocketAccess access, @Nullable IPeripheral peripheral) {
        if (peripheral instanceof PocketModemPeripheral) {
            IPocketAccess hijacked = new HijackedPocketAccess(access);

            (this.advanced ? ComputerCraft.PocketUpgrades.wirelessModemAdvanced
                    : ComputerCraft.PocketUpgrades.wirelessModemNormal)
                    .update(hijacked, peripheral);
        }

        peripheral = ((PocketServerComputer) access).getPeripheral(COLONY_SIDE);
        if (peripheral instanceof PocketColonyPeripheral) {
            Colony4CC.POCKET_COLONY.get().update(access, peripheral);
        }
    }

    private static class HijackedPocketAccess implements IPocketAccess {
        private final IPocketAccess access;
        private int light;

        public HijackedPocketAccess(IPocketAccess access) {
            this.access = access;
            this.light = access.getLight();
        }

        @Override
        @Nullable
        public Entity getEntity() { return this.access.getEntity(); }

        @Override
        public int getColour() { return this.access.getColour(); }

        @Override
        public void setColour(int i) { this.access.setColour(i); }

        @Override
        public int getLight() { return this.light; }

        @Override
        public void setLight(int i) { this.light = i; }

        @Override
        @Nonnull
        public CompoundNBT getUpgradeNBTData() { return this.access.getUpgradeNBTData(); }

        @Override
        public void updateUpgradeNBTData() { this.access.updateUpgradeNBTData(); }

        @Override
        public void invalidatePeripheral() {}

        @Override
        @Nonnull
        public Map<ResourceLocation, IPeripheral> getUpgrades() { return this.access.getUpgrades(); }
    }
}
