package net.papierkorb2292.command_expander.variables;

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
        StringBuilder result = new StringBuilder();
        result.append('\"');
        String value = this.value;
        for(int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch(c) {
                case '\t' -> result.append('\\').append('t');
                case '\b' -> result.append('\\').append('b');
                case '\n' -> result.append('\\').append('n');
                case '\r' -> result.append('\\').append('r');
                case '\f' -> result.append('\\').append('f');
                case '\\' -> result.append('\\').append('\\');
                case '"' -> result.append('\\').append('"');
                default -> result.append(c);
            }
        }
        return result.append('\"').toString();
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
                    public <T> DataResult<Pair<Variable, T>> read(DynamicOps<T> ops, T input, VariableType type) {
                        return ops.getStringValue(input).map(value -> Pair.of(new StringVariable(value), ops.empty()));
                    }
                });

        @Override
        public Variable addVariables(Variable left, Variable right) {
            return new StringVariable(((StringVariable) left).value + ((StringVariable) right).value);
        }
    }
}
