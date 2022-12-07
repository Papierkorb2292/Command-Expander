package net.papierkorb2292.command_expander.variables;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;

import java.util.UUID;

public class EntityVariable extends Variable {

    public final UUID uuid;

    public EntityVariable(UUID entity) {
        super();
        this.uuid = entity;
    }

    @Override
    public int intValue() {
        return (int) uuid.getLeastSignificantBits();
    }

    @Override
    public long longValue() {
        return uuid.getLeastSignificantBits();
    }

    @Override
    public float floatValue() {
        return uuid.getLeastSignificantBits();
    }

    @Override
    public double doubleValue() {
        return uuid.getLeastSignificantBits();
    }

    @Override
    public String stringValue() {
        return "(entity)\"" + uuid.toString() + "\"";
    }

    @Override
    public VariableType getType() {
        return EntityVariableType.INSTANCE;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EntityVariable && uuid.equals(((EntityVariable)o).uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public NbtElement toNbt() {
        return NbtHelper.fromUuid(uuid);
    }

    public static class EntityVariableType implements VariableType {

        public static final EntityVariableType INSTANCE = new EntityVariableType();

        @Override
        public Variable createVariable() {
            return new EntityVariable(null);
        }

        @Override
        public VariableTypeTemplate getTemplate() {
            return TEMPLATE;
        }

        @Override
        public VariableType getNextLoweredType() {
            return LongVariable.LongVariableType.INSTANCE;
        }

        @Override
        public String getName() {
            return "entity";
        }

        public static final VariableTypeTemplate TEMPLATE = new VariableTypeTemplate(
                0, () -> EntityVariableType.INSTANCE,
                (type, var) -> {
                    if(var instanceof EntityVariable entity) {
                        return new EntityVariable(entity.uuid);
                    }
                    if(var instanceof ListVariable list) {
                        if (list.value.size() == 2) {
                            return new EntityVariable(new UUID(list.value.get(0).longValue(), list.value.get(1).longValue()));
                        }
                        if (list.value.size() == 4) {
                            return new EntityVariable(new UUID((long) list.value.get(0).intValue() << 32 | (long) list.value.get(1).intValue(), (long) list.value.get(3).intValue() << 32 | (long) list.value.get(4).intValue()));
                        }
                        throw VariableManager.PARSE_EXCEPTION.create(var.stringValue(), type.asString());
                    }
                    if(var instanceof StringVariable string) {
                        try {
                            return new EntityVariable(UUID.fromString(string.getString()));
                        } catch (IllegalArgumentException e) {
                            throw VariableManager.PARSE_EXCEPTION.create(var.stringValue(), type.asString());
                        }
                    }
                    throw VariableManager.PARSE_EXCEPTION.create(var.stringValue(), type.asString());
                },
                new VariableCodec() {

                    @Override
                    public <T> DataResult<T> write(Variable input, DynamicOps<T> ops, T prefix) {
                        UUID uuid = ((EntityVariable) input).uuid;
                        return ops.mapBuilder().add("most", Codec.LONG.encode(uuid.getMostSignificantBits(), ops, ops.empty())).add("least", Codec.LONG.encode(uuid.getLeastSignificantBits(), ops, ops.empty())).build(prefix);
                    }

                    @Override
                    public <T> DataResult<Pair<Variable, T>> read(DynamicOps<T> ops, T input, VariableType type) {
                        return ops.getMap(input).flatMap(map ->
                                Codec.LONG.decode(ops, map.get("most")).flatMap(most ->
                                        Codec.LONG.decode(ops, map.get("least")).map(least ->
                                                Pair.of(new EntityVariable(new UUID(most.getFirst(), least.getFirst())), ops.empty()))));
                    }
                });
    }
}
