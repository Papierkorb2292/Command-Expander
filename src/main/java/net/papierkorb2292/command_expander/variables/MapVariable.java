package net.papierkorb2292.command_expander.variables;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.papierkorb2292.command_expander.variables.immediate.operator.AddableOperatorVariableType;

import java.util.*;
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
    public NbtElement toNbt() throws CommandSyntaxException {
        NbtCompound result = new NbtCompound();
        for(Map.Entry<Variable, Variable> entry : value.entrySet()) {
            result.put(entry.getKey().stringValue(), Variable.createNbt(entry.getValue()));
        }
        return result;
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

        @Override
        public String getName() {
            return "map";
        }

        public MapVariableType combine(MapVariableType other) {
            MapVariableType mapType = this;
            VariableType keyType = key, valueType = value;
            if(keyType == null || valueType == null) {
                mapType = new MapVariableType();
                mapType.key = keyType == null ? other.key : keyType;
                mapType.value = valueType == null ? other.value : valueType;
            }
            return mapType;
        }

        private static MapVariable getMapFromListLike(ListLike list, MapVariableType type) throws CommandSyntaxException {
            if(list.getContentType() == null) {
                while(list.hasNext()) {
                    if(list.next() != null) {
                        throw VariableManager.CHILD_TYPE_WAS_NULL_BUT_CHILDREN_WERE_PRESENT_EXCEPTION.create();
                    }
                }
                return new MapVariable(type);
            }
            if(!(list.getContentType() instanceof MapEntryVariable.MapEntryVariableType entryType)) {
                throw VariableManager.INCOMPATIBLE_TYPES_EXCEPTION.create(type, list.getTypeString());
            }
            type = type.combine(new MapVariableType(entryType.key, entryType.value));
            VariableType keyType = type.key;
            VariableType valueType = type.value;
            VariableManager.Caster
                    keyCaster = keyType == null ? null : keyType.getTemplate().caster,
                    valueCaster = valueType == null ? null : valueType.getTemplate().caster;
            MapVariable map = new MapVariable(type);
            while(list.hasNext()) {
                MapEntryVariable entry = (MapEntryVariable)list.next();
                if(entry == null) {
                    continue;
                }
                Variable key = entry.value;
                if(key != null) {
                    if(keyType == null) {
                        throw VariableManager.CHILD_TYPE_WAS_NULL_BUT_CHILDREN_WERE_PRESENT_EXCEPTION.create();
                    }
                    key = keyCaster.cast(keyType, key);
                }
                Variable value = entry.key;
                if(value != null) {
                    if(valueType == null) {
                        throw VariableManager.CHILD_TYPE_WAS_NULL_BUT_CHILDREN_WERE_PRESENT_EXCEPTION.create();
                    }
                    value = valueCaster.cast(valueType, value);
                }
                map.value.put(key, value);
            }
            return map;
        }

        public static final VariableTypeTemplate TEMPLATE = new VariableTypeTemplate(2, MapVariableType::new, (type, var) -> {
            MapVariableType mapType = (MapVariableType)type;
            if(var instanceof ListVariable list) {
                return getMapFromListLike(new ListLike() {

                    private final Iterator<Variable> it = list.value.iterator();

                    @Override
                    public VariableType getContentType() {
                        return list.getContentType();
                    }

                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }

                    @Override
                    public Variable next() {
                        return it.next();
                    }

                    @Override
                    public String getTypeString() {
                        return list.type.asString();
                    }
                }, mapType);
            }
            if(var instanceof IteratorVariable it) {
                return getMapFromListLike(new ListLike() {
                    @Override
                    public VariableType getContentType() {
                        return it.getType().getChild(0);
                    }

                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }

                    @Override
                    public Variable next() throws CommandSyntaxException {
                        return it.next();
                    }

                    @Override
                    public String getTypeString() {
                        return it.getType().asString();
                    }
                }, mapType);
            }
            if(!(var instanceof MapVariable map)) {
                throw VariableManager.INCOMPATIBLE_TYPES_EXCEPTION.create(type.asString(), var == null ? "null" : var.getType().asString());
            }
            mapType = mapType.combine(new MapVariableType(map.type.key, map.type.value));
            VariableType keyType = mapType.key;
            VariableType valueType = mapType.value;
            VariableManager.Caster
                    keyCaster = keyType == null ? null : keyType.getTemplate().caster,
                    valueCaster = valueType == null ? null : valueType.getTemplate().caster;
            MapVariable result = new MapVariable(mapType);
            for(Map.Entry<Variable, Variable> entry : map.value.entrySet()) {
                Variable key = entry.getKey();
                if(key != null) {
                    if(keyType == null) {
                        throw VariableManager.CHILD_TYPE_WAS_NULL_BUT_CHILDREN_WERE_PRESENT_EXCEPTION.create();
                    }
                    key = keyCaster.cast(keyType, key);
                }
                Variable value = entry.getValue();
                if(value != null) {
                    if(valueType == null) {
                        throw VariableManager.CHILD_TYPE_WAS_NULL_BUT_CHILDREN_WERE_PRESENT_EXCEPTION.create();
                    }
                    value = valueCaster.cast(valueType, value);
                }
                result.value.put(key, value);
            }
            return result;
        }, new VariableCodec() {

            @Override
            protected <T> DataResult<Pair<Variable, T>> read(DynamicOps<T> ops, T input, VariableType type) {
                MapVariableType mapType = (MapVariableType) type;
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
                                    T errorElements = errorElementsDataResult.resultOrPartial(VariableManager.dumpError).orElse(null);
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

        private interface ListLike {

            VariableType getContentType();
            boolean hasNext();
            Variable next() throws CommandSyntaxException;
            String getTypeString();
        }
    }
}
