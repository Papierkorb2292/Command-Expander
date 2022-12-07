package net.papierkorb2292.command_expander.variables;


import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

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
        byte[] typeArray = Variable.VariableType.getEncoded(var.type);
        DataResult<T> result = var.type.getTemplate().codec.encode(var.var, ops, prefix);
        result = result.mapError(error -> "Error encoding variable: " + error);
        if(result.error().isPresent()) {
            if(result.resultOrPartial(VariableManager.dumpError).isPresent()) {
                result = result.promotePartial(VariableManager.dumpError);
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
            DataResult<Variable.VariableType> typeResult = Variable.VariableType.decodeType(typeBuffer.array(), new Variable.VariableType.OffsetHolder());
            return typeResult.flatMap(type -> {
                DataResult<Pair<VariableHolder, T>> var = type.getTemplate().codec.decode(ops, input, type);
                return var.flatMap(variable -> DataResult.success(Pair.of(new TypedVariable(type, variable.getFirst().variable), variable.getSecond())));
            });
        }));
    }
}
