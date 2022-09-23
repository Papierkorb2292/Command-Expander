package net.papierkorb2292.command_expander.variables;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.papierkorb2292.command_expander.variables.immediate.operator.IntegerOperatorVariableType;

public class LongVariable extends CriteriaBindableNumberVariable {

    private long value = 0;

    public LongVariable() { }
    public LongVariable(long value) {
        this.value = value;
    }

    public void setValue(long value) {
        this.value = value;
    }

    @Override
    public int intValue() {
        return (int)value;
    }

    @Override
    public long longValue() {
        return value;
    }

    @Override
    public float floatValue() {
        return value;
    }

    @Override
    public double doubleValue() {
        return value;
    }

    @Override
    public String stringValue() {
        return String.valueOf(value) + 'L';
    }

    @Override
    public VariableType getType() {
        return LongVariableType.INSTANCE;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LongVariable && ((LongVariable)o).value == value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    public static LongVariable parse(String value) {
        if(value.length() == 0) {
            return new LongVariable(0);
        }
        int i = 0;
        char c = value.charAt(0);
        char sign = '+';
        if(c == '+' || c == '-') {
            sign = c;
            c = value.charAt(1);
            i = 1;
        }
        if(c == '0') {
            c = value.charAt(i);
            String trimmedValue = value.substring(i + 2);
            if(sign == '-') {
                trimmedValue = "-" + trimmedValue;
            }
            if(c == 'x' || c == 'X') {
                return parseHexadecimal(trimmedValue);
            }
            if(c == 'b' || c == 'B') {
                return parseBinary(trimmedValue);
            }
            return parseOctal(trimmedValue);
        }
        return parseDecimal(value);
    }

    public static LongVariable parseDecimal(String value) {
        return new LongVariable(Long.parseLong(value));
    }

    public static LongVariable parseHexadecimal(String value) {
        return new LongVariable(Long.parseLong(value, 16));
    }

    public static LongVariable parseOctal(String value) {
        return new LongVariable(Long.parseLong(value, 8));
    }

    public static LongVariable parseBinary(String value) {
        return new LongVariable(Long.parseLong(value, 2));
    }

    @Override
    public void add(int value) {
        this.value += value;
    }

    @Override
    public void set(int value) {
        this.value = value;
    }

    public static class LongVariableType implements CriteriaBindableNumberVariableType, IntegerOperatorVariableType {

        public static final LongVariableType INSTANCE = new LongVariableType();

        @Override
        public CriteriaBindableNumberVariable createVariable() {
            return new LongVariable();
        }

        public VariableTypeTemplate getTemplate() {
            return TEMPLATE;
        }

        @Override
        public VariableType getNextLoweredType() {
            return FloatVariable.FloatVariableType.INSTANCE;
        }

        @Override
        public String getName() {
            return "long";
        }

        public static final VariableTypeTemplate TEMPLATE = new VariableTypeTemplate(
                0, () -> LongVariableType.INSTANCE,
                (type, var) -> new LongVariable(var.longValue()),
                new VariableCodec() {

                    @Override
                    public <T> DataResult<T> write(Variable input, DynamicOps<T> ops, T prefix) {
                        return ops.mergeToPrimitive(prefix, ops.createLong(input.longValue()));
                    }

                    @Override
                    public <T> DataResult<Pair<Variable, T>> read(DynamicOps<T> ops, T input, VariableType type) {
                        return ops.getNumberValue(input).map(value -> Pair.of(new LongVariable(value.longValue()), ops.empty()));
                    }
                });

        @Override
        public Variable addVariables(Variable left, Variable right) {
            return new LongVariable(left.longValue() + right.longValue());
        }

        @Override
        public Variable multiplyVariables(Variable left, Variable right) {
            return new LongVariable(left.longValue() * right.longValue());
        }

        @Override
        public Variable negateVariable(Variable value) {
            return new LongVariable(-value.longValue());
        }

        @Override
        public Variable andVariables(Variable left, Variable right) {
            return new LongVariable(left.longValue() & right.longValue());
        }

        @Override
        public Variable shiftVariablesLeft(Variable left, int right) {
            return new LongVariable(left.longValue() << right);
        }

        @Override
        public Variable orVariables(Variable left, Variable right) {
            return new LongVariable(left.longValue() | right.longValue());
        }

        @Override
        public Variable shiftVariablesRight(Variable left, int right) {
            return new LongVariable(left.longValue() >> right);
        }

        @Override
        public Variable xorVariables(Variable left, Variable right) {
            return new LongVariable(left.longValue() ^ right.longValue());
        }

        @Override
        public Variable divideVariables(Variable left, Variable right) {
            return new LongVariable(left.longValue() / right.longValue());
        }

        @Override
        public Variable subtractVariables(Variable left, Variable right) {
            return new LongVariable(left.longValue() - right.longValue());
        }
    }
}
