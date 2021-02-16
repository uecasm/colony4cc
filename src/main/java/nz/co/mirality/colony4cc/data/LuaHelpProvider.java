package nz.co.mirality.colony4cc.data;

import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DirectoryCache;
import net.minecraft.data.IDataProvider;
import net.minecraft.resources.IResource;
import net.minecraft.resources.ResourcePackType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.data.ExistingFileHelper;
import nz.co.mirality.colony4cc.Colony4CC;
import nz.co.mirality.colony4cc.peripheral.ColonyPeripheral;

import javax.annotation.Nonnull;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("UnstableApiUsage")
public class LuaHelpProvider implements IDataProvider {
    private final DataGenerator generator;
    private final ExistingFileHelper existingFileHelper;

    public LuaHelpProvider(DataGenerator generator, ExistingFileHelper existingFileHelper) {
        this.generator = generator;
        this.existingFileHelper = existingFileHelper;
    }

    /**
     * Performs this provider's action.
     *
     * @param cache Directory cache
     */
    @Override
    public void act(@Nonnull DirectoryCache cache) {
        generateHelp(cache, "colony/", "colony", ColonyPeripheral.class);
    }

    /**
     * Gets a name for this provider, to use in logging.
     */
    @Override
    @Nonnull
    public String getName() { return "LuaHelp"; }

    @SuppressWarnings("SameParameterValue")
    private void generateHelp(@Nonnull DirectoryCache cache, @Nonnull String prefix, @Nonnull String name, @Nonnull Class<?> klass) {
        try {
            ResourceLocation location = new ResourceLocation(ComputerCraft.MOD_ID, "lua/rom/help/" + prefix + name + ".txt");
            Path path = generator.getOutputFolder().resolve("data").resolve(location.getNamespace()).resolve(location.getPath());

            String content;
            IResource resource = existingFileHelper.getResource(location, ResourcePackType.SERVER_DATA);
            try (Scanner scanner = new Scanner(resource.getInputStream())
                    .useDelimiter("(?<=\\n)|(?!\\n)(?<=\\r)")) {
                StringWriter out = new StringWriter();
                while (scanner.hasNext()) {
                    String line = scanner.next();
                    if (line.startsWith("[API]")) {
                        line = generateApiHelp(name, klass, line.substring(5));
                    }
                    out.write(line);
                }
                content = out.toString();
            }

            String hash = HASH_FUNCTION.hashUnencodedChars(content).toString();
            if (!Objects.equals(cache.getPreviousHash(path), hash) || !Files.exists(path)) {
                Files.createDirectories(path.getParent());

                try (BufferedWriter writer = Files.newBufferedWriter(path)) {
                    writer.write(content);
                }
            }
            cache.recordHash(path, hash);
        } catch (IOException e) {
            Colony4CC.LOGGER.error("Error generating lua help for " + name, e);
        }
    }

    private static String generateApiHelp(@Nonnull final String name, @Nonnull final Class<?> klass, @Nonnull final String newline) {
        List<MethodInfo> methods = new ArrayList<>();
        for (Method method : klass.getMethods()) {
            LuaFunction function = method.getAnnotation(LuaFunction.class);
            if (function == null) continue;

            LuaDoc doc = method.getAnnotation(LuaDoc.class);

            String methodName = method.getName();
            String args = generateApiArgs(method, doc);
            String returns = generateApiReturns(method, doc);
            if (!returns.isEmpty()) { returns = " => " + returns; }

            MethodInfo info = new MethodInfo();
            info.method = method;
            info.function = function;
            info.doc = doc;
            info.name = methodName;
            info.content = String.format("%s.%s(%s)%s%s", name, methodName, args, returns, newline);
            methods.add(info);
        }

        methods.sort((a, b) -> {
            if (a.doc == null) return b.doc == null ? 0 : 1;
            if (b.doc == null) return -1;

            if (a.doc.group() < b.doc.group()) return -1;
            if (a.doc.group() > b.doc.group()) return 1;

            if (a.doc.order() < b.doc.order()) return -1;
            if (a.doc.order() > b.doc.order()) return 1;

            return String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name);
        });

        return methods.stream().map(m -> m.content).collect(Collectors.joining());
    }

    static class MethodInfo {
        public Method method;
        public LuaFunction function;
        public LuaDoc doc;
        public String name;
        public String content;
    }

    private static String generateApiArgs(Method method, LuaDoc doc) {
        if (doc != null && !doc.args().isEmpty()) {
            return doc.args();
        }

        if (method.getParameterCount() == 0) return "";

        Colony4CC.LOGGER.warn("Missing @LuaDoc args on " + method.getName());
        return "...";

        // at one point I had a vague hope that this could iterate the
        // parameters and generate something useful, but reflection can't
        // see the declared parameter name, which makes it a bit useless
        // for documentation purposes (unless we only wanted to see the
        // types).  ideally JavaDoc would probably be involved in the doc
        // generation process somehow, but that's above my pay grade.
    }

    private static String generateApiReturns(Method method, LuaDoc doc) {
        if (doc != null && !doc.returns().isEmpty()) {
            return doc.returns();
        }

        Class<?> returnType = method.getReturnType();
        if (returnType == void.class) {
            return "";
        }
        if (returnType == String.class) {
            return "string";
        }

        if (returnType.isPrimitive()) {
            return returnType.getName().toLowerCase(Locale.ROOT);
        }

        return "table";
    }
}
