package net.papierkorb2292.command_expander.variables;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtShort;
import net.papierkorb2292.command_expander.variables.immediate.operator.IntegerOperatorVariableType;

public class ShortVariable extends CriteriaBindableNumberVariable {

    private short value = 0;

    public ShortVariable() { }
    public ShortVariable(short value) {
        this.value = value;
    }

    public void setValue(short value) {
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
        return String.valueOf(value) + 'S';
    }

    @Override
    public VariableType getType() {
        return ShortVariableType.INSTANCE;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ShortVariable && ((ShortVariable)o).value == value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public NbtElement toNbt() {
        return NbtShort.of(value);
    }

    public static ShortVariable parse(String value) {
        if(value.length() == 0) {
            return new ShortVariable((byte) 0);
        }
        return new ShortVariable(Short.parseShort(value));
    }

    @Override
    public void add(int value) {
        this.value += value;
    }

    @Override
    public void set(int value) {
        this.value = (short)value;
    }

    public static class ShortVariableType implements CriteriaBindableNumberVariableType, IntegerOperatorVariableType {

        public static final ShortVariableType INSTANCE = new ShortVariableType();

        @Override
        public ShortVariable createVariable() {
            return new ShortVariable();
        }

        public VariableTypeTemplate getTemplate() {
            return TEMPLATE;
        }

        @Override
        public VariableType getNextLoweredType() {
            return IntVariable.IntVariableType.INSTANCE;
        }

        @Override
        public String getName() {
            return "short";
        }

        public static final VariableTypeTemplate TEMPLATE = new VariableTypeTemplate(
                0, () -> INSTANCE,
                (type, var) -> new ShortVariable(var.shortValue()),
                new VariableCodec() {

                    @Override
                    public <T> DataResult<T> write(Variable input, DynamicOps<T> ops, T prefix) {
                        return ops.mergeToPrimitive(prefix, ops.createShort(input.shortValue()));
                    }

                    @Override
                    public <T> DataResult<Pair<Variable, T>> read(DynamicOps<T> ops, T input, VariableType type) {
                        return ops.getNumberValue(input).map(value -> Pair.of(new ShortVariable(value.shortValue()), ops.empty()));
                    }
                });

        @Override
        public Variable addVariables(Variable left, Variable right) {
            return new ShortVariable((short) (left.shortValue() + right.shortValue()));
        }

        @Override
        public Variable multiplyVariables(Variable left, Variable right) {
            return new ShortVariable((short) (left.shortValue() * right.shortValue()));
        }

        @Override
        public Variable negateVariable(Variable value) {
            return new ShortVariable((short) -value.shortValue());
        }

        @Override
        public Variable andVariables(Variable left, Variable right) {
            return new ShortVariable((short) (left.shortValue() & right.shortValue()));
        }

        @Override
        public Variable shiftVariablesLeft(Variable left, int right) {
            return new ShortVariable((short) (left.shortValue() << right));
        }

        @Override
        public Variable orVariables(Variable left, Variable right) {
            return new ShortVariable((short) (left.shortValue() | right.shortValue()));
        }

        @Override
        public Variable shiftVariablesRight(Variable left, int right) {
            return new ShortVariable((short) (left.shortValue() >> right));
        }

        @Override
        public Variable xorVariables(Variable left, Variable right) {
            return new ShortVariable((short) (left.shortValue() ^ right.shortValue()));
        }

        @Override
        public Variable divideVariables(Variable left, Variable right) {
            return new ShortVariable((short) (left.shortValue() / right.shortValue()));
        }

        @Override
        public Variable subtractVariables(Variable left, Variable right) {
            return new ShortVariable((short) (left.shortValue() - right.shortValue()));
        }
    }
}
