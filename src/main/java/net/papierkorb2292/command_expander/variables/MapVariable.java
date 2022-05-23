package net.papierkorb2292.command_expander.variables;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
            sb.append(buildEntryStringValue(it.next()));
            while(it.hasNext()) {
                sb.append(", ");
                sb.append(buildEntryStringValue(it.next()));
            }
        }
        sb.append(" }");
        return sb.toString();
    }

    @Override
    public VariableType getType() {
        return type;
    }

    private String buildEntryStringValue(Map.Entry<Variable, Variable> entry) {
        return String.format("{ %s, %s }", entry.getKey().stringValue(), entry.getValue().stringValue());
    }

    @Override
    public Variable get(Variable indexVar) {
        return value.get(indexVar);
    }

    @Override
    public Variable getOrCreate(Variable indexVar) {
        return value.computeIfAbsent(indexVar, key -> type.value.createVariable());
    }

    public static class MapVariableType implements VariableType {
        VariableType key;
        VariableType value;

        @Override
        public Variable createVariable() {
            return new MapVariable(this);
        }

        @Override
        public VariableTypeTemplate getTemplate() {
            return template;
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

        public static final VariableTypeTemplate template = new VariableTypeTemplate(2, MapVariable.MapVariableType::new, (type, var) -> {
            if(!(var instanceof MapVariable map)) {
                throw VariableManager.INCOMPATIBLE_TYPES_EXCEPTION.create("MapVariable", var.getClass().getName());
            }
            MapVariable.MapVariableType mapType = (MapVariable.MapVariableType)type;
            Variable.VariableType keyType = mapType.key, valueType = mapType.value;
            VariableManager.Caster keyCaster = keyType.getTemplate().caster, valueCaster = valueType.getTemplate().caster;
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
                                    DataResult<T> errorElementsDataResult = ops.listBuilder()
                                            .add(keyList.getSecond())
                                            .add(valueList.getSecond())
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
