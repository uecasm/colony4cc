package nz.co.mirality.colony4cc.network;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

import static nz.co.mirality.colony4cc.Colony4CC.ID;

public class Colony4CCPacketHandler
{
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public static void init() {
        int id = 14;
        INSTANCE.registerMessage(++id, SAddBuildingOverlayPacket.class,
                SAddBuildingOverlayPacket::write,
                SAddBuildingOverlayPacket::read,
                SAddBuildingOverlayPacket::handle);
    }
}
