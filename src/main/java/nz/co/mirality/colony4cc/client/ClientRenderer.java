package nz.co.mirality.colony4cc.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeBuffers;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.ColorHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.NetworkEvent;
import nz.co.mirality.colony4cc.network.SAddBuildingOverlayPacket;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import static nz.co.mirality.colony4cc.Colony4CC.ID;
import static nz.co.mirality.colony4cc.Constants.TICKS_PER_MINUTE;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = ID, value = Dist.CLIENT)
public class ClientRenderer
{
    public static final RenderTypeBuffers renderBuffers = new RenderTypeBuffers();
    private static final IRenderTypeBuffer.Impl renderBuffer = renderBuffers.bufferSource();
    private static final Supplier<IVertexBuilder> linesWithCullAndDepth = () -> renderBuffer.getBuffer(RenderType.lines());
    private static final Supplier<IVertexBuilder> linesWithoutCullAndDepth = () -> renderBuffer.getBuffer(CustomRenderTypes.OVERLAY_LINES);
    private static final Supplier<IVertexBuilder> claimArea = () -> renderBuffer.getBuffer(CustomRenderTypes.CLAIM_AREA);

    private static final ConcurrentMap<ResourceLocation, ConcurrentMap<BlockPos, Overlay>> overlays = new ConcurrentHashMap<>();

    private static class Overlay
    {
        private final List<SAddBuildingOverlayPacket.Overlay> overlays;
        private int lifetime;

        public Overlay(@Nonnull final List<SAddBuildingOverlayPacket.Overlay> overlays) {
            this.overlays = overlays;
            this.lifetime = 2 * TICKS_PER_MINUTE;
        }

        public List<SAddBuildingOverlayPacket.Overlay> overlays() { return overlays; }

        public boolean tick() {
            return --lifetime > 0;
        }
    }

    public static void addBuildingOverlay(@Nonnull final SAddBuildingOverlayPacket msg,
                                          @Nonnull final Supplier<NetworkEvent.Context> ctx) {
        final ConcurrentMap<BlockPos, Overlay> posMap = overlays.computeIfAbsent(msg.world(), k -> new ConcurrentHashMap<>());
        posMap.put(msg.pos(), new Overlay(msg.overlays()));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void clientTick(@Nonnull final TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !Minecraft.getInstance().isPaused()) {
            overlays.values().forEach(posMap -> posMap.entrySet().removeIf(e -> !e.getValue().tick()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void clientDisconnected(@Nonnull final ClientPlayerNetworkEvent.LoggedOutEvent event) {
        overlays.clear();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderWorldLastEvent(@Nonnull final RenderWorldLastEvent event) {
        final ClientWorld world = Minecraft.getInstance().level;
        final ConcurrentMap<BlockPos, Overlay> posMap = overlays.get(world.dimension().location());
        if (posMap == null || posMap.isEmpty()) return;

        final MatrixStack matrixStack = event.getMatrixStack();
        final Vector3d viewPosition = Minecraft.getInstance().getEntityRenderDispatcher().camera.getPosition();

        matrixStack.pushPose();
        matrixStack.translate(-viewPosition.x, -viewPosition.y, -viewPosition.z);

        // todo: check bounding area vs. camera to ignore invisible regions?
        for (final Overlay overlays : posMap.values())
        {
            // render strong overlays first
            RenderSystem.disableDepthTest();
            for (final SAddBuildingOverlayPacket.Overlay overlay : overlays.overlays())
            {
                if (!overlay.strong()) continue;
                renderOverlay(matrixStack, linesWithoutCullAndDepth.get(), overlay);
            }
            renderBuffer.endBatch();

            // render weak overlays next
            RenderSystem.enableDepthTest();
            for (final SAddBuildingOverlayPacket.Overlay overlay : overlays.overlays())
            {
                if (overlay.strong()) continue;
                renderOverlay(matrixStack, linesWithCullAndDepth.get(), overlay);
            }
            renderBuffer.endBatch();
        }

        renderBuffer.endBatch();
        matrixStack.popPose();
    }

    private static void renderOverlay(@Nonnull final MatrixStack matrixStack,
                                      @Nonnull final IVertexBuilder borderVertexBuilder,
                                      @Nonnull final SAddBuildingOverlayPacket.Overlay overlay) {
        final AxisAlignedBB box = AxisAlignedBB.of(overlay.bounds()).inflate(0.002D);
        final float red = ColorHelper.PackedColor.red(overlay.color()) / 255F;
        final float green = ColorHelper.PackedColor.green(overlay.color()) / 255F;
        final float blue = ColorHelper.PackedColor.blue(overlay.color()) / 255F;
        if (overlay.fill()) {
            renderFilledBox(matrixStack, borderVertexBuilder, box,
                    red, green, blue, 1F,
                    red, green, blue, 0.2F);
        } else {
            renderBoxBorder(matrixStack, borderVertexBuilder, box,
                    red, green, blue, 1F);
        }
    }

    private static void renderBoxBorder(@Nonnull final MatrixStack matrixStack,
                                        @Nonnull final IVertexBuilder vertexBuilder,
                                        @Nonnull final AxisAlignedBB box,
                                        final float red, final float green, final float blue, final float alpha) {
        WorldRenderer.renderLineBox(matrixStack, vertexBuilder,
                box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ,
                red, green, blue, alpha);
    }

    private static void renderBiQuad(@Nonnull final MatrixStack matrixStack,
                                     @Nonnull final IVertexBuilder vertexBuilder,
                                     @Nonnull final Vector3d p1,
                                     @Nonnull final Vector3d p2,
                                     @Nonnull final Vector3d p3,
                                     @Nonnull final Vector3d p4,
                                     final float red, final float green, final float blue, final float alpha) {
        final Matrix4f matrix4f = matrixStack.last().pose();

        vertexBuilder.vertex(matrix4f, (float)p1.x, (float)p1.y, (float)p1.z).color(red, green, blue, alpha).endVertex();
        vertexBuilder.vertex(matrix4f, (float)p2.x, (float)p2.y, (float)p2.z).color(red, green, blue, alpha).endVertex();
        vertexBuilder.vertex(matrix4f, (float)p3.x, (float)p3.y, (float)p3.z).color(red, green, blue, alpha).endVertex();
        vertexBuilder.vertex(matrix4f, (float)p4.x, (float)p4.y, (float)p4.z).color(red, green, blue, alpha).endVertex();

        vertexBuilder.vertex(matrix4f, (float)p4.x, (float)p4.y, (float)p4.z).color(red, green, blue, alpha).endVertex();
        vertexBuilder.vertex(matrix4f, (float)p3.x, (float)p3.y, (float)p3.z).color(red, green, blue, alpha).endVertex();
        vertexBuilder.vertex(matrix4f, (float)p2.x, (float)p2.y, (float)p2.z).color(red, green, blue, alpha).endVertex();
        vertexBuilder.vertex(matrix4f, (float)p1.x, (float)p1.y, (float)p1.z).color(red, green, blue, alpha).endVertex();
    }

    private static void renderBoxFill(@Nonnull final MatrixStack matrixStack,
                                      @Nonnull final AxisAlignedBB box,
                                      final float red, final float green, final float blue, final float alpha) {
        final IVertexBuilder vertexBuilder = claimArea.get();

        final Vector3d[] points = {
            new Vector3d(box.minX, box.minY, box.minZ), //111 a=0
            new Vector3d(box.maxX, box.minY, box.minZ), //211 b=1
            new Vector3d(box.maxX, box.minY, box.maxZ), //212 c=2
            new Vector3d(box.minX, box.minY, box.maxZ), //112 d=3
            new Vector3d(box.minX, box.maxY, box.minZ), //121 e=4
            new Vector3d(box.maxX, box.maxY, box.minZ), //221 f=5
            new Vector3d(box.maxX, box.maxY, box.maxZ), //222 g=6
            new Vector3d(box.minX, box.maxY, box.maxZ), //122 h=7
        };

        // front face
        renderBiQuad(matrixStack, vertexBuilder, points[0], points[4], points[5], points[1], red, green, blue, alpha);
        // back face
        renderBiQuad(matrixStack, vertexBuilder, points[3], points[2], points[6], points[7], red, green, blue, alpha);
        // bottom face
        renderBiQuad(matrixStack, vertexBuilder, points[0], points[1], points[2], points[3], red, green, blue, alpha);
        // top face
        renderBiQuad(matrixStack, vertexBuilder, points[4], points[7], points[6], points[5], red, green, blue, alpha);
        // left face
        renderBiQuad(matrixStack, vertexBuilder, points[0], points[3], points[7], points[4], red, green, blue, alpha);
        // right face (b,f,g,c)
        renderBiQuad(matrixStack, vertexBuilder, points[1], points[5], points[6], points[2], red, green, blue, alpha);
    }

    private static void renderFilledBox(@Nonnull final MatrixStack matrixStack,
                                        @Nonnull final IVertexBuilder borderVertexBuilder,
                                        @Nonnull final AxisAlignedBB box,
                                        final float borderRed, final float borderGreen, final float borderBlue, final float borderAlpha,
                                        final float fillRed, final float fillGreen, final float fillBlue, final float fillAlpha) {
        renderBoxBorder(matrixStack, borderVertexBuilder, box, borderRed, borderGreen, borderBlue, borderAlpha);
        renderBoxFill(matrixStack, box, fillRed, fillGreen, fillBlue, fillAlpha);
    }



    /*
    @NotNull
    @SuppressWarnings("SameParameterValue")
    private static Tuple<VoxelShape, Vector3d> createBox(@NotNull final BlockPos size) {
        final Vector3d scale = new Vector3d(size.getX(), size.getY(), size.getZ());
        return new Tuple<>(VoxelShapes.block(), scale);
    }

    @NotNull
    @SuppressWarnings("SameParameterValue")
    private static Tuple<VoxelShape, Vector3d> createManhattenCircle(final int radius,
                                                                     final int height) {
        VoxelShape shape = VoxelShapes.empty();
        final double step = 0.5D / radius;
        for (double i = 0; i < 0.5; i += step) {
            final double x1 = i;
            final double z1 = 0.5 - i;
            final double x2 = 1 - i - step;
            final double z2 = 0.5 + i + step;
            shape = VoxelShapes.joinUnoptimized(shape, VoxelShapes.box(x1, 0, z1, x2, 1, z2), IBooleanFunction.OR);
        }
        return new Tuple<>(shape.optimize(), new Vector3d(radius*2, height, radius*2));
    }

    @SuppressWarnings("SameParameterValue")
    private static void renderBorderedSolid(@NotNull final Tuple<VoxelShape, Vector3d> shapeScale,
                                            final double xo, final double yo, final double zo,
                                            final float borderRed, final float borderGreen, final float borderBlue, final float borderAlpha,
                                            final float fillRed, final float fillGreen, final float fillBlue, final float fillAlpha,
                                            @NotNull final MatrixStack matrixStack) {
        renderSolid(shapeScale.getA(), xo, yo, zo, shapeScale.getB(), fillRed, fillGreen, fillBlue, fillAlpha, matrixStack, claimArea.get());
        renderBorder(shapeScale.getA(), xo, yo, zo, shapeScale.getB(), borderRed, borderGreen, borderBlue, borderAlpha, matrixStack, linesWithCullAndDepth.get());
    }

    @SuppressWarnings("SameParameterValue")
    private static void renderSolid(@NotNull final VoxelShape shape,
                                    final double xo, final double yo, final double zo, final Vector3d scale,
                                    final float red, final float green, final float blue, final float alpha,
                                    @NotNull final MatrixStack matrixStack,
                                    @NotNull final IVertexBuilder vertexBuilder) {
        final Vector3d viewPosition = Minecraft.getInstance().getEntityRenderDispatcher().camera.getPosition();

        matrixStack.pushPose();
        matrixStack.translate(-viewPosition.x, -viewPosition.y, -viewPosition.z);

        renderShape(matrixStack, vertexBuilder, shape, xo, yo, zo, scale, red, green, blue, alpha);

        matrixStack.popPose();
    }

    private static void renderBorder(@NotNull final VoxelShape shape,
                                     final double xo, final double yo, final double zo, final Vector3d scale,
                                     final float red, final float green, final float blue, final float alpha,
                                     @NotNull final MatrixStack matrixStack,
                                     @NotNull final IVertexBuilder vertexBuilder) {
        final Vector3d viewPosition = Minecraft.getInstance().getEntityRenderDispatcher().camera.getPosition();

        matrixStack.pushPose();
        matrixStack.translate(-viewPosition.x, -viewPosition.y, -viewPosition.z);

        final Matrix4f matrix4f = matrixStack.last().pose();
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            vertexBuilder.vertex(matrix4f, (float)(x1*scale.x + xo), (float)(y1*scale.y + yo), (float)(z1*scale.z + zo)).color(red, green, blue, alpha).endVertex();
            vertexBuilder.vertex(matrix4f, (float)(x2*scale.x + xo), (float)(y2*scale.y + yo), (float)(z2*scale.z + zo)).color(red, green, blue, alpha).endVertex();
        });

        matrixStack.popPose();
    }

    private static void renderShape(final MatrixStack matrixStack,
                                    final IVertexBuilder vertexBuilder,
                                    final VoxelShape shape,
                                    final double xo, final double yo, final double zo, final Vector3d scale,
                                    final float red, final float green, final float blue, final float alpha) {
        final Matrix4f matrix4f = matrixStack.last().pose();

        for (final Direction side : Direction.values()) {
            final VoxelShape face = shape.getFaceShape(side);
            //final VoxelShape face = VoxelShapes.getFaceShape(shape, side);
            face.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
                switch (side) {
                    case DOWN:      // negative Y
                        // bottom face (a,b,c,d)
                        //vertexBuilder.vertex(matrix4f, (float)(x1*scale.x + xo), (float)(y1*scale.y + yo), (float)(z1*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        //vertexBuilder.vertex(matrix4f, (float)(x2*scale.x + xo), (float)(y1*scale.y + yo), (float)(z1*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        //vertexBuilder.vertex(matrix4f, (float)(x2*scale.x + xo), (float)(y1*scale.y + yo), (float)(z2*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        //vertexBuilder.vertex(matrix4f, (float)(x1*scale.x + xo), (float)(y1*scale.y + yo), (float)(z2*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        break;
                    case UP:        // positive Y
                        // top face (e,h,g,f)
                        //vertexBuilder.vertex(matrix4f, (float)(x1*scale.x + xo), (float)(y2*scale.y + yo), (float)(z1*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        //vertexBuilder.vertex(matrix4f, (float)(x1*scale.x + xo), (float)(y2*scale.y + yo), (float)(z2*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        //vertexBuilder.vertex(matrix4f, (float)(x2*scale.x + xo), (float)(y2*scale.y + yo), (float)(z2*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        //vertexBuilder.vertex(matrix4f, (float)(x2*scale.x + xo), (float)(y2*scale.y + yo), (float)(z1*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        break;
                    case NORTH:     // negative Z
                        // front face (a,e,f,b)
                        vertexBuilder.vertex(matrix4f, (float)(x1*scale.x + xo), (float)(y1*scale.y + yo), (float)(z1*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x1*scale.x + xo), (float)(y2*scale.y + yo), (float)(z1*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x2*scale.x + xo), (float)(y2*scale.y + yo), (float)(z1*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x2*scale.x + xo), (float)(y1*scale.y + yo), (float)(z1*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        // left face (a,d,h,e)
                        vertexBuilder.vertex(matrix4f, (float)(x1*scale.x + xo), (float)(y1*scale.y + yo), (float)(z1*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x1*scale.x + xo), (float)(y1*scale.y + yo), (float)(z2*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x1*scale.x + xo), (float)(y2*scale.y + yo), (float)(z2*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x1*scale.x + xo), (float)(y2*scale.y + yo), (float)(z1*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        break;
                    case SOUTH:     // positive Z
                        // back face (d,c,g,h)
                        vertexBuilder.vertex(matrix4f, (float)(x1*scale.x + xo), (float)(y1*scale.y + yo), (float)(z2*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x2*scale.x + xo), (float)(y1*scale.y + yo), (float)(z2*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x2*scale.x + xo), (float)(y2*scale.y + yo), (float)(z2*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x1*scale.x + xo), (float)(y2*scale.y + yo), (float)(z2*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        // right face (b,f,g,c)
                        vertexBuilder.vertex(matrix4f, (float)(x2*scale.x + xo), (float)(y1*scale.y + yo), (float)(z1*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x2*scale.x + xo), (float)(y2*scale.y + yo), (float)(z1*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x2*scale.x + xo), (float)(y2*scale.y + yo), (float)(z2*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x2*scale.x + xo), (float)(y1*scale.y + yo), (float)(z2*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        break;
                    case WEST:     // negative X
                        // for no adequately explored reason, the geometry for this generates in NORTH
                        // front face (a,e,f,b)
                        vertexBuilder.vertex(matrix4f, (float)(x1*scale.x + xo), (float)(y1*scale.y + yo), (float)(z1*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x1*scale.x + xo), (float)(y2*scale.y + yo), (float)(z1*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x2*scale.x + xo), (float)(y2*scale.y + yo), (float)(z1*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x2*scale.x + xo), (float)(y1*scale.y + yo), (float)(z1*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        // left face (a,d,h,e)
                        vertexBuilder.vertex(matrix4f, (float)(x1*scale.x + xo), (float)(y1*scale.y + yo), (float)(z1*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x1*scale.x + xo), (float)(y1*scale.y + yo), (float)(z2*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x1*scale.x + xo), (float)(y2*scale.y + yo), (float)(z2*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x1*scale.x + xo), (float)(y2*scale.y + yo), (float)(z1*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        break;
                    case EAST:      // positive X
                        // for no adequately explored reason, the geometry for this generates in SOUTH
                        // back face (d,c,g,h)
                        vertexBuilder.vertex(matrix4f, (float)(x1*scale.x + xo), (float)(y1*scale.y + yo), (float)(z2*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x2*scale.x + xo), (float)(y1*scale.y + yo), (float)(z2*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x2*scale.x + xo), (float)(y2*scale.y + yo), (float)(z2*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x1*scale.x + xo), (float)(y2*scale.y + yo), (float)(z2*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        // right face (b,f,g,c)
                        vertexBuilder.vertex(matrix4f, (float)(x2*scale.x + xo), (float)(y1*scale.y + yo), (float)(z1*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x2*scale.x + xo), (float)(y2*scale.y + yo), (float)(z1*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x2*scale.x + xo), (float)(y2*scale.y + yo), (float)(z2*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        vertexBuilder.vertex(matrix4f, (float)(x2*scale.x + xo), (float)(y1*scale.y + yo), (float)(z2*scale.z + zo)).color(red, green, blue, alpha).endVertex();
                        break;
                }
            });
        }

        *//*
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) ->
        {
            // front face (a,e,f,b)
            vertexBuilder.vertex(matrix4f, (float)x1, (float)y1, (float)z1).color(red, green, blue, alpha).endVertex();
            vertexBuilder.vertex(matrix4f, (float)x1, (float)y2, (float)z1).color(red, green, blue, alpha).endVertex();
            vertexBuilder.vertex(matrix4f, (float)x2, (float)y2, (float)z1).color(red, green, blue, alpha).endVertex();
            vertexBuilder.vertex(matrix4f, (float)x2, (float)y1, (float)z1).color(red, green, blue, alpha).endVertex();

            // back face (d,c,g,h)
            vertexBuilder.vertex(matrix4f, (float)x1, (float)y1, (float)z2).color(red, green, blue, alpha).endVertex();
            vertexBuilder.vertex(matrix4f, (float)x2, (float)y1, (float)z2).color(red, green, blue, alpha).endVertex();
            vertexBuilder.vertex(matrix4f, (float)x2, (float)y2, (float)z2).color(red, green, blue, alpha).endVertex();
            vertexBuilder.vertex(matrix4f, (float)x1, (float)y2, (float)z2).color(red, green, blue, alpha).endVertex();

            // bottom face (a,b,c,d)
            vertexBuilder.vertex(matrix4f, (float)x1, (float)y1, (float)z1).color(red, green, blue, alpha).endVertex();
            vertexBuilder.vertex(matrix4f, (float)x2, (float)y1, (float)z1).color(red, green, blue, alpha).endVertex();
            vertexBuilder.vertex(matrix4f, (float)x2, (float)y1, (float)z2).color(red, green, blue, alpha).endVertex();
            vertexBuilder.vertex(matrix4f, (float)x1, (float)y1, (float)z2).color(red, green, blue, alpha).endVertex();

            // top face (e,h,g,f)
            vertexBuilder.vertex(matrix4f, (float)x1, (float)y2, (float)z1).color(red, green, blue, alpha).endVertex();
            vertexBuilder.vertex(matrix4f, (float)x1, (float)y2, (float)z2).color(red, green, blue, alpha).endVertex();
            vertexBuilder.vertex(matrix4f, (float)x2, (float)y2, (float)z2).color(red, green, blue, alpha).endVertex();
            vertexBuilder.vertex(matrix4f, (float)x2, (float)y2, (float)z1).color(red, green, blue, alpha).endVertex();

            // left face (a,d,h,e)
            vertexBuilder.vertex(matrix4f, (float)x1, (float)y1, (float)z1).color(red, green, blue, alpha).endVertex();
            vertexBuilder.vertex(matrix4f, (float)x1, (float)y1, (float)z2).color(red, green, blue, alpha).endVertex();
            vertexBuilder.vertex(matrix4f, (float)x1, (float)y2, (float)z2).color(red, green, blue, alpha).endVertex();
            vertexBuilder.vertex(matrix4f, (float)x1, (float)y2, (float)z1).color(red, green, blue, alpha).endVertex();

            // right face (b,f,g,c)
            vertexBuilder.vertex(matrix4f, (float)x2, (float)y1, (float)z1).color(red, green, blue, alpha).endVertex();
            vertexBuilder.vertex(matrix4f, (float)x2, (float)y2, (float)z1).color(red, green, blue, alpha).endVertex();
            vertexBuilder.vertex(matrix4f, (float)x2, (float)y2, (float)z2).color(red, green, blue, alpha).endVertex();
            vertexBuilder.vertex(matrix4f, (float)x2, (float)y1, (float)z2).color(red, green, blue, alpha).endVertex();
        });
        *//*
    }
    */
}
