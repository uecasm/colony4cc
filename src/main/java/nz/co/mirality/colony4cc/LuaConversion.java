package nz.co.mirality.colony4cc;

import dan200.computercraft.shared.peripheral.generic.data.ItemData;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public final class LuaConversion {
    public interface IConverter {
        Object convert(Object value);
    }

    private LuaConversion() {}

    private static final List<IConverter> CONVERTERS = new ArrayList<>();

    public static void register(final IConverter converter) { CONVERTERS.add(converter); }

    static {
        register(new CollectionConverter());
        register(new MinecraftConverter());
    }

    public static Object convert(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }

        for (IConverter converter : CONVERTERS) {
            Object result = converter.convert(value);
            if (result != null) return result;
        }

        Colony4CC.LOGGER.error("Unable to convert value type {}", value.getClass().getName());
        return null;
    }

    private static class CollectionConverter implements IConverter {
        @Override
        public Object convert(Object value) {
            if (value instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) value;
                Map<Object, Object> result = new HashMap<>();
                map.forEach((k, v) -> result.put(String.valueOf(k), LuaConversion.convert(v)));
                return result;
            }
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                Map<Object, Object> result = new HashMap<>();
                for (int i = 0, count = list.size(); i < count; ++i) {
                    result.put(i + 1, LuaConversion.convert(list.get(i)));
                }
                return result;
            }
            if (value instanceof Collection) {
                Collection<?> list = (Collection<?>) value;
                Map<Object, Object> result = new HashMap<>();
                final int[] count = {0};
                list.forEach(v -> result.put(++count[0], LuaConversion.convert(v)));
                return result;
            }
            return null;
        }
    }

    public static class MinecraftConverter implements IConverter {
        @Override
        public Object convert(Object value) {
            if (value instanceof ItemStack) {
                return convertStack((ItemStack) value);
            }
            if (value instanceof GlobalPos) {
                return convert((GlobalPos) value);
            }
            if (value instanceof BlockPos) {
                return convert((BlockPos) value);
            }
            if (value instanceof CompoundNBT) {
                return convertNbt((CompoundNBT) value);
            }
            if (value instanceof ResourceLocation) {
                return ((ResourceLocation) value).toString();
            }
            if (value instanceof ITextComponent) {
                return ((ITextComponent) value).getString();
            }
            return null;
        }

        @Nonnull
        private Map<Object, Object> convert(GlobalPos pos) {
            Map<Object, Object> result = convert(pos.pos());
            result.put("world", pos.dimension().location().toString());
            return result;
        }

        @Nonnull
        private Map<Object, Object> convert(BlockPos pos) {
            Map<Object, Object> result = new HashMap<>();
            result.put("x", pos.getX());
            result.put("y", pos.getY());
            result.put("z", pos.getZ());
            return result;
        }

        @Nonnull
        public static Map<Object, Object> convertStack(@Nonnull ItemStack stack) {
            Map<Object, Object> result = new HashMap<>();

            boolean wasEmptyish = stack != ItemStack.EMPTY && stack.getCount() == 0;
            if (wasEmptyish) {
                // ordinarily zero-count stacks are treated as empty, which breaks things;
                // we want to treat them as regular stacks where possible
                stack.setCount(1);
            }
            ItemData.fill(result, stack);
            if (wasEmptyish) {
                stack.setCount(0);  // it should be a copy we don't care about, but just in case...
                result.put("count", 0);
            }

            return result;
        }

        @Nullable
        private static Object convertNbt(@Nullable INBT nbt) {
            if (nbt == null) return null;
            if (nbt instanceof CompoundNBT) {
                CompoundNBT compound = (CompoundNBT) nbt;
                Map<Object, Object> result = new HashMap<>();
                for (String key : compound.getAllKeys()) {
                    result.put(key, convertNbt(compound.get(key)));
                }
                return result;
            }
            if (nbt instanceof NumberNBT) {
                return ((NumberNBT) nbt).getAsNumber();
            }
            if (nbt instanceof ListNBT) {
                List<Object> result = new ArrayList<>();
                for (INBT inbt : (ListNBT) nbt) {
                    result.add(convertNbt(inbt));
                }
                return result;
            }
            // this doesn't correctly convert all kinds of NBT, but it's
            // good enough for most purposes.  certainly anything this
            // mod is likely to encounter.
            return nbt.getAsString();
        }
    }
}
