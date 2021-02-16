package nz.co.mirality.colony4cc.peripheral;

import com.ldtteam.structurize.util.LanguageHandler;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.colony.managers.interfaces.IBuildingManager;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.resolver.player.IPlayerRequestResolver;
import com.minecolonies.api.colony.requestsystem.resolver.retrying.IRetryingRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.colony.workorders.IWorkOrder;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.research.IGlobalResearch;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.research.ILocalResearch;
import com.minecolonies.api.research.ILocalResearchTree;
import com.minecolonies.api.research.util.ResearchState;
import com.minecolonies.coremod.colony.buildings.utils.BuildingBuilderResource;
import com.minecolonies.coremod.colony.buildings.workerbuildings.BuildingBuilder;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import nz.co.mirality.colony4cc.Colony4CC;
import nz.co.mirality.colony4cc.LuaConversion;
import nz.co.mirality.colony4cc.data.LuaDoc;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public abstract class ColonyPeripheral implements IPeripheral {
    public abstract World getWorld();
    public abstract BlockPos getPos();
    public abstract boolean equals(@Nullable IPeripheral other);
    public abstract Object getTarget();

    @Nonnull
    @Override
    public String getType() {
        return Colony4CC.PERIPHERAL_NAME;
    }

    private IColony getColony() {
        World world = this.getWorld();
        if (world == null) return null;

        IMinecoloniesAPI api = IMinecoloniesAPI.getInstance();
        IColonyManager colonies = api.getColonyManager();
        return colonies.getColonyByPosFromWorld(world, this.getPos());
    }

    protected boolean passedSecurityCheck = true;

    protected void securityCheck(Entity entity) {
        if (entity instanceof PlayerEntity) {
            IColony colony = this.getColony();
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
        Optional<BlockPos> p = toBlockPos(pos);
        if (!p.isPresent()) {
            return new Object[] { null, "expected coordinates" };
        }

        IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck) {
            return new Object[] { null, "no colony" };
        }

        return new Object[] { colony.isCoordInColony(this.getWorld(), p.get()) };
    }

    @LuaFunction(mainThread = true)
    @LuaDoc(group = 2, order = 1)
    public final Object[] getInfo() {
        IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck) {
            return new Object[] { null, "no colony" };
        }

        HashMap<Object, Object> data = new HashMap<>();
        data.put("id", colony.getID());
        data.put("name", colony.getName());
        data.put("active", colony.isActive());
        data.put("location", GlobalPos.getPosition(colony.getDimension(), colony.getCenter()));
        data.put("style", colony.getStyle());
        data.put("happiness", colony.getOverallHappiness());
        data.put("mourning", colony.isMourning());
        data.put("raid", colony.isColonyUnderAttack());
        data.put("citizens", colony.getCitizenManager().getCurrentCitizenCount());
        data.put("maxCitizens", colony.getCitizenManager().getMaxCitizens());
        return new Object[] { LuaConversion.convert(data) };
    }

    @LuaFunction(mainThread = true)
    @LuaDoc(group = 3, order = 1)
    public final Object[] getBuildings() {
        IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck) {
            return new Object[] { null, "no colony" };
        }

        IBuildingManager manager = colony.getBuildingManager();
        List<Object> buildingData = new ArrayList<>();
        for (Map.Entry<BlockPos, IBuilding> entry : manager.getBuildings().entrySet()) {
            IBuilding building = entry.getValue();

            Map<Object, Object> footprintData = new HashMap<>();
            footprintData.put("corner1", building.getCorners().getA());
            footprintData.put("corner2", building.getCorners().getB());
            footprintData.put("rotation", building.getRotation());
            footprintData.put("mirror", building.isMirrored());

            List<Object> citizensData = new ArrayList<>();
            for (ICitizenData citizen : building.getAssignedCitizen()) {
                Map<Object, Object> citizenData = new HashMap<>();
                citizenData.put("id", citizen.getId());
                citizenData.put("name", citizen.getName());
                citizensData.add(citizenData);
            }
            for (int i = citizensData.size(); i < building.getMaxInhabitants(); ++i) {
                citizensData.add(new HashMap<>());
            }

            HashMap<Object, Object> data = new HashMap<>();
            data.put("location", entry.getKey());
            data.put("type", building.getSchematicName());
            data.put("style", building.getStyle());
            data.put("level", building.getBuildingLevel());
            data.put("maxLevel", building.getMaxBuildingLevel());
            data.put("name", building.getCustomBuildingName());
            data.put("built", building.isBuilt());
            data.put("wip", building.hasWorkOrder());
            data.put("priority", building.getPickUpPriority());
            data.put("footprint", footprintData);
            data.put("citizens", citizensData);
            data.put("storageBlocks", building.getContainers().size());
            data.put("storageSlots", calculateStorageSlots(building));
            data.put("guarded", manager.hasGuardBuildingNear(building));
            buildingData.add(data);
        }

        return new Object[] { LuaConversion.convert(buildingData) };
    }

    private static int calculateStorageSlots(IBuilding building) {
        LazyOptional<IItemHandler> capability = building.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
        IItemHandler handler = capability.resolve().orElse(null);
        if (handler != null) {
            return handler.getSlots();
        }
        return 0;
    }

    @LuaFunction(mainThread = true)
    @LuaDoc(group = 4, order = 1)
    public final Object[] getCitizens() {
        IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck) {
            return new Object[] { null, "no colony" };
        }

        List<Object> citizensData = new ArrayList<>();
        for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
            Map<Object, Object> data = new HashMap<>();
            data.put("id", citizen.getId());
            data.put("name", citizen.getName());
            data.put("location", citizen.getLastPosition());
            data.put("bed", citizen.getHomeBuilding() == null ? null : citizen.getBedPos());
            data.put("job", JobInfo(citizen.getJob()));
            data.put("home", BuildingInfo(citizen.getHomeBuilding()));
            data.put("work", BuildingInfo(citizen.getWorkBuilding()));
            data.put("status", StatusInfo(citizen.getStatus()));
            data.put("age", citizen.isChild() ? "child" : "adult");
            data.put("sex", citizen.isFemale() ? "female" : "male");
            data.put("saturation", citizen.getSaturation());
            data.put("happiness", citizen.getCitizenHappinessHandler().getHappiness(colony));

            Map<Object, Object> skillsData = new HashMap<>();
            for (Map.Entry<Skill, Tuple<Integer, Double>> entry :
                    citizen.getCitizenSkillHandler().getSkills().entrySet()) {
                Map<Object, Object> skillData = new HashMap<>();
                skillData.put("level", entry.getValue().getA());
                skillData.put("xp", entry.getValue().getB());
                skillsData.put(entry.getKey().name(), skillData);
            }
            data.put("skills", skillsData);
            //data.put("jobSkill", citizen.getJobModifier());

            citizensData.add(data);
        }

        return new Object[] { LuaConversion.convert(citizensData) };
    }

    @LuaFunction(mainThread = true)
    @LuaDoc(group = 5, order = 1)
    public final Object[] getWorkOrders() {
        IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck) {
            return new Object[] { null, "no colony" };
        }

        List<Object> worksData = new ArrayList<>();
        for (IWorkOrder workOrder : colony.getWorkManager().getWorkOrders().values()) {
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
        IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck) {
            return new Object[] { null, "no colony" };
        }

        IWorkOrder workOrder = colony.getWorkManager().getWorkOrder(id);
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
        // the builder only fully refreshes its resource list when accessed via View...
        // similarly, this information is not really available via the pure API.
        IBuildingView buildingView = IMinecoloniesAPI.getInstance().getColonyManager()
                .getBuildingView(this.getWorld().getDimensionKey(), pos);
        if (!(buildingView instanceof BuildingBuilder.View)) {
            return new Object[] { null, "not builder" };
        }
        IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck || buildingView.getColony().getID() != colony.getID()) {
            return new Object[] { null, "wrong colony" };
        }

        BuildingBuilder.View builder = (BuildingBuilder.View) buildingView;
        List<Object> result = new ArrayList<>();
        for (BuildingBuilderResource resource : builder.getResources().values()) {
            Map<Object, Object> data = new HashMap<>();
            // copying ensures amount from player is zero
            BuildingBuilderResource resourceCopy = new BuildingBuilderResource(resource.getItemStack(), resource.getAmount(), resource.getAvailable());
            resourceCopy.setAmountInDelivery(resource.getAmountInDelivery());

            ItemStack stack = resourceCopy.getItemStack().copy();
            stack.setCount(resourceCopy.getAmount());
            data.put("item", resourceCopy.getItemStack());
            data.put("available", resourceCopy.getAvailable());
            data.put("delivering", resourceCopy.getAmountInDelivery());
            data.put("status", resourceCopy.getAvailabilityStatus().toString());
            result.add(data);
        }

        return new Object[] { LuaConversion.convert(result) };
    }

    @LuaFunction(mainThread = true)
    @LuaDoc(group = 5, order = 5)
    public final Object[] getRequests() {
        IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck) {
            return new Object[] { null, "no colony" };
        }

        IRequestManager manager = colony.getRequestManager();
        if (manager == null) {
            return new Object[] { null, "no request system" };
        }

        IPlayerRequestResolver player = manager.getPlayerResolver();
        IRetryingRequestResolver retrying = manager.getRetryingRequestResolver();

        Set<IToken<?>> tokens = new HashSet<>();
        tokens.addAll(player.getAllAssignedRequests());
        tokens.addAll(retrying.getAllAssignedRequests());

        List<IRequest<?>> requests = tokens.stream().map(manager::getRequestForToken)
                .filter(r -> r != null && r.getRequest() instanceof IDeliverable)
                .distinct().collect(Collectors.toList());

        List<Object> result = new ArrayList<>();
        for (IRequest<?> request : requests) {
            IDeliverable deliverable = (IDeliverable) request.getRequest();
            Map<Object, Object> data = new HashMap<>();
            //data.put("id", request.getId().getIdentifier().toString());
            data.put("name", request.getShortDisplayString().getString());
            data.put("state", request.getState().toString());
            data.put("count", deliverable.getCount());
            data.put("minCount", deliverable.getMinimumCount());
            data.put("items", request.getDisplayStacks());
            data.put("target", request.getRequester().getRequesterDisplayName(manager, request).getString());
            result.add(data);
        }

        return new Object[] { LuaConversion.convert(result) };
    }

    @LuaFunction(mainThread = true)
    @LuaDoc(group = 6, order = 1)
    public final Object[] getResearch() {
        IColony colony = getColony();
        if (colony == null || !this.passedSecurityCheck) {
            return new Object[] { null, "no colony" };
        }

        IGlobalResearchTree tree = IGlobalResearchTree.getInstance();
        ILocalResearchTree colonyTree = colony.getResearchManager().getResearchTree();

        Map<Object, Object> result = new HashMap<>();
        for (String branch : tree.getBranches()) {
            result.put(branch, getResearch(branch, tree.getPrimaryResearch(branch), tree, colonyTree));
        }

        return new Object[] { LuaConversion.convert(result) };
    }

    @Nonnull
    private List<Object> getResearch(@Nonnull String branch, @Nullable List<String> names,
                                     @Nonnull IGlobalResearchTree tree, @Nonnull ILocalResearchTree colonyTree) {
        List<Object> result = new ArrayList<>();
        if (names != null || !this.passedSecurityCheck) {
            for (String name : names) {
                IGlobalResearch research = tree.getResearch(branch, name);
                if (research == null) continue;
                ILocalResearch colonyResearch = colonyTree.getResearch(branch, name);

                Map<Object, Object> data = new HashMap<>();
                data.put("id", name);
                data.put("name", research.getDesc());
                data.put("effect", research.getEffect().getDesc());
                data.put("status", (colonyResearch == null ? ResearchState.NOT_STARTED : colonyResearch.getState()).toString());

                List<Object> children = getResearch(branch, research.getChilds(), tree, colonyTree);
                if (!children.isEmpty()) {
                    data.put("children", children);
                }
                result.add(data);
            }
        }
        return result;
    }

    private static Optional<BlockPos> toBlockPos(@Nullable Map<?, ?> table) {
        if (table == null || !table.containsKey("x") || !table.containsKey("y") || !table.containsKey("z")) {
            return Optional.empty();
        }

        int x = ((Number) table.get("x")).intValue();
        int y = ((Number) table.get("y")).intValue();
        int z = ((Number) table.get("z")).intValue();

        return Optional.of(new BlockPos(x, y, z));
    }

    private static Object JobInfo(IJob<?> job) {
        if (job == null) return null;
        return LanguageHandler.translateKey(job.getName());
    }

    private static Object BuildingInfo(IBuilding building) {
        if (building == null) return null;

        Map<Object, Object> data = new HashMap<>();
        data.put("location", building.getPosition());
        data.put("type", building.getSchematicName());
        data.put("level", building.getBuildingLevel());
        return data;
    }

    private static Object StatusInfo(VisibleCitizenStatus status) {
        if (status == null) return "Idle";
        return status.getTranslatedText();
    }
}
