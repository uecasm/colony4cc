package nz.co.mirality.colony4cc;

import net.minecraftforge.common.ForgeConfigSpec;

public class Colony4CCConfig
{
    private final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
    private final ForgeConfigSpec spec;

    private final ForgeConfigSpec.BooleanValue freeCreativePocketPlayerCost;
    private final ForgeConfigSpec.IntValue highlightWorkerCostMultiplier;
    private final ForgeConfigSpec.IntValue highlightBuildingCostMultiplier;

    public Colony4CCConfig()
    {
        builder.push("balance");

        freeCreativePocketPlayerCost = builder.comment("True if creative players using a Pocket Computer don't get charged item costs")
                .translation("config.colony4cc.freeCreativePocketPlayerCost")
                .define("freeCreativePocketPlayerCost", true);

        highlightWorkerCostMultiplier = builder.comment("0 = highlightWorker is always free; 1 = normal price")
                .translation("config.colony4cc.highlightWorkerCostMultiplier")
                .defineInRange("highlightWorkerCostMultiplier", 1, 0, 2);

        highlightBuildingCostMultiplier = builder.comment("0 = highlightBuilding is always free; 1 = normal price; 2+ = cost multiplier")
                .translation("config.colony4cc.highlightBuildingCostMultiplier")
                .defineInRange("highlightBuildingCostMultiplier", 1, 0, 8);

        builder.pop();

        spec = builder.build();
    }

    public ForgeConfigSpec getSpec() { return spec; }

    public boolean getFreeCreativePocketPlayerCost() { return freeCreativePocketPlayerCost.get(); }
    public int getHighlightWorkerCostMultiplier() { return highlightWorkerCostMultiplier.get(); }
    public int getHighlightBuildingCostMultiplier() { return highlightBuildingCostMultiplier.get(); }
}
