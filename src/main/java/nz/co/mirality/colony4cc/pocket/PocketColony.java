package nz.co.mirality.colony4cc.pocket;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.AbstractPocketUpgrade;
import dan200.computercraft.api.pocket.IPocketAccess;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import nz.co.mirality.colony4cc.Colony4CC;
import nz.co.mirality.colony4cc.peripheral.PocketColonyPeripheral;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PocketColony extends AbstractPocketUpgrade {
    public PocketColony() {
        super(new ResourceLocation(Colony4CC.ID, "pocket_colony"),
                Colony4CC.PERIPHERAL_BLOCK.get());
    }

    @Nullable
    @Override
    public IPeripheral createPeripheral(@Nonnull IPocketAccess access) {
        return new PocketColonyPeripheral();
    }

    @Override
    public void update(@Nonnull IPocketAccess access, @Nullable IPeripheral peripheral) {
        if (!(peripheral instanceof PocketColonyPeripheral)) return;

        PocketColonyPeripheral colony = (PocketColonyPeripheral) peripheral;

        colony.setEntity(access.getEntity());

        access.setLight(colony.isValid() ? 0x00BA00 : 0xBA0000);
    }
}
