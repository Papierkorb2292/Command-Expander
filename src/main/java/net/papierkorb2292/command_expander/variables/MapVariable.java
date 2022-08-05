package net.papierkorb2292.command_expander.variables;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.papierkorb2292.command_expander.variables.immediate.operator.AddableOperatorVariableType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class MapVariable extends IndexableVariable {

    final MapVariableType type;
    final Map<Variable, Variable> value = new HashMap<>();

    public MapVariable(MapVariableType type) {
        this.type = type;
    }

    @Override
    public int intValue() {
        return value.size();
    }

    @Override
    public long longValue() {
        return value.size();
    }

    @Override
    public float floatValue() {
        return value.size();
    }

    @Override
    public double doubleValue() {
        return value.size();
    }

    @Override
    public String stringValue() {
        StringBuilder sb = new StringBuilder();
        sb.append("{ ");
        if(value.size() > 0) {
            Iterator<Map.Entry<Variable, Variable>> it = value.entrySet().iterator();
            Map.Entry<Variable, Variable> entry = it.next();
            sb.append(MapEntryVariable.buildStringValue(entry.getKey(), entry.getValue()));
            while(it.hasNext()) {
                sb.append(", ");
                entry = it.next();
                sb.append(MapEntryVariable.buildStringValue(entry.getKey(), entry.getValue()));
            }
        }
        sb.append(" }");
        return sb.toString();
    }

    @Override
    public VariableType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MapVariable map && value.equals(map.value);
    }

    public boolean existingKeysMatch(MapVariable other) {
        return value.entrySet().stream().allMatch(entry -> other.value.containsKey(entry.getKey()) && other.value.get(entry.getKey()).equals(entry.getValue()));
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public Variable get(Variable indexVar) {
        return value.get(indexVar);
    }

    @Override
    public boolean ensureIndexExists(Variable indexVar) {
        return value.containsKey(indexVar);
    }

    @Override
    public boolean set(Variable indexVar, Variable value) {
        Variable prev = this.value.put(indexVar, value);
        return prev == null || !prev.equals(value);
    }

    @Override
    public boolean remove(Variable indexVar) {
        boolean contained = value.containsKey(indexVar);
        value.remove(indexVar);
        return contained;
    }

    @Override
    public Variable ensureIndexCompatible(Variable indexVar) throws CommandSyntaxException {
        return VariableManager.castVariable(type.key, indexVar);
    }

    @Override
    public int clear() {
        int size = value.size();
        value.clear();
        return size;
    }

    @Override
    public int setAll(Variable value) {
        this.value.replaceAll((k, v) -> value);
        return this.value.size();
    }

    @Override
    public VariableType getContentType() {
        return type.value;
    }

    @Override
    public Stream<Variable> getContents() {
        MapEntryVariable.MapEntryVariableType entryType = new MapEntryVariable.MapEntryVariableType(type.key, type.value);
        return value.entrySet().stream().map(entry -> {
            MapEntryVariable entryVar = new MapEntryVariable(entryType);
            entryVar.key = entry.getKey();
            entryVar.value = entry.getValue();
            return entryVar;
        });
    }

    @Override
    public Stream<Variable> getIndices() {
        return value.keySet().stream();
    }

    public static class MapVariableType implements VariableType, AddableOperatorVariableType {

        public VariableType key;
        public VariableType value;

        public MapVariableType() { }
        public MapVariableType(VariableType key, VariableType value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public Variable createVariable() {
            return new MapVariable(this);
        }

        @Override
        public VariableTypeTemplate getTemplate() {
            return TEMPLATE;
        }

        @Override
        public VariableType getNextLoweredType() {
            return new ListVariable.ListVariableType(new MapEntryVariable.MapEntryVariableType(key, value));
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

        public static final VariableTypeTemplate TEMPLATE = new VariableTypeTemplate(2, MapVariable.MapVariableType::new, (type, var) -> {
            if(!(var instanceof MapVariable map)) {
                throw VariableManager.INCOMPATIBLE_TYPES_EXCEPTION.create("MapVariable", var.getClass().getName());
            }
            MapVariable.MapVariableType mapType = (MapVariable.MapVariableType)type;
            Variable.VariableType keyType = mapType.key, valueType = mapType.value;
            VariableManager.Caster keyCaster = (keyType == null ? map.type.key : keyType).getTemplate().caster, valueCaster = (valueType == null ? map.type.value : valueType).getTemplate().caster;
            MapVariable result = new MapVariable(mapType);
            for(Map.Entry<Variable, Variable> entry : map.value.entrySet()) {
                result.value.put(keyCaster.cast(keyType, entry.getKey()), valueCaster.cast(valueType, entry.getValue()));
            }
            return result;
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

        @Override
        public Variable addVariables(Variable left, Variable right) {
            if(!(left instanceof MapVariable && right instanceof MapVariable)) {
                return null;
            }
            MapVariable result = new MapVariable(this);
            result.value.putAll(((MapVariable)left).value);
            result.value.putAll(((MapVariable)right).value);
            return result;
        }
    }
}
