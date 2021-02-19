package nz.co.mirality.colony4cc.mixin;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.IPocketAccess;
import dan200.computercraft.api.pocket.IPocketUpgrade;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.pocket.core.PocketServerComputer;
import net.minecraft.world.World;
import nz.co.mirality.colony4cc.peripheral.PocketColonyPeripheral;
import nz.co.mirality.colony4cc.pocket.PocketColonyWireless;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PocketServerComputer.class)
public abstract class MixinPocketServerComputer extends ServerComputer implements IPocketAccess {
    @Shadow(remap = false)
    private IPocketUpgrade upgrade;

    private MixinPocketServerComputer(World world, int computerID, String label, int instanceID, ComputerFamily family, int terminalWidth, int terminalHeight) {
        super(world, computerID, label, instanceID, family, terminalWidth, terminalHeight);
    }

    @Inject(at = @At("HEAD"), method = "invalidatePeripheral()V", remap = false)
    private void invalidatePeripheral(CallbackInfo callback) {
        if (this.upgrade instanceof PocketColonyWireless) {
            PocketColonyWireless upgrade = (PocketColonyWireless) this.upgrade;
            IPeripheral sidePeripheral = upgrade.createColonyPeripheral(this);
            this.setPeripheral(PocketColonyWireless.COLONY_SIDE, sidePeripheral);
        } else if (this.getPeripheral(PocketColonyWireless.COLONY_SIDE) instanceof PocketColonyPeripheral) {
            this.setPeripheral(PocketColonyWireless.COLONY_SIDE, null);
        }
    }
}
