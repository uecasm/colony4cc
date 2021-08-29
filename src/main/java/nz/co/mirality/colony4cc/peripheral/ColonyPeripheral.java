package nz.co.mirality.colony4cc.peripheral;

import com.google.common.base.Functions;
import com.ldtteam.structurize.util.LanguageHandler;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.*;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.colony.managers.interfaces.IBuildingManager;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.Player;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.resolver.player.IPlayerRequestResolver;
import com.minecolonies.api.colony.requestsystem.resolver.retrying.IRetryingRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.colony.workorders.IWorkOrder;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.research.IGlobalResearch;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.research.ILocalResearch;
import com.minecolonies.api.research.ILocalResearchTree;
import com.minecolonies.api.research.effects.IResearchEffect;
import com.minecolonies.api.research.util.ResearchState;
import com.minecolonies.coremod.colony.Colony;
import com.minecolonies.coremod.colony.buildings.AbstractBuildingStructureBuilder;
import com.minecolonies.coremod.colony.buildings.utils.BuildingBuilderResource;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import nz.co.mirality.colony4cc.Colony4CC;
import nz.co.mirality.colony4cc.LuaConversion;
import nz.co.mirality.colony4cc.data.LuaDoc;
import nz.co.mirality.colony4cc.network.Colony4CCPacketHandler;
import nz.co.mirality.colony4cc.network.SAddBuildingOverlayPacket;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.minecolonies.api.util.constant.WindowConstants.KEY_TO_PERMISSIONS;
import static nz.co.mirality.colony4cc.Colony4CC.RGB_CHARGE;
import static nz.co.mirality.colony4cc.Constants.*;

public abstract class ColonyPeripheral implements IPeripheral {
    public abstract World getWorld();
    public abstract BlockPos getPos();
    public abstract boolean equals(@Nullable IPeripheral other);
    public abstract Object getTarget();
    @Nullable protected abstract IItemHandler getInventory(@Nullable Direction side);

    @Nonnull
    @Override
    public String getType() {
        return Colony4CC.PERIPHERAL_NAME;
    }

    @Nullable
    private IColony getColony() {
        final World world = this.getWorld();
        if (world == null) return null;

        final IMinecoloniesAPI api = IMinecoloniesAPI.getInstance();
        final IColonyManager colonies = api.getColonyManager();
        return colonies.getColonyByPosFromWorld(world, this.getPos());
    }

    protected boolean passedSecurityCheck = true;

    protected void securityCheck(Entity entity) {
        if (entity instanceof PlayerEntity) {
            final IColony colony = this.getColony();
            if (colony != null) {
                this.passedSecurityCheck = colony.getPermissions()
                        .hasPermission((PlayerEntity) entity, Action.ACCESS_HUTS);
                return;
            }
        }

        this.passedSecurityCheck = false;
    }

    @LuaFunction(mainThread = true)
    @LuaDoc(group = 1, order = 1)
    public final boolean isValid() {
        return this.passedSecurityCheck && getColony() != null;
    }

    @LuaFunction(mainThread = true)
    @LuaDoc(group = 1, order = 2, args = "table pos", returns = "boolean")
    public final Object[] isWithin(@Nullable Map<?, ?> pos) {
        final Optional<BlockPos> p = toBlockPos(pos);
        if (!p.isPresent()) {
            return new Object[] { null, "expected coordinates" };
        }

        final IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck) {
            return new Object[] { null, "no colony" };
        }

        return new Object[] { colony.isCoordInColony(this.getWorld(), p.get()) };
    }

    @LuaFunction(mainThread = true)
    @LuaDoc(group = 2, order = 1)
    public final Object[] getInfo() {
        final IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck) {
            return new Object[] { null, "no colony" };
        }

        final Map<Object, Object> data = new HashMap<>();
        protectPut("getInfo", data, "id", colony::getID);
        protectPut("getInfo", data, "name", colony::getName);
        protectPut("getInfo", data, "active", colony::isActive);
        protectPut("getInfo", data, "location", () -> GlobalPos.of(colony.getDimension(), colony.getCenter()));
        protectPut("getInfo", data, "style", colony::getStyle);
        protectPut("getInfo", data, "happiness", colony::getOverallHappiness);
        protectPut("getInfo", data, "raid", colony::isColonyUnderAttack);
        protectPut("getInfo", data, "citizens", () -> colony.getCitizenManager().getCurrentCitizenCount());
        protectPut("getInfo", data, "maxCitizens", () -> colony.getCitizenManager().getMaxCitizens());
        return new Object[] { LuaConversion.convert(data) };
    }

    @LuaFunction(mainThread = true)
    @LuaDoc(group = 3, order = 1)
    public final Object[] getBuildings() {
        final IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck) {
            return new Object[] { null, "no colony" };
        }

        final IBuildingManager manager = colony.getBuildingManager();
        final List<Object> buildingData = new ArrayList<>();
        for (final Map.Entry<BlockPos, IBuilding> entry : manager.getBuildings().entrySet()) {
            final IBuilding building = entry.getValue();

            final Map<Object, Object> footprintData = new HashMap<>();
            protectPut("getBuildings.footprint", footprintData, "corner1", () -> building.getCorners().getA());
            protectPut("getBuildings.footprint", footprintData, "corner2", () -> building.getCorners().getB());
            protectPut("getBuildings.footprint", footprintData, "rotation", building::getRotation);
            protectPut("getBuildings.footprint", footprintData, "mirror", building::isMirrored);

            final List<Object> citizensData = new ArrayList<>();
            for (final ICitizenData citizen : building.getAssignedCitizen()) {
                final Map<Object, Object> citizenData = new HashMap<>();
                protectPut("getBuildings.citizen", citizenData, "id", citizen::getId);
                protectPut("getBuildings.citizen", citizenData, "name", citizen::getName);
                citizensData.add(citizenData);
            }
            for (int i = citizensData.size(); i < building.getMaxInhabitants(); ++i) {
                citizensData.add(new HashMap<>());
            }

            final Map<Object, Object> data = new HashMap<>();
            data.put("location", entry.getKey());
            protectPut("getBuildings", data, "type", building::getSchematicName);
            protectPut("getBuildings", data, "style", building::getStyle);
            protectPut("getBuildings", data, "level", building::getBuildingLevel);
            protectPut("getBuildings", data, "maxLevel", building::getMaxBuildingLevel);
            protectPut("getBuildings", data, "claimRadius", () -> building.getClaimRadius(building.getBuildingLevel()));
            protectPut("getBuildings", data, "name", building::getCustomBuildingName);
            protectPut("getBuildings", data, "built", building::isBuilt);
            protectPut("getBuildings", data, "wip", building::hasWorkOrder);
            protectPut("getBuildings", data, "priority", building::getPickUpPriority);
            data.put("footprint", footprintData);
            data.put("citizens", citizensData);
            protectPut("getBuildings", data, "storageBlocks", () -> building.getContainers().size());
            protectPut("getBuildings", data, "storageSlots", () -> calculateStorageSlots(building));
            protectPut("getBuildings", data, "guarded", () -> manager.hasGuardBuildingNear(building));
            buildingData.add(data);
        }

        return new Object[] { LuaConversion.convert(buildingData) };
    }

    private static int calculateStorageSlots(@Nonnull final IBuilding building) {
        final LazyOptional<IItemHandler> capability = building.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
        final IItemHandler handler = capability.resolve().orElse(null);
        if (handler != null) {
            return handler.getSlots();
        }
        return 0;
    }

    @LuaFunction(mainThread = true)
    @LuaDoc(group = 4, order = 1)
    public final Object[] getCitizens() {
        final IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck) {
            return new Object[] { null, "no colony" };
        }

        final List<Object> citizensData = new ArrayList<>();
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
            final Map<Object, Object> data = new HashMap<>();
            protectPut("getCitizens", data, "id", citizen::getId);
            protectPut("getCitizens", data, "name", citizen::getName);
            protectPut("getCitizens", data, "location", citizen::getLastPosition);
            protectPut("getCitizens", data, "bed", () -> citizen.getHomeBuilding() == null ? null : citizen.getBedPos());
            protectPut("getCitizens", data, "job", () -> JobInfo(citizen.getJob()));
            protectPut("getCitizens", data, "home", () -> BuildingInfo(citizen.getHomeBuilding()));
            protectPut("getCitizens", data, "work", () -> BuildingInfo(citizen.getWorkBuilding()));
            protectPut("getCitizens", data, "status", () -> StatusInfo(citizen.getStatus()));
            protectPut("getCitizens", data, "age", () -> citizen.isChild() ? "child" : "adult");
            protectPut("getCitizens", data, "sex", () -> citizen.isFemale() ? "female" : "male");
            protectPut("getCitizens", data, "saturation", citizen::getSaturation);
            protectPut("getCitizens", data, "happiness", () -> citizen.getCitizenHappinessHandler().getHappiness(colony));
            citizen.getEntity().ifPresent(entity -> {
                data.put("health", entity.getHealth());
                data.put("max_health", entity.getAttributeValue(Attributes.MAX_HEALTH));
                data.put("armor", entity.getAttributeValue(Attributes.ARMOR));
                data.put("toughness", entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
            });

            try {
                final Map<Object, Object> skillsData = new HashMap<>();
                for (final Map.Entry<Skill, Tuple<Integer, Double>> entry :
                        citizen.getCitizenSkillHandler().getSkills().entrySet())
                {
                    final Map<Object, Object> skillData = new HashMap<>();
                    skillData.put("level", entry.getValue().getA());
                    skillData.put("xp", entry.getValue().getB());
                    skillsData.put(entry.getKey().name(), skillData);
                }
                data.put("skills", skillsData);
                //data.put("jobSkill", citizen.getJobModifier());
            } catch (Exception ex) {
                Colony4CC.LOGGER.error(String.format("Error reading skills from citizen %d in colony %d: %s",
                        citizen.getId(), colony.getID(), ex.getMessage()), ex);
            }

            citizensData.add(data);
        }

        return new Object[] { LuaConversion.convert(citizensData) };
    }

    @LuaFunction(mainThread = true)
    @LuaDoc(group = 4, order = 2)
    public final Object[] getVisitors() {
        final IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck) {
            return new Object[] { null, "no colony" };
        }

        final List<Object> visitorsData = new ArrayList<>();
        for (final ICivilianData civilian : colony.getVisitorManager().getCivilianDataMap().values()) {
            if (!(civilian instanceof IVisitorData)) continue;
            final IVisitorData visitor = (IVisitorData) civilian;
            final Map<Object, Object> data = new HashMap<>();
            protectPut("getVisitors", data, "id", visitor::getId);
            protectPut("getVisitors", data, "name", visitor::getName);
            protectPut("getVisitors", data, "location", visitor::getLastPosition);
            protectPut("getVisitors", data, "chair", visitor::getSittingPosition);
            protectPut("getVisitors", data, "age", () -> visitor.isChild() ? "child" : "adult");
            protectPut("getVisitors", data, "sex", () -> visitor.isFemale() ? "female" : "male");
            protectPut("getVisitors", data, "saturation", visitor::getSaturation);
            protectPut("getVisitors", data, "happiness", () -> visitor.getCitizenHappinessHandler().getHappiness(colony));
            protectPut("getVisitors", data, "cost", visitor::getRecruitCost);

            try {
                final Map<Object, Object> skillsData = new HashMap<>();
                for (final Map.Entry<Skill, Tuple<Integer, Double>> entry :
                        visitor.getCitizenSkillHandler().getSkills().entrySet()) {
                    final Map<Object, Object> skillData = new HashMap<>();
                    skillData.put("level", entry.getValue().getA());
                    skillData.put("xp", entry.getValue().getB());
                    skillsData.put(entry.getKey().name(), skillData);
                }
                data.put("skills", skillsData);
                //data.put("jobSkill", citizen.getJobModifier());
            } catch (Exception ex) {
                Colony4CC.LOGGER.error(String.format("Error reading skills from visitor %d in colony %d: %s",
                        visitor.getId(), colony.getID(), ex.getMessage()), ex);
            }

            visitorsData.add(data);
        }

        return new Object[] { LuaConversion.convert(visitorsData) };
    }

    @LuaFunction(mainThread = true)
    @LuaDoc(group = 4, order = 3)
    public final Object[] getPlayers()
    {
        final IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck)
        {
            return new Object[]{null, "no colony"};
        }

        final IPermissions permissions = colony.getPermissions();
        final Set<UUID> officersPresent = colony.getMessagePlayerEntities().stream()
            .map(Entity::getUUID)
            .collect(Collectors.toSet());

        Map<UUID, PlayerEntity> visitors;
        try {
            visitors = ((Colony) colony).getVisitingPlayers().stream()
                .collect(Collectors.toMap(Entity::getUUID, Functions.identity()));
        } catch (Exception ex) {
            visitors = new HashMap<>();
        }

        final List<Map<Object, Object>> players = new ArrayList<>();
        for (final Player player : permissions.getPlayers().values())
        {
            final boolean isVisitor = visitors.containsKey(player.getID());
            visitors.remove(player.getID());

            final Map<Object, Object> playerData = new HashMap<>();
            protectPut("getPlayers.players", playerData, "name", player::getName);
            protectPut("getPlayers.players", playerData, "rank", () -> player.getRank().getName());
            if (officersPresent.contains(player.getID())) {
                playerData.put("present", true);
            } else {
                playerData.put("present", isVisitor);
            }
            players.add(playerData);
        }
        for (final PlayerEntity player : visitors.values())
        {
            final Map<Object, Object> playerData = new HashMap<>();
            protectPut("getPlayers.players", playerData, "name", player::getName);
            playerData.put("present", true);
            players.add(playerData);
        }

        final List<Map<Object, Object>> ranks = new ArrayList<>();
        for (final Rank rank : permissions.getRanks().values())
        {
            final Map<Object, Object> rankData = new HashMap<>();
            protectPut("getPlayers.ranks", rankData, "name", rank::getName);
            protectPut("getPlayers.ranks", rankData, "hostile", rank::isHostile);
            final List<Object> permission = new ArrayList<>();
            for (final Action action : Action.values())
            {
                if (permissions.hasPermission(rank, action)) {
                    permission.add(ActionInfo(action));
                }
            }
            rankData.put("permissions", permission);
            ranks.add(rankData);
        }

        final Map<Object, Object> data = new HashMap<>();
        data.put("players", players);
        data.put("ranks", ranks);

        return new Object[] { LuaConversion.convert(data) };
    }

    @LuaFunction(mainThread = true)
    @LuaDoc(group = 5, order = 1)
    public final Object[] getWorkOrders() {
        final IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck) {
            return new Object[] { null, "no colony" };
        }

        final List<Object> worksData = new ArrayList<>();
        for (final IWorkOrder workOrder : colony.getWorkManager().getWorkOrders().values()) {
            // the API does not currently provide sufficient methods to usefully decode
            // a work order, so we just render its raw property data for now.
            CompoundNBT nbt = new CompoundNBT();
            workOrder.write(nbt);
            worksData.add(nbt);
        }

        return new Object[] { LuaConversion.convert(worksData) };
    }

    @LuaFunction(mainThread = true)
    @LuaDoc(group = 5, order = 2, args = "number id")
    public final Object[] getWorkOrderResources(int id) {
        final IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck) {
            return new Object[] { null, "no colony" };
        }

        final IWorkOrder workOrder = colony.getWorkManager().getWorkOrder(id);
        if (workOrder == null) {
            return new Object[] { null, "no work order" };
        }

        if (!workOrder.isClaimed()) {
            return new Object[] { null, "not claimed" };
        }

        return getBuilderResources(workOrder.getClaimedBy());
    }

    @LuaFunction(mainThread = true)
    @LuaDoc(group = 5, order = 3, args = "table pos")
    public final Object[] getBuilderResources(@Nullable Map<?, ?> pos) {
        return toBlockPos(pos)
                .map(this::getBuilderResources)
                .orElseGet(() -> new Object[] { null, "expected coordinates" });
    }

    private Object[] getBuilderResources(BlockPos pos) {
        final IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck) {
            return new Object[] { null, "no colony" };
        }

        final IBuilding building = colony.getBuildingManager().getBuilding(pos);
        if (!(building instanceof AbstractBuildingStructureBuilder)) {
            return new Object[] { null, "not builder" };
        }

        // the builder only fully refreshes its resource list when serialized to View data...
        final PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        building.serializeToView(buffer);
        buffer.release();

        // ... but we can't actually create a View here since we might be server-only.
        // fortunately we can now query the data from the server-side building after the above.
        final List<BuildingBuilderResource> resources = new ArrayList<>(((AbstractBuildingStructureBuilder) building).getNeededResources().values());
        resources.sort(new BuildingBuilderResource.ResourceComparator());

        final List<Object> result = new ArrayList<>();
        for (BuildingBuilderResource resource : resources) {
            final Map<Object, Object> data = new HashMap<>();
            // copying ensures amount from player is zero
            final BuildingBuilderResource resourceCopy = new BuildingBuilderResource(resource.getItemStack(), resource.getAmount(), resource.getAvailable());
            resourceCopy.setAmountInDelivery(resource.getAmountInDelivery());

            final ItemStack stack = resourceCopy.getItemStack().copy();
            stack.setCount(resourceCopy.getAmount());
            data.put("item", stack);
            protectPut("getBuilderResources", data, "available", resourceCopy::getAvailable);
            protectPut("getBuilderResources", data, "delivering", resourceCopy::getAmountInDelivery);
            protectPut("getBuilderResources", data, "status", () -> resourceCopy.getAvailabilityStatus().toString());
            result.add(data);
        }

        return new Object[] { LuaConversion.convert(result) };
    }

    @LuaFunction(mainThread = true)
    @LuaDoc(group = 5, order = 5)
    public final Object[] getRequests() {
        final IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck) {
            return new Object[] { null, "no colony" };
        }

        final IRequestManager manager = colony.getRequestManager();
        if (manager == null) {
            return new Object[] { null, "no request system" };
        }

        final IPlayerRequestResolver player = manager.getPlayerResolver();
        final IRetryingRequestResolver retrying = manager.getRetryingRequestResolver();

        final Set<IToken<?>> tokens = new HashSet<>();
        tokens.addAll(player.getAllAssignedRequests());
        tokens.addAll(retrying.getAllAssignedRequests());

        final List<IRequest<?>> requests = tokens.stream().map(manager::getRequestForToken)
                .filter(r -> r != null && r.getRequest() instanceof IDeliverable)
                .distinct().collect(Collectors.toList());

        final List<Object> result = new ArrayList<>();
        for (final IRequest<?> request : requests) {
            final IDeliverable deliverable = (IDeliverable) request.getRequest();
            final Map<Object, Object> data = new HashMap<>();
            //protectPut("getRequests", data, "id", () -> request.getId().getIdentifier().toString());
            protectPut("getRequests", data, "name", () -> TextFormatting.stripFormatting(request.getShortDisplayString().getString()));
            protectPut("getRequests", data, "desc", () -> TextFormatting.stripFormatting(request.getLongDisplayString().getString()));
            protectPut("getRequests", data, "state", () -> request.getState().toString());
            protectPut("getRequests", data, "count", deliverable::getCount);
            protectPut("getRequests", data, "minCount", deliverable::getMinimumCount);
            protectPut("getRequests", data, "items", request::getDisplayStacks);
            protectPut("getRequests", data, "target", () -> request.getRequester().getRequesterDisplayName(manager, request).getString());
            result.add(data);
        }

        return new Object[] { LuaConversion.convert(result) };
    }

    @LuaFunction(mainThread = true)
    @LuaDoc(group = 6, order = 1)
    public final Object[] getResearch() {
        final IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck) {
            return new Object[] { null, "no colony" };
        }

        final IGlobalResearchTree tree = IGlobalResearchTree.getInstance();
        final ILocalResearchTree colonyTree = colony.getResearchManager().getResearchTree();

        final Map<Object, Object> result = new HashMap<>();
        for (final ResourceLocation branch : tree.getBranches()) {
            result.put(branch, getResearch(branch, tree.getPrimaryResearch(branch), tree, colonyTree));
        }

        return new Object[] { LuaConversion.convert(result) };
    }

    @LuaFunction(mainThread = true)
    @LuaDoc(group = 7, order = 1, args = "number id, [string/number direction = \\\"up\\\"]", returns = "boolean")
    public final Object[] highlightWorker(final IArguments args) throws LuaException {
        final IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck) {
            return new Object[] { null, "no colony" };
        }

        final int workerId = args.getInt(0);
        final Direction side = getDirection(args, 1);

        final ICitizenData civilian = colony.getCitizenManager().getCivilian(workerId);
        if (civilian == null) {
            return new Object[] { null, "no worker" };
        }

        final AbstractEntityCitizen entity = civilian.getEntity().orElse(null);
        if (entity == null) {
            return new Object[] { null, "worker unloaded" };
        }

        final int costMultiplier = Colony4CC.CONFIG.getHighlightWorkerCostMultiplier();
        if (costMultiplier > 0 && !hasResearch(colony, RESEARCH_FREE_WORKER_HIGHLIGHT)) {
            final IItemHandler handler = getInventory(side);
            if (handler == null) {
                return new Object[] { null, "no inventory" };
            }

            if (!consumeFuel(handler, ModItems.scrollHighLight, costMultiplier)) {
                return new Object[] { null, "no fuel" };
            }
        }

        entity.addEffect(new EffectInstance(Effects.GLOWING, TICKS_PER_MINUTE * 2));
        entity.addEffect(new EffectInstance(Effects.MOVEMENT_SPEED, TICKS_PER_MINUTE * 2));

        return new Object[] { true };
    }

    @LuaFunction(mainThread = true)
    @LuaDoc(group = 7, order = 2, args = "table pos, [table options], [string/number direction = \\\"up\\\"]", returns = "boolean")
    public final Object[] highlightBuilding(final IArguments args) throws LuaException {
        final IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck) {
            return new Object[] { null, "no colony" };
        }

        final BlockPos pos = toBlockPos(args.getTable(0)).orElse(null);
        final Map<Object, Object> options = (Map<Object, Object>) args.optTable(1, new HashMap<>());
        final Direction side = getDirection(args, 2);

        final IBuilding building = pos == null ? null : colony.getBuildingManager().getBuilding(pos);
        if (building == null) {
            return new Object[] { null, "no building" };
        }

        final List<SAddBuildingOverlayPacket.Overlay> overlays = new ArrayList<>();
        if (options.containsKey("hut")) {
            final boolean fill = !options.get("hut").equals("frame");
            final MutableBoundingBox box = new MutableBoundingBox(pos, pos);
            overlays.add(new SAddBuildingOverlayPacket.Overlay(box, 0xFF0000, fill, true));
        }
        if (options.containsKey("footprint")) {
            final boolean fill = !options.get("footprint").equals("frame");
            final Tuple<BlockPos, BlockPos> corners = building.getCorners();
            final MutableBoundingBox box = new MutableBoundingBox(corners.getA(), corners.getB());
            overlays.add(new SAddBuildingOverlayPacket.Overlay(box, 0x0000FF, fill, false));
        }
        if (options.containsKey("claim")) {
            final boolean fill = !options.get("claim").equals("frame");
            final int claimRadius = building.getClaimRadius(building.getBuildingLevel());
            final MutableBoundingBox box = createChunkRadiusBox(pos, claimRadius);
            overlays.add(new SAddBuildingOverlayPacket.Overlay(box, 0x00FF00, fill, false));
        }

        ServerPlayerEntity player = null;
        if (options.containsKey("player")) {
            if (options.get("player") instanceof String) {
                player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayerByName((String) options.get("player"));
            }
            if (player == null) {
                return new Object[] { null, "player not found" };
            }
        }

        final int cost = overlays.size();
        final int costMultiplier = Colony4CC.CONFIG.getHighlightBuildingCostMultiplier();
        if (cost > 0 && costMultiplier > 0 && !hasResearch(colony, RESEARCH_FREE_BUILDING_HIGHLIGHT)) {
            final IItemHandler handler = getInventory(side);
            if (handler == null) {
                return new Object[] { null, "no inventory" };
            }

            if (!consumeFuel(handler, RGB_CHARGE.get(), cost * costMultiplier)) {
                return new Object[] { null, "no fuel" };
            }
        }

        final SAddBuildingOverlayPacket packet = new SAddBuildingOverlayPacket(colony.getWorld().dimension().location(), pos, overlays);
        if (player != null) {
            final ServerPlayerEntity finalPlayer = player;
            Colony4CCPacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> finalPlayer), packet);
        } else {
            Colony4CCPacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), packet);
        }

        return new Object[] { true };
    }

    @Nonnull
    private List<Object> getResearch(@Nonnull ResourceLocation branch, @Nullable List<ResourceLocation> names,
                                     @Nonnull IGlobalResearchTree tree, @Nonnull ILocalResearchTree colonyTree) {
        final List<Object> result = new ArrayList<>();
        if (names != null && this.passedSecurityCheck) {
            for (final ResourceLocation name : names) {
                final IGlobalResearch research = tree.getResearch(branch, name);
                if (research == null) continue;
                final ILocalResearch colonyResearch = colonyTree.getResearch(branch, name);

                final Map<Object, Object> data = new HashMap<>();
                data.put("id", name);
                protectPut("getResearch", data, "name", research::getName);
                protectPut("getResearch", data, "effects",
                        () -> research.getEffects().stream()
                            .map(IResearchEffect::getDesc).collect(Collectors.toList()));
                protectPut("getResearch", data, "status",
                        () -> (colonyResearch == null ? ResearchState.NOT_STARTED : colonyResearch.getState()).toString());

                final List<Object> children = getResearch(branch, research.getChildren(), tree, colonyTree);
                if (!children.isEmpty()) {
                    data.put("children", children);
                }
                result.add(data);
            }
        }
        return result;
    }

    private boolean hasResearch(@Nonnull final IColony colony,
                                @Nonnull final ResourceLocation effectId) {
        return colony.getResearchManager().getResearchEffects().getEffectStrength(effectId) > 0;
    }

    private static Optional<BlockPos> toBlockPos(@Nullable Map<?, ?> table) {
        if (table == null || !table.containsKey("x") || !table.containsKey("y") || !table.containsKey("z")) {
            return Optional.empty();
        }

        final int x = ((Number) table.get("x")).intValue();
        final int y = ((Number) table.get("y")).intValue();
        final int z = ((Number) table.get("z")).intValue();

        return Optional.of(new BlockPos(x, y, z));
    }

    private static MutableBoundingBox createChunkRadiusBox(final BlockPos pos, final int chunkRadius) {
        final int blockRadius = chunkRadius * 16;
        final ChunkPos chunk = new ChunkPos(pos);
        final int x1 = chunk.getMinBlockX() - blockRadius;
        final int y1 = (pos.getY() & ~15) - blockRadius;
        final int z1 = chunk.getMinBlockZ() - blockRadius;
        final int x2 = chunk.getMaxBlockX() + blockRadius;
        final int y2 = (pos.getY() | 15) + blockRadius;
        final int z2 = chunk.getMaxBlockZ() + blockRadius;
        return new MutableBoundingBox(x1, y1, z1, x2, y2, z2);
    }

    @Nullable
    private static Direction getDirection(@Nonnull final IArguments args, final int index) throws LuaException {
        final Object directionArg = args.get(index);
        if (directionArg instanceof String) {
            return args.getEnum(index, Direction.class);
        } else if (directionArg != null) {
            return Direction.from3DDataValue(args.getInt(index));
        }
        return null;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected boolean consumeFuel(@Nonnull final IItemHandler handler,
                                  @Nonnull final Item fuel,
                                  final int count) {
        for (int slot = 0, max = handler.getSlots(); slot < max; ++slot)
        {
            final ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (!stack.getItem().equals(fuel)) continue;

            final ItemStack result = handler.extractItem(slot, count, false);
            if (result.isEmpty()) continue;
            if (result.getCount() < count) {
                handler.insertItem(slot, result, false);
                continue;
            }

            return true;
        }
        return false;
    }

    private static Object JobInfo(IJob<?> job) {
        if (job == null) return null;
        return LanguageHandler.translateKey(job.getName());
    }

    private static Object BuildingInfo(@Nullable final IBuilding building) {
        if (building == null) return null;

        final Map<Object, Object> data = new HashMap<>();
        protectPut("BuildingInfo", data, "location", building::getPosition);
        protectPut("BuildingInfo", data, "type", building::getSchematicName);
        protectPut("BuildingInfo", data, "level", building::getBuildingLevel);
        return data;
    }

    private static Object StatusInfo(@Nullable final VisibleCitizenStatus status) {
        if (status == null) return "Idle";
        return status.getTranslatedText();
    }

    private static Object ActionInfo(@Nonnull final Action action) {
        final String name = action.toString().toLowerCase(Locale.US);
        final String desc = LanguageHandler.format(KEY_TO_PERMISSIONS + name);
        return desc.contains(KEY_TO_PERMISSIONS) ? name : desc;
    }

    private static void protectPut(@Nonnull final String context,
                                   @Nonnull final Map<Object, Object> data,
                                   @Nonnull final String key,
                                   @Nonnull final Supplier<Object> valueProvider)
    {
        try {
            data.put(key, valueProvider.get());
        } catch (Exception ex) {
            Colony4CC.LOGGER.error("Error generating " + key + " in " + context, ex);
        }
    }
}
