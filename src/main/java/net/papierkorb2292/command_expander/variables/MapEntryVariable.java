package net.papierkorb2292.command_expander.variables;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

public class MapEntryVariable extends Variable {

    public Variable key, value;
    final MapEntryVariableType type;

    public MapEntryVariable(MapEntryVariableType type) {
        this.type = type;
    }

    public MapEntryVariable(MapEntryVariableType type, Variable key, Variable value) throws CommandSyntaxException  {
        this.type = type;
        this.key = VariableManager.castVariable(type.key, key);
        this.value = VariableManager.castVariable(type.value, value);
    }

    @Override
    public int intValue() {
        return value == null ? 0 : value.intValue();
    }

    @Override
    public long longValue() {
        return value == null ? 0 : value.longValue();
    }

    @Override
    public float floatValue() {
        return value == null ? 0 : value.floatValue();
    }

    @Override
    public double doubleValue() {
        return value == null ? 0 : value.doubleValue();
    }

    @Override
    public String stringValue() {
        return buildStringValue(key, value);
    }

    public static String buildStringValue(Variable key, Variable value) {
        return String.format("{ %s: %s }", key == null ? "null" : key.stringValue(), value == null ? "null" : value.stringValue());
    }

    @Override
    public VariableType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MapEntryVariable entry && entry.key.equals(key) && entry.value.equals(value);
    }

    @Override
    public int hashCode() {
        return 0;
    }

    public static class MapEntryVariableType implements VariableType {

        public VariableType key;
        public VariableType value;

        public MapEntryVariableType() { }
        public MapEntryVariableType(VariableType key, VariableType value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public Variable createVariable() {
            return new MapEntryVariable(this);
        }

        @Override
        public VariableTypeTemplate getTemplate() {
            return TEMPLATE;
        }

        @Override
        public VariableType getNextLoweredType() {
            return null;
        }

        @Override
        public void setChild(int index, VariableType child) {
            if(index == 0) {
                key = child;
                return;
            }
            if(index == 1) {
                value = child;
            }
        }

        public VariableType getChild(int index) {
            return index == 0 ? key : index == 1 ? value : null;
        }

        @Override
        public String getName() {
            return "entry";
        }

        public static final VariableTypeTemplate TEMPLATE = new VariableTypeTemplate(2, MapEntryVariableType::new, (type, var) -> {
            if(!(var instanceof MapEntryVariable entry)) {
                throw VariableManager.INCOMPATIBLE_TYPES_EXCEPTION.create("MapEntryVariable", var.getClass().getName());
            }
            MapEntryVariableType entryType = (MapEntryVariableType)type;
            if(entryType.key == null || entryType.value == null) {
                VariableType keyType = entryType.key, valueType = entryType.value;
                entryType = new MapEntryVariableType();
                if(keyType == null) {
                    keyType = entry.type.key;
                    if(keyType == null && entry.key != null) {
                        throw VariableManager.CHILD_TYPE_WAS_NULL_BUT_CHILDREN_WERE_PRESENT_EXCEPTION.create();
                    }
                }
                entryType.key = keyType;
                if(valueType == null) {
                    valueType = entry.type.value;
                    if(valueType == null && entry.value != null) {
                        throw VariableManager.CHILD_TYPE_WAS_NULL_BUT_CHILDREN_WERE_PRESENT_EXCEPTION.create();
                    }
                }
                entryType.value = valueType;
            }
            return new MapEntryVariable(entryType, entry.key, entry.value);
        }, new VariableCodec() {

            @Override
            protected <T> DataResult<Pair<Variable, T>> read(DynamicOps<T> ops, T input, Variable.VariableType type) {
                MapEntryVariable.MapEntryVariableType mapEntryType = (MapEntryVariable.MapEntryVariableType) type;
                return ops.get(input, "keys")
                        .flatMap(keyListElement -> mapEntryType.key.getTemplate().codec.decode(ops, keyListElement, mapEntryType.key))
                        .mapError(error -> "Error decoding key of map entry: (" + error + ")")
                        .flatMap(key -> ops.get(input, "values")
                                .flatMap(valueListElement -> mapEntryType.value.getTemplate().codec.decode(ops, valueListElement, mapEntryType.value))
                                .mapError(error -> "Error decoding value of map entry: (" + error + ")")
                                .flatMap(value -> {
                                    DataResult<T> errorElementsDataResult = ops.mapBuilder()
                                            .add("keys", key.getSecond())
                                            .add("values", value.getSecond())
                                            .build(ops.empty());
                                    T errorElements = errorElementsDataResult.resultOrPartial(VariableManager.dumpError).orElse(null);
                                    MapEntryVariable result = new MapEntryVariable(mapEntryType);
                                    result.key = key.getFirst().variable;
                                    result.value = value.getFirst().variable;
                                    return errorElementsDataResult.error().isPresent()
                                            ? DataResult.error(
                                            errorElementsDataResult.error().get().message(),
                                            Pair.of(result, errorElements))
                                            : DataResult.success(Pair.of(result, errorElements));
                                }));
            }

            @Override
            protected <T> DataResult<T> write(Variable input, DynamicOps<T> ops, T prefix) {
                MapEntryVariable entry = (MapEntryVariable) input;
                return ops.mapBuilder()
                        .add("keys", entry.type.key.getTemplate().codec.encode(entry.key, ops, ops.empty()).mapError(error -> "Error encoding key of map entry: (" + error + ")"))
                        .add("values", entry.type.value.getTemplate().codec.encode(entry.value, ops, ops.empty()).mapError(error -> "Error encoding values of map entry: (" + error + ")"))
                        .build(prefix);
            }
        });
    }
}
