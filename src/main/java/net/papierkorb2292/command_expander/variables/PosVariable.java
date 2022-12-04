package net.papierkorb2292.command_expander.variables;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.papierkorb2292.command_expander.variables.immediate.operator.AddableOperatorVariableType;
import net.papierkorb2292.command_expander.variables.immediate.operator.MultipliableOperatorVariableType;
import net.papierkorb2292.command_expander.variables.immediate.operator.NegatableOperatorVariableType;
import net.papierkorb2292.command_expander.variables.immediate.operator.SubtractableOperatorVariableType;

import java.util.List;

public class PosVariable extends Variable {

    private final DoubleVariable x;
    private final DoubleVariable y;
    private final DoubleVariable z;

    public PosVariable() {
        this(0, 0, 0);
    }
    public PosVariable(double x, double y, double z) {
        this.x = new DoubleVariable(x);
        this.y = new DoubleVariable(y);
        this.z = new DoubleVariable(z);
    }

    @Override
    public int intValue() {
        return (int)doubleValue();
    }

    @Override
    public long longValue() {
        return (long)doubleValue();
    }

    @Override
    public float floatValue() {
        return (float)doubleValue();
    }

    @Override
    public double doubleValue() {
        double x = this.x.doubleValue();
        double y = this.y.doubleValue();
        double z = this.z.doubleValue();
        return x * x + y * y + z * z;
    }

    @Override
    public String stringValue() {
        return String.format("(pos){ %s, %s, %s }", x.doubleValue(), y.doubleValue(), z.doubleValue());
    }

    @Override
    public VariableType getType() {
        return PosVariableType.INSTANCE;
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof PosVariable pos)) {
            return false;
        }
        return x.equals(pos.x) && y.equals(pos.y) && z.equals(pos.z);
    }

    @Override
    public int hashCode() {
        return Double.hashCode(x.doubleValue()) ^ Double.hashCode(y.doubleValue()) ^ Double.hashCode(z.doubleValue());
    }

    @Override
    public NbtElement toNbt() throws CommandSyntaxException {
        NbtList result = new NbtList();
        result.add(NbtDouble.of(x.doubleValue()));
        result.add(NbtDouble.of(y.doubleValue()));
        result.add(NbtDouble.of(z.doubleValue()));
        return result;
    }

    public Variable getX() {
        return x;
    }

    public Variable getY() {
        return y;
    }

    public Variable getZ() {
        return z;
    }

    public void setX(Variable value) {
        x.setValue(value.doubleValue());
    }

    public void setY(Variable value) {
        y.setValue(value.doubleValue());
    }

    public void setZ(Variable value) {
        z.setValue(value.doubleValue());
    }

    public static class PosVariableType implements VariableType, AddableOperatorVariableType, SubtractableOperatorVariableType, NegatableOperatorVariableType, MultipliableOperatorVariableType {

        public static final PosVariableType INSTANCE = new PosVariableType();

        @Override
        public Variable createVariable() {
            return new PosVariable();
        }

        public static final VariableTypeTemplate TEMPLATE = new VariableTypeTemplate(0, () -> INSTANCE, (type, var) -> {
            if(var instanceof PosVariable pos) {
                return new PosVariable(pos.x.doubleValue(), pos.y.doubleValue(), pos.z.doubleValue());
            }
            if(!(var instanceof ListVariable list)) {
                throw VariableManager.INCOMPATIBLE_TYPES_EXCEPTION.create(type.asString(), var == null ? "null" : var.getType().asString());
            }
            if(list.value.size() != 3) {
                throw VariableManager.PARSE_EXCEPTION.create(var.stringValue(), type.asString());
            }
            Variable
                    x = list.value.get(0),
                    y = list.value.get(1),
                    z = list.value.get(2);
            return new PosVariable(x == null ? 0 : x.doubleValue(), y == null ? 0 : y.doubleValue(), z == null ? 0 : z.doubleValue());
        }, new VariableCodec() {
            @Override
            protected <T> DataResult<Pair<Variable, T>> read(DynamicOps<T> ops, T input, VariableType type) {
                return ops.getStream(input).flatMap(stream -> {
                    List<T> list = stream.toList();
                    if(list.size() != 3) {
                        return DataResult.error("Pos element list has invalid size");
                    }
                    return ops.getNumberValue(list.get(0)).flatMap(
                            x -> ops.getNumberValue(list.get(1)).flatMap(
                                    y -> ops.getNumberValue(list.get(2)).map(
                                            z -> Pair.of(new PosVariable(
                                                    x.doubleValue(), y.doubleValue(), z.doubleValue()),
                                                    ops.empty()))));
                });
            }

            @Override
            protected <T> DataResult<T> write(Variable input, DynamicOps<T> ops, T prefix) {
                PosVariable pos = ((PosVariable) input);
                return ops.listBuilder()
                        .add(ops.createDouble(pos.x.doubleValue()))
                        .add(ops.createDouble(pos.y.doubleValue()))
                        .add(ops.createDouble(pos.z.doubleValue()))
                        .build(prefix);
            }
        });

        @Override
        public VariableTypeTemplate getTemplate() {
            return TEMPLATE;
        }

        @Override
        public VariableType getNextLoweredType() {
            return DoubleVariable.DoubleVariableType.INSTANCE;
        }

        @Override
        public String getName() {
            return "pos";
        }

        @Override
        public Variable addVariables(Variable left, Variable right) {
            PosVariable posLeft = (PosVariable)left;
            PosVariable posRight = (PosVariable)right;
            return new PosVariable(
                    posLeft.x.doubleValue() + posRight.x.doubleValue(),
                    posLeft.y.doubleValue() + posRight.y.doubleValue(),
                    posLeft.z.doubleValue() + posRight.z.doubleValue()
            );
        }

        @Override
        public Variable multiplyVariables(Variable left, Variable right) {
            PosVariable posLeft = (PosVariable)left;
            PosVariable posRight = (PosVariable)right;
            return new PosVariable(
                    posLeft.x.doubleValue() * posRight.x.doubleValue(),
                    posLeft.y.doubleValue() * posRight.y.doubleValue(),
                    posLeft.z.doubleValue() * posRight.z.doubleValue()
            );
        }

        @Override
        public Variable negateVariable(Variable value) {
            PosVariable pos = (PosVariable)value;
            return new PosVariable(
                    -pos.x.doubleValue(),
                    -pos.y.doubleValue(),
                    -pos.z.doubleValue()
            );
        }

        @Override
        public Variable subtractVariables(Variable left, Variable right) {
            PosVariable posLeft = (PosVariable)left;
            PosVariable posRight = (PosVariable)right;
            return new PosVariable(
                    posLeft.x.doubleValue() - posRight.x.doubleValue(),
                    posLeft.y.doubleValue() - posRight.y.doubleValue(),
                    posLeft.z.doubleValue() - posRight.z.doubleValue()
            );
        }

        public static PosVariable calcCross(PosVariable left, PosVariable right) {
            double xl = left.x.doubleValue();
            double yl = left.y.doubleValue();
            double zl = left.z.doubleValue();
            double xr = right.x.doubleValue();
            double yr = right.y.doubleValue();
            double zr = right.z.doubleValue();

            return new PosVariable(yl * zr - zl * yr, zl * xr - xl * zr, xl * yr - yl * xr);
        }

        public static PosVariable calcNormalize(PosVariable value) {
            double x = value.x.doubleValue();
            double y = value.y.doubleValue();
            double z = value.z.doubleValue();
            double scale = MathHelper.fastInverseSqrt(x * x + y * y + z * z);
            return new PosVariable(
                    x * scale,
                    y * scale,
                    z * scale
            );
        }

        public static DoubleVariable calcDot(PosVariable left, PosVariable right) {
            return new DoubleVariable(
                    left.x.doubleValue() * right.x.doubleValue() + left.y.doubleValue() * right.y.doubleValue() + left.z.doubleValue() * right.z.doubleValue());
        }
    }
}
