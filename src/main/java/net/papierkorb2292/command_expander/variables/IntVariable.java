package net.papierkorb2292.command_expander.variables;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.papierkorb2292.command_expander.variables.immediate.operator.IntegerOperatorVariableType;

public class IntVariable extends CriteriaBindableNumberVariable {

    private int value = 0;

    public IntVariable() { }
    public IntVariable(int value) {
        this.value = value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    @Override
    public int intValue() {
        return value;
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
        return String.valueOf(value);
    }

    @Override
    public VariableType getType() {
        return IntVariableType.INSTANCE;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IntVariable && ((IntVariable)o).value == value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    public static IntVariable parse(String value) {
        if(value.length() == 0) {
            return new IntVariable(0);
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

    public static IntVariable parseDecimal(String value) {
        return new IntVariable(Integer.parseInt(value));
    }

    public static IntVariable parseHexadecimal(String value) {
        return new IntVariable(Integer.parseInt(value, 16));
    }

    public static IntVariable parseOctal(String value) {
        return new IntVariable(Integer.parseInt(value, 8));
    }

    public static IntVariable parseBinary(String value) {
        return new IntVariable(Integer.parseInt(value, 2));
    }

    @Override
    public void add(int value) {
        this.value += value;
    }

    @Override
    public void set(int value) {
        this.value = value;
    }

    public static class IntVariableType implements CriteriaBindableNumberVariableType, IntegerOperatorVariableType {

        public static final IntVariableType INSTANCE = new IntVariableType();

        @Override
        public IntVariable createVariable() {
            return new IntVariable();
        }

        public VariableTypeTemplate getTemplate() {
            return TEMPLATE;
        }

        @Override
        public VariableType getNextLoweredType() {
            return LongVariable.LongVariableType.INSTANCE;
        }

        @Override
        public String getName() {
            return "int";
        }

        public static final VariableTypeTemplate TEMPLATE = new VariableTypeTemplate(
                0, () -> IntVariable.IntVariableType.INSTANCE,
                (type, var) -> new IntVariable(var.intValue()),
                new VariableCodec() {

                    @Override
                    public <T> DataResult<T> write(Variable input, DynamicOps<T> ops, T prefix) {
                        return ops.mergeToPrimitive(prefix, ops.createInt(input.intValue()));
                    }

                    @Override
                    public <T> DataResult<Pair<Variable, T>> read(DynamicOps<T> ops, T input, Variable.VariableType type) {
                        return ops.getNumberValue(input).map(value -> Pair.of(new IntVariable(value.intValue()), ops.empty()));
                    }
                });

        @Override
        public Variable addVariables(Variable left, Variable right) {
            return new IntVariable(left.intValue() + right.intValue());
        }

        @Override
        public Variable multiplyVariables(Variable left, Variable right) {
            return new IntVariable(left.intValue() * right.intValue());
        }

        @Override
        public Variable negateVariable(Variable value) {
            return new IntVariable(-value.intValue());
        }

        @Override
        public Variable andVariables(Variable left, Variable right) {
            return new IntVariable(left.intValue() & right.intValue());
        }

        @Override
        public Variable shiftVariablesLeft(Variable left, int right) {
            return new IntVariable(left.intValue() << right);
        }

        @Override
        public Variable orVariables(Variable left, Variable right) {
            return new IntVariable(left.intValue() | right.intValue());
        }

        @Override
        public Variable shiftVariablesRight(Variable left, int right) {
            return new IntVariable(left.intValue() >> right);
        }

        @Override
        public Variable xorVariables(Variable left, Variable right) {
            return new IntVariable(left.intValue() ^ right.intValue());
        }

        @Override
        public Variable divideVariables(Variable left, Variable right) {
            return new IntVariable(left.intValue() / right.intValue());
        }

        @Override
        public Variable subtractVariables(Variable left, Variable right) {
            return new IntVariable(left.intValue() - right.intValue());
        }
    }
}
