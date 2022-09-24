package net.papierkorb2292.command_expander.variables;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.util.math.Vec3d;
import net.papierkorb2292.command_expander.variables.immediate.operator.AddableOperatorVariableType;
import net.papierkorb2292.command_expander.variables.immediate.operator.MultipliableOperatorVariableType;
import net.papierkorb2292.command_expander.variables.immediate.operator.NegatableOperatorVariableType;
import net.papierkorb2292.command_expander.variables.immediate.operator.SubtractableOperatorVariableType;

import java.util.List;

public class PosVariable extends Variable {

    private Vec3d pos = Vec3d.ZERO;

    public PosVariable() { }
    public PosVariable(Vec3d pos) {
        this.pos = pos;
    }

    public void set(Vec3d pos) {
        this.pos = pos;
    }

    @Override
    public int intValue() {
        return (int)pos.lengthSquared();
    }

    @Override
    public long longValue() {
        return (long)pos.lengthSquared();
    }

    @Override
    public float floatValue() {
        return (float)pos.lengthSquared();
    }

    @Override
    public double doubleValue() {
        return pos.lengthSquared();
    }

    @Override
    public String stringValue() {
        return String.format("(pos){ %s, %s, %s }", pos.x, pos.y, pos.z);
    }

    @Override
    public VariableType getType() {
        return PosVariableType.INSTANCE;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PosVariable && ((PosVariable)o).pos.equals(pos);
    }

    @Override
    public int hashCode() {
        return pos.hashCode();
    }

    public static class PosVariableType implements VariableType, AddableOperatorVariableType, SubtractableOperatorVariableType, NegatableOperatorVariableType, MultipliableOperatorVariableType {

        public static final PosVariableType INSTANCE = new PosVariableType();

        @Override
        public Variable createVariable() {
            return new PosVariable();
        }

        public static final VariableTypeTemplate TEMPLATE = new VariableTypeTemplate(0, () -> INSTANCE, (type, var) -> {
            if(var instanceof PosVariable pos) {
                return new PosVariable(pos.pos);
            }
            if(!(var instanceof ListVariable list)) {
                throw VariableManager.INCOMPATIBLE_TYPES_EXCEPTION.create(type.asString(), var == null ? "null" : var.getType().asString());
            }
            if(list.value.size() != 3) {
                throw VariableManager.PARSE_EXCEPTION.create(var.stringValue(), type);
            }
            Variable
                    x = list.value.get(0),
                    y = list.value.get(1),
                    z = list.value.get(2);
            return new PosVariable(new Vec3d(x == null ? 0 : x.doubleValue(), y == null ? 0 : y.doubleValue(), z == null ? 0 : z.doubleValue()));
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
                                                    new Vec3d(
                                                            x.doubleValue(),
                                                            y.doubleValue(),
                                                            z.doubleValue())),
                                                    ops.empty()))));
                });
            }

            @Override
            protected <T> DataResult<T> write(Variable input, DynamicOps<T> ops, T prefix) {
                Vec3d pos = ((PosVariable) input).pos;
                return ops.listBuilder()
                        .add(ops.createDouble(pos.x))
                        .add(ops.createDouble(pos.y))
                        .add(ops.createDouble(pos.z))
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
            return new PosVariable(((PosVariable)left).pos.add(((PosVariable)right).pos));
        }

        @Override
        public Variable multiplyVariables(Variable left, Variable right) {
            return new PosVariable(((PosVariable)left).pos.multiply(((PosVariable)right).pos));
        }

        @Override
        public Variable negateVariable(Variable value) {
            return new PosVariable(((PosVariable)value).pos.negate());
        }

        @Override
        public Variable subtractVariables(Variable left, Variable right) {
            return new PosVariable(((PosVariable)left).pos.subtract(((PosVariable)right).pos));
        }

        public static PosVariable calcCross(PosVariable left, PosVariable right) {
            return new PosVariable(left.pos.crossProduct(right.pos));
        }

        public static PosVariable calcNormalize(PosVariable value) {
            return new PosVariable(value.pos.normalize());
        }

        public static DoubleVariable calcDot(PosVariable left, PosVariable right) {
            return new DoubleVariable(left.pos.dotProduct(right.pos));
        }
    }
}
