package nz.co.mirality.colony4cc.network;

import com.google.common.collect.ImmutableList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.util.text.Color;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;
import nz.co.mirality.colony4cc.client.ClientRenderer;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SAddBuildingOverlayPacket
{
    private final ResourceLocation world;
    private final BlockPos pos;
    private final List<Overlay> overlays;

    public static class Overlay
    {
        private final MutableBoundingBox bounds;
        private final int color;
        private final boolean fill;
        private final boolean strong;

        public Overlay(@Nonnull final MutableBoundingBox bounds,
                       @Nonnull final int color,
                       final boolean fill,
                       final boolean strong)
        {
            this.bounds = bounds;
            this.color = color;
            this.fill = fill;
            this.strong = strong;
        }

        public MutableBoundingBox bounds() { return bounds; }
        public int color() { return color; }
        public boolean fill() { return fill; }
        public boolean strong() { return strong; }

        public void write(@Nonnull final PacketBuffer buf) {
            buf.writeVarInt(bounds.x0);
            buf.writeVarInt(bounds.y0);
            buf.writeVarInt(bounds.z0);
            buf.writeVarInt(bounds.x1);
            buf.writeVarInt(bounds.y1);
            buf.writeVarInt(bounds.z1);
            buf.writeInt(color);
            buf.writeBoolean(fill);
            buf.writeBoolean(strong);
        }

        public static Overlay read(@Nonnull final PacketBuffer buf) {
            final int x1 = buf.readVarInt();
            final int y1 = buf.readVarInt();
            final int z1 = buf.readVarInt();
            final int x2 = buf.readVarInt();
            final int y2 = buf.readVarInt();
            final int z2 = buf.readVarInt();
            final int color = buf.readInt();
            final boolean fill = buf.readBoolean();
            final boolean strong = buf.readBoolean();
            return new Overlay(new MutableBoundingBox(x1, y1, z1, x2, y2, z2), color, fill, strong);
        }
    }

    public SAddBuildingOverlayPacket(@Nonnull final ResourceLocation world,
                                     @Nonnull final BlockPos pos,
                                     @Nonnull final List<Overlay> overlays) {
        this.world = world;
        this.pos = pos;
        this.overlays = ImmutableList.copyOf(overlays);
    }

    public ResourceLocation world() { return world; }
    public BlockPos pos() { return pos; }
    public List<Overlay> overlays() { return overlays; }

    public void write(@Nonnull final PacketBuffer buf) {
        buf.writeResourceLocation(world);
        buf.writeBlockPos(pos);
        buf.writeVarInt(overlays.size());
        for (final Overlay overlay : overlays) {
            overlay.write(buf);
        }
    }

    @Nonnull
    public static SAddBuildingOverlayPacket read(@Nonnull final PacketBuffer buf) {
        final ResourceLocation world = buf.readResourceLocation();
        final BlockPos pos = buf.readBlockPos();
        final int size = buf.readVarInt();
        final List<Overlay> overlays = new ArrayList<>();
        for (int i = 0; i < size; ++i) {
            overlays.add(Overlay.read(buf));
        }
        return new SAddBuildingOverlayPacket(world, pos, overlays);
    }

    public static void handle(@Nonnull final SAddBuildingOverlayPacket msg,
                              @Nonnull final Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientRenderer.addBuildingOverlay(msg, ctx)));
        ctx.get().setPacketHandled(true);
    }
}
