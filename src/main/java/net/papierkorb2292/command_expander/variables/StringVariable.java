package net.papierkorb2292.command_expander.variables;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtString;
import net.papierkorb2292.command_expander.variables.immediate.operator.AddableOperatorVariableType;

public class StringVariable extends Variable {

    String value = "";

    public StringVariable() { }
    public StringVariable(String value) {
        this.value = value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public int intValue() {
        return value.length();
    }

    @Override
    public long longValue() {
        return value.length();
    }

    @Override
    public float floatValue() {
        return value.length();
    }

    @Override
    public double doubleValue() {
        return value.length();
    }

    @Override
    public String stringValue() {
        return "\""+value+"\"";
    }

    @Override
    public VariableType getType() {
        return StringVariableType.INSTANCE;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StringVariable && ((StringVariable) o).value.equals(value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public NbtElement toNbt() {
        return NbtString.of(value);
    }

    public String getString() {
        return value;
    }

    public static class StringVariableType implements VariableType, AddableOperatorVariableType {

        public static final StringVariableType INSTANCE = new StringVariableType();

        @Override
        public Variable createVariable() {
            return new StringVariable(null);
        }

        @Override
        public VariableTypeTemplate getTemplate() {
            return TEMPLATE;
        }

        private static final VariableType loweredType = new ListVariable.ListVariableType(ShortVariable.ShortVariableType.INSTANCE);

        @Override
        public VariableType getNextLoweredType() {
            return loweredType;
        }

        @Override
        public String getName() {
            return "string";
        }

        public static final VariableTypeTemplate TEMPLATE = new VariableTypeTemplate(
                0, () -> INSTANCE,
                (type, var) -> {
                    if(var instanceof StringVariable string) {
                        return new StringVariable(string.value);
                    }
                    if(!(var instanceof ListVariable list) || !ShortVariable.ShortVariableType.INSTANCE.instanceOf(list.getContentType())) {
                        throw VariableManager.INCOMPATIBLE_TYPES_EXCEPTION.create(type.asString(), var == null ? "null" : var.getType().asString());
                    }
                    StringBuilder sb = new StringBuilder();
                    for(int i = 0; i < list.value.size(); ++i) {
                        sb.append((char) list.value.get(i).shortValue());
                    }
                    return new StringVariable(sb.toString());
                },
                new VariableCodec() {

                    @Override
                    public <T> DataResult<T> write(Variable input, DynamicOps<T> ops, T prefix) {
                        return ops.mergeToPrimitive(prefix, ops.createString(((StringVariable)input).value));
                    }

                    @Override
                    public <T> DataResult<Pair<Variable, T>> read(DynamicOps<T> ops, T input, Variable.VariableType type) {
                        return ops.getStringValue(input).map(value -> Pair.of(new StringVariable(value), ops.empty()));
                    }
                });

        @Override
        public Variable addVariables(Variable left, Variable right) {
            return new StringVariable(((StringVariable) left).value + ((StringVariable) right).value);
        }
    }
}
