package net.papierkorb2292.command_expander.variables;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtElement;
import net.papierkorb2292.command_expander.variables.immediate.operator.IntegerOperatorVariableType;

public class ByteVariable extends CriteriaBindableNumberVariable {

    private byte value = 0;

    public ByteVariable() { }
    public ByteVariable(byte value) {
        this.value = value;
    }

    public void setValue(byte value) {
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
        return String.valueOf(value) + 'B';
    }

    @Override
    public VariableType getType() {
        return ByteVariableType.INSTANCE;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ByteVariable && ((ByteVariable)o).value == value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public NbtElement toNbt() {
        return NbtByte.of(value);
    }

    public static ByteVariable parse(String value) {
        if(value.length() == 0) {
            return new ByteVariable((byte) 0);
        }
        return new ByteVariable(Byte.parseByte(value));
    }

    @Override
    public void add(int value) {
        this.value += value;
    }

    @Override
    public void set(int value) {
        this.value = (byte)value;
    }

    public static class ByteVariableType implements CriteriaBindableNumberVariableType, IntegerOperatorVariableType {

        public static final ByteVariableType INSTANCE = new ByteVariableType();

        @Override
        public IntVariable createVariable() {
            return new IntVariable();
        }

        public VariableTypeTemplate getTemplate() {
            return TEMPLATE;
        }

        @Override
        public VariableType getNextLoweredType() {
            return ShortVariable.ShortVariableType.INSTANCE;
        }

        @Override
        public String getName() {
            return "byte";
        }

        public static final VariableTypeTemplate TEMPLATE = new VariableTypeTemplate(
                0, () -> INSTANCE,
                (type, var) -> new ByteVariable(var.byteValue()),
                new VariableCodec() {

                    @Override
                    public <T> DataResult<T> write(Variable input, DynamicOps<T> ops, T prefix) {
                        return ops.mergeToPrimitive(prefix, ops.createByte(input.byteValue()));
                    }

                    @Override
                    public <T> DataResult<Pair<Variable, T>> read(DynamicOps<T> ops, T input, VariableType type) {
                        return ops.getNumberValue(input).map(value -> Pair.of(new ByteVariable(value.byteValue()), ops.empty()));
                    }
                });

        @Override
        public Variable addVariables(Variable left, Variable right) {
            return new ByteVariable((byte) (left.byteValue() + right.byteValue()));
        }

        @Override
        public Variable multiplyVariables(Variable left, Variable right) {
            return new ByteVariable((byte) (left.byteValue() * right.byteValue()));
        }

        @Override
        public Variable negateVariable(Variable value) {
            return new ByteVariable((byte) -value.byteValue());
        }

        @Override
        public Variable andVariables(Variable left, Variable right) {
            return new ByteVariable((byte) (left.byteValue() & right.byteValue()));
        }

        @Override
        public Variable shiftVariablesLeft(Variable left, int right) {
            return new ByteVariable((byte) (left.byteValue() << right));
        }

        @Override
        public Variable orVariables(Variable left, Variable right) {
            return new ByteVariable((byte) (left.byteValue() | right.byteValue()));
        }

        @Override
        public Variable shiftVariablesRight(Variable left, int right) {
            return new ByteVariable((byte) (left.byteValue() >> right));
        }

        @Override
        public Variable xorVariables(Variable left, Variable right) {
            return new ByteVariable((byte) (left.byteValue() ^ right.byteValue()));
        }

        @Override
        public Variable divideVariables(Variable left, Variable right) {
            return new ByteVariable((byte) (left.byteValue() / right.byteValue()));
        }

        @Override
        public Variable subtractVariables(Variable left, Variable right) {
            return new ByteVariable((byte) (left.byteValue() - right.byteValue()));
        }
    }
}
