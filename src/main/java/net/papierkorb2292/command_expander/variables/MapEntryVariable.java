package net.papierkorb2292.command_expander.variables;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import java.util.Iterator;
import java.util.List;

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
        return value.intValue();
    }

    @Override
    public long longValue() {
        return value.longValue();
    }

    @Override
    public float floatValue() {
        return value.floatValue();
    }

    @Override
    public double doubleValue() {
        return value.doubleValue();
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

        public static final VariableTypeTemplate TEMPLATE = new VariableTypeTemplate(2, MapEntryVariableType::new, (type, var) -> {
            if(!(var instanceof MapEntryVariable entry)) {
                throw VariableManager.INCOMPATIBLE_TYPES_EXCEPTION.create("MapEntryVariable", var.getClass().getName());
            }
            MapEntryVariableType entryType = (MapEntryVariableType)type;
            return new MapEntryVariable(entryType, entry.key, entry.value);
        }, new VariableCodec() {

            @Override
            protected <T> DataResult<Pair<Variable, T>> read(DynamicOps<T> ops, T input, Variable.VariableType type) {
                MapVariable.MapVariableType mapType = (MapVariable.MapVariableType) type;
                return ops.get(input, "keys")
                        .flatMap(keyListElement -> decodeList(ops, keyListElement, mapType.key))
                        .mapError(error -> "Error decoding keys of map: (" + error + ")")
                        .flatMap(keyList -> ops.get(input, "values")
                                .flatMap(valueListElement -> decodeList(ops, valueListElement, mapType.value))
                                .mapError(error -> "Error decoding values of map: (" + error + ")")
                                .flatMap(valueList -> {
                                    List<Variable> keys = keyList.getFirst(), values = valueList.getFirst();
                                    DataResult<T> errorElementsDataResult = ops.mapBuilder()
                                            .add("keys", keyList.getSecond())
                                            .add("values", valueList.getSecond())
                                            .build(ops.empty());
                                    T errorElements = errorElementsDataResult.resultOrPartial(VariableManager::dumpError).orElse(null);
                                    MapVariable result = new MapVariable(mapType);
                                    if(keys.size() != values.size()) {
                                        String error = "Key and value list of map had different sizes; ";
                                        if(errorElementsDataResult.error().isPresent()) {
                                            error += errorElementsDataResult.error().get().message() + "; ";
                                        }
                                        return DataResult.error(error, Pair.of(result, errorElements));
                                    }
                                    Iterator<Variable> keysIterator = keys.iterator(), valuesIterator = values.iterator();
                                    while(keysIterator.hasNext()) {
                                        result.value.put(keysIterator.next(), valuesIterator.next());
                                    }
                                    return errorElementsDataResult.error().isPresent()
                                            ? DataResult.error(
                                            errorElementsDataResult.error().get().message(),
                                            Pair.of(result, errorElements))
                                            : DataResult.success(Pair.of(result, errorElements));
                                }));
            }

            @Override
            protected <T> DataResult<T> write(Variable input, DynamicOps<T> ops, T prefix) {
                MapVariable map = (MapVariable) input;
                return ops.mapBuilder()
                        .add("keys", encodeList(((MapVariable) input).value.keySet().stream().toList(), ops, ops.empty(), map.type.key).mapError(error -> "Error encoding keys of map: (" + error + ")"))
                        .add("values", encodeList(((MapVariable) input).value.values().stream().toList(), ops, ops.empty(), map.type.value).mapError(error -> "Error encoding values of map: (" + error + ")"))
                        .build(prefix);
            }
        });
    }
}
