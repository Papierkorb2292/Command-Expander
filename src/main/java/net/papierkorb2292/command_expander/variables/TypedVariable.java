package net.papierkorb2292.command_expander.variables;


import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;

public class TypedVariable {

    public Variable.VariableType type;
    public @Nullable Variable var;

    public TypedVariable(Variable.VariableType type, @Nullable Variable var) {
        this.type = type;
        this.var = var;
    }

    public void castAndSet(@Nullable Variable var) throws CommandSyntaxException {
        this.var = VariableManager.castVariable(type, var);
    }

    public static <T> DataResult<T> encode(TypedVariable var, DynamicOps<T> ops, T prefix) {
        Deque<Variable.VariableType> typeEncoderStack = new LinkedList<>();
        Queue<VariableTypeTemplate> typeEncoderEncounteredTypes = new LinkedList<>();
        int typeSize = 1;
        typeEncoderStack.add(var.type);
        while(!typeEncoderStack.isEmpty()) {
            Variable.VariableType type = typeEncoderStack.pop();
            VariableTypeTemplate typeTemplate = type.getTemplate();
            typeEncoderEncounteredTypes.add(typeTemplate);
            int childrenCount = typeTemplate.childrenCount;
            typeSize += childrenCount;
            for(int i = 0; i < childrenCount; ++i) {
                typeEncoderStack.add(type.getChild(i));
            }
        }
        byte[] typeArray = new byte[typeSize];
        int index = 0;
        while(!typeEncoderEncounteredTypes.isEmpty()) {
            typeArray[index] = typeEncoderEncounteredTypes.remove().id;
            ++index;
        }
        DataResult<T> result = var.type.getTemplate().codec.encode(var.var, ops, prefix);
        result = result.mapError(error -> "Error encoding variable: " + error);
        if(result.error().isPresent()) {
            if(result.resultOrPartial(VariableManager::dumpError).isPresent()) {
                result = result.promotePartial(VariableManager::dumpError);
            }
            else {
                return result.mapError(error -> "Error encoding variable: " + error);
            }
        }
        result = result.flatMap(element -> ops.mapBuilder().add("type", ops.createByteList(ByteBuffer.wrap(typeArray))).build(element));
        return result.mapError(error -> "Error adding type to encoded variable: " + error);
    }

    public static <T> DataResult<Pair<TypedVariable, T>> decode(T input, DynamicOps<T> ops) {
        DataResult<MapLike<T>> dataResult = ops.getMap(input);
        return dataResult.flatMap(map -> ops.getByteBuffer(map.get("type")).flatMap(typeBuffer -> {
            DataResult<Variable.VariableType> typeResult = decodeType(typeBuffer.array(), new OffsetHolder());
            return typeResult.flatMap(type -> {
                DataResult<Pair<VariableHolder, T>> var = type.getTemplate().codec.decode(ops, input, type);
                return var.flatMap(variable -> DataResult.success(Pair.of(new TypedVariable(type, variable.getFirst().variable), variable.getSecond())));
            });
        }));
    }

    private static DataResult<Variable.VariableType> decodeType(byte[] type, OffsetHolder offset) {
        if(type.length - offset.value <= 0) {
            return DataResult.error("Type in the data of variable has invalid length");
        }
        byte id = type[offset.value];
        if(id < 0 || id >= VariableManager.TYPES_BY_ID.size()) {
            return DataResult.error(String.format("Encountered invalid type id '%s' while decoding variable", id));
        }
        VariableTypeTemplate template = VariableManager.TYPES_BY_ID.get(id);
        Variable.VariableType result = template.typeFactory.get();
        ++offset.value;
        DataResult<Variable.VariableType> parsedType = DataResult.success(result);
        for(int i = 0; i < template.childrenCount; ++i) {
            int finalI = i;
            parsedType = parsedType.apply2((currentType, child) -> {
                currentType.setChild(finalI, child);
                return currentType;
            }, decodeType(type, offset));
        }
        return parsedType;
    }

    private static class OffsetHolder {

        public int value = 0;
    }
}
