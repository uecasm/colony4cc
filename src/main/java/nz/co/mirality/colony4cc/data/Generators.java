package nz.co.mirality.colony4cc.data;

import net.minecraft.data.DataGenerator;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.GatherDataEvent;
import nz.co.mirality.colony4cc.Colony4CC;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, modid = Colony4CC.ID)
public class Generators {
    @SubscribeEvent
    public static void gather(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        generator.addProvider(new LuaHelpProvider(generator, event.getExistingFileHelper()));
    }
}
