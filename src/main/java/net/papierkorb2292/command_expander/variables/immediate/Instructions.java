package net.papierkorb2292.command_expander.variables.immediate;

import com.google.common.collect.AbstractIterator;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.floats.FloatBinaryOperator;
import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import net.minecraft.command.EntitySelector;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.math.MathHelper;
import net.papierkorb2292.command_expander.mixin_method_interfaces.VariableManagerContainer;
import net.papierkorb2292.command_expander.variables.*;
import net.papierkorb2292.command_expander.variables.immediate.operator.*;
import net.papierkorb2292.command_expander.variables.path.PathChildrenAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class Instructions {

    private Instructions() { } //Prevent instantiations

    public static final Instruction SIN = getDoubleUnaryInstruction(Math::sin);
    public static final Instruction COS = getDoubleUnaryInstruction(Math::cos);
    public static final Instruction TAN = getDoubleUnaryInstruction(Math::tan);
    public static final Instruction ASIN = getDoubleUnaryInstruction(Math::asin);
    public static final Instruction ACOS = getDoubleUnaryInstruction(Math::acos);
    public static final Instruction ATAN = getDoubleUnaryInstruction(Math::atan);
    public static final Instruction TO_RADIANS = getDoubleUnaryInstruction(Math::toRadians);
    public static final Instruction TO_DEGREES = getDoubleUnaryInstruction(Math::toDegrees);
    public static final Instruction EXP = getDoubleUnaryInstruction(Math::exp);
    public static final Instruction LOG = getDoubleUnaryInstruction(Math::log);
    public static final Instruction LOG10 = getDoubleUnaryInstruction(Math::log10);
    public static final Instruction SQRT = getDoubleUnaryInstruction(Math::sqrt);
    public static final Instruction CBRT = getDoubleUnaryInstruction(Math::cbrt);
    public static final Instruction CEIL = getDoubleUnaryInstruction(Math::ceil);
    public static final Instruction FLOOR = getDoubleUnaryInstruction(Math::floor);
    public static final Instruction RINT = getDoubleUnaryInstruction(Math::rint);
    public static final Instruction SINH = getDoubleUnaryInstruction(Math::sinh);
    public static final Instruction COSH = getDoubleUnaryInstruction(Math::cosh);
    public static final Instruction TANH = getDoubleUnaryInstruction(Math::tanh);
    public static final Instruction EXPM1 = getDoubleUnaryInstruction(Math::expm1);
    public static final Instruction LOG1P = getDoubleUnaryInstruction(Math::log1p);

    public static Instruction getDoubleUnaryInstruction(DoubleUnaryOperator operator) {
        return new DoubleUnaryInstruction(operator);
    }

    public static class DoubleUnaryInstruction extends Instruction {

        private final DoubleUnaryOperator operator;

        public DoubleUnaryInstruction(DoubleUnaryOperator operator) {
            super(1, 1, false);
            this.operator = operator;
        }

        @Override
        public void apply(CalculationContext context) {
            Deque<Either<VariableHolder, Stream<Variable>>> stack = context.stack();
            stack.push(stack.pop().mapBoth(
                    holder -> new VariableHolder(calcOp(holder.variable)),
                    varStream -> varStream.map(this::calcOp)));
        }

        private DoubleVariable calcOp(Variable var) {
            if(var == null ||!var.getType().instanceOf(DoubleVariable.DoubleVariableType.INSTANCE)) {
                return new DoubleVariable();
            }
            return new DoubleVariable(operator.applyAsDouble(var.doubleValue()));
        }
    }

    public static final Instruction IEEE_REMAINDER = getDoubleBinaryInstruction(Math::IEEEremainder);
    public static final Instruction ATAN2 = getDoubleBinaryInstruction(Math::atan2);
    public static final Instruction POW = getDoubleBinaryInstruction(Math::pow);
    public static final Instruction HYPOT = getDoubleBinaryInstruction(Math::hypot);

    public static Instruction getDoubleBinaryInstruction(DoubleBinaryOperator operator) {
        return new DoubleBinaryInstruction(operator);
    }

    public static class DoubleBinaryInstruction extends Instruction {

        private final ImmediateValue.CommandBiFunction applyFunction;

        public DoubleBinaryInstruction(DoubleBinaryOperator op) {
            super(2, 1, false);
            this.applyFunction = (l, r) -> {
                if(l == null || r == null) {
                    return null;
                }
                return new DoubleVariable(op.applyAsDouble(l.doubleValue(), r.doubleValue()));
            };
        }

        @Override
        public void apply(CalculationContext context) {
            applyToTwoParameters(context, applyFunction);
        }
    }

    public static final Instruction ROUND = getFloatingPointUnaryInstruction(Math::round, Math::round);
    public static final Instruction NEXT_UP = getFloatingPointUnaryInstruction(Math::nextUp, Math::nextUp);
    public static final Instruction NEXT_DOWN = getFloatingPointUnaryInstruction(Math::nextDown, Math::nextDown);
    public static final Instruction GET_EXPONENT = getFloatingPointUnaryInstruction(Math::getExponent, Math::getExponent);
    public static final Instruction ULP = getFloatingPointUnaryInstruction(Math::ulp, Math::ulp);
    public static final Instruction SIGNUM = getFloatingPointUnaryInstruction(Math::signum, Math::signum);

    public static Instruction getFloatingPointUnaryInstruction(FloatUnaryOperator floatOp, DoubleUnaryOperator doubleOp) {
        return new FloatingPointUnaryInstruction(floatOp, doubleOp);
    }

    public static class FloatingPointUnaryInstruction extends Instruction {

        private final FloatUnaryOperator floatOp;
        private final DoubleUnaryOperator doubleOp;

        public FloatingPointUnaryInstruction(FloatUnaryOperator floatOp, DoubleUnaryOperator doubleOp) {
            super(1, 1, false);
            this.floatOp = floatOp;
            this.doubleOp = doubleOp;
        }

        @Override
        public void apply(CalculationContext context) {
            Deque<Either<VariableHolder, Stream<Variable>>> stack = context.stack();
            stack.push(stack.pop().mapBoth(
                    holder -> new VariableHolder(calcOp(holder.variable)),
                    varStream -> varStream.map(this::calcOp)));
        }

        private Variable calcOp(Variable var) {
            if(var == null || !var.getType().instanceOf(DoubleVariable.DoubleVariableType.INSTANCE)) {
                return null;
            }
            if(var.getType().instanceOf(FloatVariable.FloatVariableType.INSTANCE)) {
                return new FloatVariable(floatOp.apply(var.floatValue()));
            }
            return new DoubleVariable(doubleOp.applyAsDouble(var.doubleValue()));
        }
    }

    public static final Instruction COPY_SIGN = getFloatingPointBinaryInstruction(Math::copySign, Math::copySign);
    public static final Instruction NEXT_AFTER = getFloatingPointBinaryInstruction(Math::nextAfter, Math::nextAfter);

    public static Instruction getFloatingPointBinaryInstruction(FloatBinaryOperator floatOp, DoubleBinaryOperator doubleOp) {
        return new FloatingPointBinaryInstruction(floatOp, doubleOp);
    }

    public static class FloatingPointBinaryInstruction extends Instruction {

        private final ImmediateValue.CommandBiFunction applyFunction;

        public FloatingPointBinaryInstruction(FloatBinaryOperator floatOp, DoubleBinaryOperator doubleOp) {
            super(2, 1, false);
            applyFunction = (l, r) -> {
                if(l == null || !l.getType().instanceOf(DoubleVariable.DoubleVariableType.INSTANCE) || r == null || !r.getType().instanceOf(DoubleVariable.DoubleVariableType.INSTANCE)) {
                    return null;
                }
                if(l.getType().instanceOf(FloatVariable.FloatVariableType.INSTANCE) && r.getType().instanceOf(FloatVariable.FloatVariableType.INSTANCE)) {
                    return new FloatVariable(floatOp.apply(l.floatValue(), r.floatValue()));
                }
                return new DoubleVariable(doubleOp.applyAsDouble(l.doubleValue(), r.doubleValue()));
            };
        }

        @Override
        public void apply(CalculationContext context) {
            applyToTwoParameters(context, applyFunction);
        }
    }

    public static final Instruction FLOOR_DIV = getIntegerBinaryInstruction(Math::floorDiv, Math::floorDiv);
    public static final Instruction FLOOR_MOD = getIntegerBinaryInstruction(Math::floorMod, Math::floorMod);

    public static Instruction getIntegerBinaryInstruction(IntBinaryOperator intOp, LongBinaryOperator longOp) {
        return new IntegerBinaryInstruction(intOp, longOp);
    }

    public static class IntegerBinaryInstruction extends Instruction {

        private final ImmediateValue.CommandBiFunction applyFunction;

        public IntegerBinaryInstruction(IntBinaryOperator intOp, LongBinaryOperator longOp) {
            super(2, 1, false);
            applyFunction = (l, r) -> {
                if(l == null || !l.getType().instanceOf(LongVariable.LongVariableType.INSTANCE) || r == null || !r.getType().instanceOf(LongVariable.LongVariableType.INSTANCE)) {
                    return null;
                }
                if(l.getType().instanceOf(IntVariable.IntVariableType.INSTANCE) && r.getType().instanceOf(IntVariable.IntVariableType.INSTANCE)) {
                    return new IntVariable(intOp.applyAsInt(l.intValue(), r.intValue()));
                }
                return new LongVariable(longOp.applyAsLong(l.longValue(), r.longValue()));
            };
        }

        @Override
        public void apply(CalculationContext context) {
            applyToTwoParameters(context, applyFunction);
        }
    }

    public static final Instruction MIN = getStandardNumberBinaryInstruction(Math::min, Math::min, Math::min, Math::min);
    public static final Instruction MAX = getStandardNumberBinaryInstruction(Math::max, Math::max, Math::max, Math::max);

    public static Instruction getStandardNumberBinaryInstruction(IntBinaryOperator intOp, LongBinaryOperator longOp, FloatBinaryOperator floatOp, DoubleBinaryOperator doubleOp) {
        return new StandardNumberBinaryInstruction(intOp, longOp, floatOp, doubleOp);
    }

    public static class StandardNumberBinaryInstruction extends Instruction {

        private final ImmediateValue.CommandBiFunction applyFunction;

        public StandardNumberBinaryInstruction(IntBinaryOperator intOp, LongBinaryOperator longOp, FloatBinaryOperator floatOp, DoubleBinaryOperator doubleOp) {
            super(2, 1, false);
            applyFunction = (l, r) -> {
                if(l == null || !l.getType().instanceOf(DoubleVariable.DoubleVariableType.INSTANCE) || r == null || !r.getType().instanceOf(DoubleVariable.DoubleVariableType.INSTANCE)) {
                    return null;
                }
                if(l.getType().instanceOf(IntVariable.IntVariableType.INSTANCE) && r.getType().instanceOf(IntVariable.IntVariableType.INSTANCE)) {
                    return new IntVariable(intOp.applyAsInt(l.intValue(), r.intValue()));
                }
                if(l.getType().instanceOf(LongVariable.LongVariableType.INSTANCE) && r.getType().instanceOf(LongVariable.LongVariableType.INSTANCE)) {
                    return new LongVariable(longOp.applyAsLong(l.longValue(), r.longValue()));
                }
                if(l.getType().instanceOf(FloatVariable.FloatVariableType.INSTANCE) && r.getType().instanceOf(FloatVariable.FloatVariableType.INSTANCE)) {
                    return new FloatVariable(floatOp.apply(l.floatValue(), r.floatValue()));
                }
                return new DoubleVariable(doubleOp.applyAsDouble(l.doubleValue(), r.doubleValue()));
            };
        }

        @Override
        public void apply(CalculationContext context) {
            applyToTwoParameters(context, applyFunction);
        }
    }

    public static final Instruction FMA = getFloatingPointTernayInstruction(Math::fma, Math::fma, "fma");
    public static final Instruction LERP = getFloatingPointTernayInstruction(MathHelper::lerp, MathHelper::lerp, "lerp");
    public static final Instruction GET_LERP_PROGRESS = getFloatingPointTernayInstruction(MathHelper::getLerpProgress, MathHelper::getLerpProgress, "lerpProgress");

    public static Instruction getFloatingPointTernayInstruction(FloatingPointTernaryInstruction.FloatTernaryOperator floatOp, FloatingPointTernaryInstruction.DoubleTernaryOperator doubleOp, String name) {
        return new FloatingPointTernaryInstruction(floatOp, doubleOp, name);
    }

    public static class FloatingPointTernaryInstruction extends Instruction {

        private final FloatTernaryOperator floatOp;
        private final DoubleTernaryOperator doubleOp;
        private final String name;

        public FloatingPointTernaryInstruction(FloatTernaryOperator floatOp, DoubleTernaryOperator doubleOp, String name) {
            super(3, 1, false);
            this.floatOp = floatOp;
            this.doubleOp = doubleOp;
            this.name = name;
        }

        @Override
        public void apply(CalculationContext context) throws CommandSyntaxException {
            Deque<Either<VariableHolder, Stream<Variable>>> stack = context.stack();
            Either<VariableHolder, Stream<Variable>> c = stack.pop(), b = stack.pop(), a = stack.pop();
            if (a.left().isPresent()) {
                Variable aVar = a.left().get().variable;
                if (b.left().isPresent()) {
                    Variable bVar = b.left().get().variable;
                    if (c.left().isPresent()) {
                        stack.push(Either.left(new VariableHolder(calcOp(aVar, bVar, c.left().get().variable))));
                        return;
                    }
                    if(c.right().isEmpty()) {
                        throw new IllegalStateException("First (c) element of stack was an invalid Either when '" + name + "' was called. Neither left nor right were present");
                    }
                    stack.push(Either.right(c.right().get().map(cVar -> {
                        try {
                            return calcOp(aVar, bVar, cVar);
                        } catch (CommandSyntaxException e) {
                            context.errorConsumer().accept(Texts.toText(e.getRawMessage()));
                            return null;
                        }
                    }).filter(Objects::nonNull)));
                    return;
                }
                if(b.right().isEmpty()) {
                    throw new IllegalStateException("Second (b) element of stack was an invalid Either when '" + name + "' was called. Neither left nor right were present");
                }
                Stream<Variable> bStream = b.right().get();
                stack.push(Either.right(c.map(
                        cHolder -> {
                            Variable cVar = cHolder.variable;
                            return bStream.map(bVar -> {
                                try {
                                    return calcOp(aVar, bVar, cVar);
                                } catch (CommandSyntaxException e) {
                                    context.errorConsumer().accept(Texts.toText(e.getRawMessage()));
                                    return null;
                                }
                            }).filter(Objects::nonNull);
                        },
                        cStream -> {
                            Iterator<Variable> bIterator = bStream.iterator(), cIterator = cStream.iterator();
                            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new AbstractIterator<>() {
                                @Nullable
                                @Override
                                protected Variable computeNext() {
                                    while (true) {
                                        if (!(bIterator.hasNext() && cIterator.hasNext())) {
                                            return endOfData();
                                        }
                                        try {
                                            return calcOp(aVar, bIterator.next(), cIterator.next());
                                        } catch (CommandSyntaxException e) {
                                            context.errorConsumer().accept(Texts.toText(e.getRawMessage()));
                                        }
                                    }
                                }
                            }, 0), false);
                        }
                )));
            }
            if(a.right().isEmpty()) {
                throw new IllegalArgumentException("Third (a) element of stack was an invalid Either when '" + name + "' was called. Neither left nor right were present");
            }
            Stream<Variable> aStream = a.right().get();
            context.stack().push(Either.right(b.map(
                    bHolder -> {
                        Variable bVar = bHolder.variable;
                        return c.map(
                                cHolder -> {
                                    Variable cVar = cHolder.variable;
                                    return aStream.map(aVar -> {
                                        try {
                                            return calcOp(aVar, bVar, cVar);
                                        } catch (CommandSyntaxException e) {
                                            context.errorConsumer().accept(Texts.toText(e.getRawMessage()));
                                            return null;
                                        }
                                    }).filter(Objects::nonNull);
                                },
                                cStream -> {
                                    Iterator<Variable> aIterator = aStream.iterator(), cIterator = cStream.iterator();
                                    return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new AbstractIterator<>() {
                                        @Nullable
                                        @Override
                                        protected Variable computeNext() {
                                            while (true) {
                                                if (!(aIterator.hasNext() && cIterator.hasNext())) {
                                                    return endOfData();
                                                }
                                                try {
                                                    return calcOp(aIterator.next(), bVar, cIterator.next());
                                                } catch (CommandSyntaxException e) {
                                                    context.errorConsumer().accept(Texts.toText(e.getRawMessage()));
                                                }
                                            }
                                        }
                                    }, 0), false);
                                }
                        );
                    },
                    bStream -> c.map(
                            cHolder -> {
                                Iterator<Variable> aIterator = aStream.iterator(), bIterator = bStream.iterator();
                                Variable cVar = cHolder.variable;
                                return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new AbstractIterator<>() {
                                    @Nullable
                                    @Override
                                    protected Variable computeNext() {
                                        while (true) {
                                            if (!(aIterator.hasNext() && bIterator.hasNext())) {
                                                return endOfData();
                                            }
                                            try {
                                                return calcOp(aIterator.next(), bIterator.next(), cVar);
                                            } catch (CommandSyntaxException e) {
                                                context.errorConsumer().accept(Texts.toText(e.getRawMessage()));
                                            }
                                        }
                                    }
                                }, 0), false);
                            },
                            cStream -> {
                                Iterator<Variable> aIterator = aStream.iterator(), bIterator = bStream.iterator(), cIterator = cStream.iterator();
                                return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new AbstractIterator<>() {
                                    @Nullable
                                    @Override
                                    protected Variable computeNext() {
                                        while (true) {
                                            if (!(aIterator.hasNext() && bIterator.hasNext() && cIterator.hasNext())) {
                                                return endOfData();
                                            }
                                            try {
                                                return calcOp(aIterator.next(), bIterator.next(), cIterator.next());
                                            } catch (CommandSyntaxException e) {
                                                context.errorConsumer().accept(Texts.toText(e.getRawMessage()));
                                            }
                                        }
                                    }
                                }, 0), false);
                            }
                    )
            )));
        }

        private Variable calcOp(Variable a, Variable b, Variable c) throws CommandSyntaxException {
            if(a == null || !a.getType().instanceOf(DoubleVariable.DoubleVariableType.INSTANCE)
                    || b == null || !b.getType().instanceOf(DoubleVariable.DoubleVariableType.INSTANCE)
                    || c == null || !c.getType().instanceOf(DoubleVariable.DoubleVariableType.INSTANCE)) {
                throw INCOMPATIBLE_TYPES_EXCEPTION.create();
            }
            if(a.getType().instanceOf(FloatVariable.FloatVariableType.INSTANCE)
                    && b.getType().instanceOf(FloatVariable.FloatVariableType.INSTANCE)
                    && c.getType().instanceOf(FloatVariable.FloatVariableType.INSTANCE)) {
                return new FloatVariable(floatOp.apply(a.floatValue(), b.floatValue(), c.floatValue()));
            }
            return new DoubleVariable(doubleOp.apply(a.doubleValue(), b.doubleValue(), c.doubleValue()));
        }

        @FunctionalInterface
        public interface FloatTernaryOperator {
            float apply(float a, float b, float c);
        }

        @FunctionalInterface
        public interface DoubleTernaryOperator {
            double apply(double a, double b, double c);
        }
    }

    public static final Instruction ABS = new Instruction(2, 1, false) {
        @Override
        public void apply(CalculationContext context) {
            Deque<Either<VariableHolder, Stream<Variable>>> stack = context.stack();
            stack.push(stack.pop().mapBoth(
                    holder -> new VariableHolder(calcOp(holder.variable)),
                    varStream -> varStream.map(this::calcOp)));
        }

        private Variable calcOp(Variable var) {
            if(var == null || !var.getType().instanceOf(DoubleVariable.DoubleVariableType.INSTANCE)) {
                return null;
            }
            if(var.getType().instanceOf(IntVariable.IntVariableType.INSTANCE)) {
                return new IntVariable(Math.abs(var.intValue()));
            }
            if(var.getType().instanceOf(LongVariable.LongVariableType.INSTANCE)) {
                return new LongVariable(Math.abs(var.longValue()));
            }
            if(var.getType().instanceOf(FloatVariable.FloatVariableType.INSTANCE)) {
                return new FloatVariable(Math.abs(var.floatValue()));
            }
            return new DoubleVariable(Math.abs(var.doubleValue()));
        }
    };

    public static final Instruction MUTLIPLY_HIGH = new Instruction(2, 1, false) {

        @Override
        public void apply(CalculationContext context) {
            applyToTwoParameters(context, (l, r) -> {
                if(l == null || !l.getType().instanceOf(LongVariable.LongVariableType.INSTANCE) || r == null || !r.getType().instanceOf(LongVariable.LongVariableType.INSTANCE)) {
                    return null;
                }
                return new LongVariable(Math.multiplyHigh(l.longValue(), r.longValue()));
            });
        }
    };

    public static final Instruction SCALB = new Instruction(2, 1, false) {

        @Override
        public void apply(CalculationContext context) {
            applyToTwoParameters(context, (l, r) -> {
                if(r == null || !r.getType().instanceOf(DoubleVariable.DoubleVariableType.INSTANCE) || l == null || !l.getType().instanceOf(IntVariable.IntVariableType.INSTANCE)) {
                    return null;
                }
                if(l.getType().instanceOf(FloatVariable.FloatVariableType.INSTANCE)) {
                    return new FloatVariable(Math.scalb(l.floatValue(), r.intValue()));
                }
                return new DoubleVariable(Math.scalb(l.doubleValue(), r.intValue()));
            });
        }
    };

    public static final Instruction TO_STRING = new Instruction(1, 1, false) {
        @Override
        public void apply(CalculationContext context) {
            context.stack().push(context.stack().pop().mapBoth(
                    holder -> new VariableHolder(calcString(holder.variable)),
                    varStream -> varStream.map(this::calcString)));
        }

        private Variable calcString(Variable var) {
            return new StringVariable(var.stringValue());
        }
    };

    public static final Instruction COLLECT = new Instruction(1, 1, false) {

        private static final IntVariable ZERO = new IntVariable();

        @Override
        public void apply(CalculationContext context) throws CommandSyntaxException {
            Either<VariableHolder, Stream<Variable>> content = context.stack().pop();
            if(content.left().isPresent()) {
                Variable left = content.left().get().variable;
                ListVariable result = new ListVariable(new ListVariable.ListVariableType(left == null ? null : left.getType()));
                result.ensureIndexExists(ZERO);
                result.set(ZERO, left);
                context.stack().push(Either.left(new VariableHolder(result)));
                return;
            }
            if(content.right().isEmpty()) {
                throw new IllegalStateException("Invalid Either on stack when 'collect' was called. Neither left nor right were present");
            }
            Variable[] vars = content.right().get().toArray(Variable[]::new);
            Variable.VariableType[] types = new Variable.VariableType[vars.length];
            for(int i = 0; i < vars.length; ++i) {
                Variable var = vars[i];
                types[i] = var == null ? null : var.getType();
            }
            Variable.VariableType.LoweredType contentType = Variable.VariableType.getLoweredType(types);
            if(contentType == null) {
                throw INCOMPATIBLE_TYPES_EXCEPTION.create();
            }
            ListVariable result = new ListVariable(new ListVariable.ListVariableType(contentType.type));
            IntVariable index = new IntVariable(vars.length - 1);
            result.ensureIndexExists(index);
            for(int i = 0; i < vars.length; ++i) {
                index.setValue(i);
                result.set(index, vars[i]);
            }
            context.stack().push(Either.left(new VariableHolder(result)));
        }
    };

    public static final Instruction WRAP_DEGREES = new Instruction(1, 1, false) {
        @Override
        public void apply(CalculationContext context) {
            Deque<Either<VariableHolder, Stream<Variable>>> stack = context.stack();
            stack.push(stack.pop().mapBoth(
                    holder -> new VariableHolder(calcOp(holder.variable)),
                    varStream -> varStream.map(this::calcOp)));
        }

        private Variable calcOp(Variable var) {
            if(var == null || !var.getType().instanceOf(DoubleVariable.DoubleVariableType.INSTANCE)) {
                return null;
            }
            if(var.getType().instanceOf(IntVariable.IntVariableType.INSTANCE)) {
                return new IntVariable(MathHelper.wrapDegrees(var.intValue()));
            }
            if(var.getType().instanceOf(FloatVariable.FloatVariableType.INSTANCE)) {
                return new FloatVariable(MathHelper.wrapDegrees(var.floatValue()));
            }
            return new DoubleVariable(MathHelper.wrapDegrees(var.doubleValue()));
        }
    };

    private static final SimpleCommandExceptionType INCOMPATIBLE_TYPES_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Encountered incompatible types: Unable to find compatible lowered type"));

    public static final Instruction ADD = getNumberOperatorInstruction(type -> type instanceof AddableOperatorVariableType, (type, left, right) -> ((AddableOperatorVariableType)type).addVariables(left, right));
    public static final Instruction MUL = getNumberOperatorInstruction(type -> type instanceof MultipliableOperatorVariableType, (type, left, right) -> ((MultipliableOperatorVariableType)type).multiplyVariables(left, right));
    public static final Instruction SUB = getNumberOperatorInstruction(type -> type instanceof SubtractableOperatorVariableType, (type, left, right) -> ((SubtractableOperatorVariableType)type).subtractVariables(left, right));
    public static final Instruction DIV = getNumberOperatorInstruction(type -> type instanceof DividableOperatorVariableType, (type, left, right) -> ((DividableOperatorVariableType)type).divideVariables(left, right));
    public static final Instruction OR = getNumberOperatorInstruction(type -> type instanceof BitwiseOrAbleOperatorVariableType, (type, left, right) -> ((BitwiseOrAbleOperatorVariableType)type).orVariables(left, right));
    public static final Instruction AND = getNumberOperatorInstruction(type -> type instanceof BitwiseAndAbleOperatorVariableType, (type, left, right) -> ((BitwiseAndAbleOperatorVariableType)type).andVariables(left, right));
    public static final Instruction XOR = getNumberOperatorInstruction(type -> type instanceof BitwiseXorAbleOperatorVariableType, (type, left, right) -> ((BitwiseXorAbleOperatorVariableType)type).xorVariables(left, right));

    public static Instruction getNumberOperatorInstruction(Predicate<Variable.VariableType> typePredicate, NumberOperatorInstruction.Operator operator) {
        return new NumberOperatorInstruction(typePredicate, operator);
    }

    public static class NumberOperatorInstruction extends Instruction {

        private final Predicate<Variable.VariableType> typePredicate;
        private final Operator operator;

        public NumberOperatorInstruction(Predicate<Variable.VariableType> typePredicate, Operator operator) {
            super(2, 1, false);
            this.typePredicate = typePredicate;
            this.operator = operator;
        }

        @Override
        public void apply(CalculationContext context) {
            applyToTwoParameters(context, (l, r) -> {
                if(l == null || r == null) {
                    return null;
                }
                Variable.VariableType.LoweredType loweredType = Variable.VariableType.getLoweredType(l.getType(), r.getType());
                if(loweredType == null) {
                    throw INCOMPATIBLE_TYPES_EXCEPTION.create();
                }
                Variable.VariableType type = loweredType.type;
                while(type != null && !typePredicate.test(type)) {
                    type = type.getNextLoweredType();
                }
                if(type == null) {
                    throw INCOMPATIBLE_TYPES_EXCEPTION.create();
                }
                return operator.apply(type, VariableManager.castVariable(type, l), VariableManager.castVariable(type, r));
            });
        }

        @FunctionalInterface
        public interface Operator {
            Variable apply(Variable.VariableType type, Variable left, Variable right);
        }
    }

    public static final Instruction SHIFT_LEFT = getBitwiseShiftOperatorInstruction(type -> type instanceof BitwiseLeftShiftableOperatorVariableType, (type, left, right) -> ((BitwiseLeftShiftableOperatorVariableType)type).shiftVariablesLeft(left, right));
    public static final Instruction SHIFT_RIGHT = getBitwiseShiftOperatorInstruction(type -> type instanceof BitwiseRightShiftableOperatorVariableType, (type, left, right) -> ((BitwiseRightShiftableOperatorVariableType)type).shiftVariablesRight(left, right));

    public static Instruction getBitwiseShiftOperatorInstruction(Predicate<Variable.VariableType> typePredicate, BitwiseShiftOperatorInstruction.Operator operator) {
        return new BitwiseShiftOperatorInstruction(typePredicate, operator);
    }

    public static class BitwiseShiftOperatorInstruction extends Instruction {

        private final Predicate<Variable.VariableType> typePredicate;
        private final Operator operator;

        public BitwiseShiftOperatorInstruction(Predicate<Variable.VariableType> typePredicate, Operator operator) {
            super(2, 1, false);
            this.typePredicate = typePredicate;
            this.operator = operator;
        }

        @Override
        public void apply(CalculationContext context) {
            applyToTwoParameters(context, (l, r) -> {
                if(l == null || r == null) {
                    return null;
                }
                Variable.VariableType type = l.getType();
                while(type != null && !typePredicate.test(type)) {
                    type = type.getNextLoweredType();
                }
                if(type == null) {
                    throw INCOMPATIBLE_TYPES_EXCEPTION.create();
                }
                return operator.apply(type, VariableManager.castVariable(type, l), r.intValue());
            });
        }

        @FunctionalInterface
        public interface Operator {
            Variable apply(Variable.VariableType type, Variable left, int right);
        }
    }

    public static final Instruction KEY = getAccessStaticChildInstruction(entry -> entry.key, PathChildrenAccessor.VariableCaster.MAP_ENTRY_VARIABLE_CASTER);
    public static final Instruction VALUE = getAccessStaticChildInstruction(entry -> entry.value, PathChildrenAccessor.VariableCaster.MAP_ENTRY_VARIABLE_CASTER);
    public static final Instruction POS_X = getAccessStaticChildInstruction(PosVariable::getX, PathChildrenAccessor.VariableCaster.POSITION_VARIABLE_CASTER);
    public static final Instruction POS_Y = getAccessStaticChildInstruction(PosVariable::getY, PathChildrenAccessor.VariableCaster.POSITION_VARIABLE_CASTER);
    public static final Instruction POS_Z = getAccessStaticChildInstruction(PosVariable::getZ, PathChildrenAccessor.VariableCaster.POSITION_VARIABLE_CASTER);

    public static <ParentType extends Variable> Instruction getAccessStaticChildInstruction(Function<ParentType, Variable> childGetter, PathChildrenAccessor.VariableCaster<ParentType> caster) {
        return new AccessStaticChildInstruction<>(childGetter, caster);
    }

    public static class AccessStaticChildInstruction<ParentType extends Variable> extends Instruction {

        private final Function<ParentType, Variable> childGetter;
        private final PathChildrenAccessor.VariableCaster<ParentType> caster;

        public AccessStaticChildInstruction(Function<ParentType, Variable> childGetter, PathChildrenAccessor.VariableCaster<ParentType> caster) {
            super(1, 1, false);
            this.childGetter = childGetter;
            this.caster = caster;
        }

        @Override
        public void apply(CalculationContext context) throws CommandSyntaxException {
            Deque<Either<VariableHolder, Stream<Variable>>> stack = context.stack();
            Either<VariableHolder, Stream<Variable>> value = stack.pop();
            if(value.left().isPresent()) {
                stack.push(Either.left(new VariableHolder(calcOp(value.left().get().variable))));
                return;
            }
            if(value.right().isEmpty()) {
                throw new IllegalStateException("Invalid Either on stack when entry child getter was called. Neither left nor right were present");
            }
            stack.push(Either.right(value.right().get().map(var -> {
                try {
                    return calcOp(var);
                } catch (CommandSyntaxException e) {
                    context.errorConsumer().accept(Texts.toText(e.getRawMessage()));
                    return null;
                }
            }).filter(Objects::nonNull)));
        }

        private Variable calcOp(Variable var) throws CommandSyntaxException {
            return childGetter.apply(caster.cast(var));
        }
    }

    public static final Instruction CROSS = getPosBinaryInstruction(PosVariable.PosVariableType::calcCross);
    public static final Instruction DOT = getPosBinaryInstruction(PosVariable.PosVariableType::calcDot);

    public static Instruction getPosBinaryInstruction(TwoPosInstruction.Operator op) {
        return new TwoPosInstruction(op);
    }

    public static class TwoPosInstruction extends Instruction {

        private final ImmediateValue.CommandBiFunction applyFunction;

        public TwoPosInstruction(Operator op) {
            super(2, 1, false);
            this.applyFunction = (l, r) -> {
                if(!(l instanceof PosVariable leftPos && r instanceof PosVariable rightPos)) {
                    return null;
                }
                return op.apply(leftPos, rightPos);
            };
        }

        @Override
        public void apply(CalculationContext context) {
            applyToTwoParameters(context, applyFunction);
        }

        @FunctionalInterface
        public interface Operator {
            Variable apply(PosVariable left, PosVariable right);
        }
    }

    public static final Instruction NORMALIZE = new Instruction(1, 1, false) {
        @Override
        public void apply(CalculationContext context) throws CommandSyntaxException {
            Deque<Either<VariableHolder, Stream<Variable>>> stack = context.stack();
            Either<VariableHolder, Stream<Variable>> value = stack.pop();
            if(value.left().isPresent()) {
                stack.push(Either.left(new VariableHolder(calcOp(value.left().get().variable))));
                return;
            }
            if(value.right().isEmpty()) {
                throw new IllegalStateException("Invalid Either on stack when 'normalize' was called. Neither left nor right were present");
            }
            stack.push(Either.right(value.right().get().map(var -> {
                try {
                    return calcOp(var);
                } catch (CommandSyntaxException e) {
                    context.errorConsumer().accept(Texts.toText(e.getRawMessage()));
                    return null;
                }
            }).filter(Objects::nonNull)));
        }

        private Variable calcOp(Variable var) throws CommandSyntaxException {
            if(var instanceof PosVariable pos) {
                return PosVariable.PosVariableType.calcNormalize(pos);
            }
            throw INCOMPATIBLE_TYPES_EXCEPTION.create();
        }
    };

    public static final Instruction RANGE = new Instruction(2, 1, false) {

        @Override
        public void apply(CalculationContext context) {
            Either<VariableHolder, Stream<Variable>> right = context.stack().pop(), left = context.stack().pop();
            context.stack().push(Either.right(left.map(
                    holderLeft -> right.map(
                            holderRight -> getRangeStream(holderLeft.variable, holderRight.variable),
                            streamRight -> streamRight.flatMap(varRight -> getRangeStream(holderLeft.variable, varRight))
                    ),
                    streamLeft -> right.map(
                            holderRight -> streamLeft.flatMap(varLeft -> getRangeStream(varLeft, holderRight.variable)),
                            streamRight -> StreamSupport.stream(Spliterators.spliteratorUnknownSize(new AbstractIterator<Stream<Variable>>() {

                                private final Iterator<Variable> itLeft = streamLeft.iterator();
                                private final Iterator<Variable> itRight = streamRight.iterator();

                                @Nullable
                                @Override
                                protected Stream<Variable> computeNext() {
                                    if (itLeft.hasNext() && itRight.hasNext()) {
                                        return getRangeStream(itLeft.next(), itRight.next());
                                    }
                                    return endOfData();
                                }
                            }, 0), false).flatMap(stream -> stream)
                    ))));
        }

        private Stream<Variable> getRangeStream(Variable startVar, Variable endVar) {
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new AbstractIterator<>() {

                private int current = startVar.intValue();
                private final int end = endVar.intValue();

                @Nullable
                @Override
                protected Variable computeNext() {
                    if(current > end) {
                        return endOfData();
                    }
                    return new IntVariable(current++);
                }
            }, 0), false);
        }
    };

    public static final Instruction RANDOM = new Instruction(0, 1, false) {
        @Override
        public void apply(CalculationContext context) {
            context.stack().push(Either.left(new VariableHolder(new DoubleVariable(Math.random()))));
        }
    };

    public static final Instruction BUILD_MAP_ENTRY = new Instruction(2, 1, false) {
        @Override
        public void apply(CalculationContext context) {
            applyToTwoParameters(context, (key, value) -> {
                MapEntryVariable result = new MapEntryVariable(new MapEntryVariable.MapEntryVariableType(key == null ? null : key.getType(), value == null ? null : value.getType()));
                result.key = key;
                result.value = value;
                return result;
            });
        }
    };

    /**
     * Wrapper for {@link ImmediateValue.CommandBiFunction#applyToTwoParameters} taking both parameters from the stack and using the error consumer of the calculation context.
     * The command context is not used.
     * @param context The calculation context to get the stack and error consumer
     * @param function The function to apply to the parameters
     */
    private static void applyToTwoParameters(Instruction.CalculationContext context, ImmediateValue.CommandBiFunction function) {
        Deque<Either<VariableHolder, Stream<Variable>>> stack = context.stack();
        Either<VariableHolder, Stream<Variable>> right = stack.pop(), left = stack.pop();
        stack.push(function.applyToTwoParameters(left, right, context.errorConsumer()));
    }

    public static final Instruction NEGATE = new Instruction(1, 1, false) {
        @Override
        public void apply(CalculationContext context) {
            Deque<Either<VariableHolder, Stream<Variable>>> stack = context.stack();
            stack.push(stack.pop().mapBoth(
                    holder -> new VariableHolder(calcNegate(holder.variable, context.errorConsumer())),
                    varStream -> varStream.map(var -> calcNegate(var, context.errorConsumer())).filter(Objects::nonNull))
                    .flatMap(holder -> holder.variable == null ? Either.right(Stream.empty()) : Either.left(holder)));
        }

        private Variable calcNegate(Variable var, Consumer<Text> errorConsumer) {
            Variable.VariableType type = var.getType();
            while(type != null && !(type instanceof NegatableOperatorVariableType)) {
                type = type.getNextLoweredType();
            }
            if(type == null) {
                errorConsumer.accept(Texts.toText(INCOMPATIBLE_TYPES_EXCEPTION.create().getRawMessage()));
                return null;
            }
            try {
                return ((NegatableOperatorVariableType)type).negateVariable(VariableManager.castVariable(type, var));
            } catch (CommandSyntaxException e) {
                errorConsumer.accept(Texts.toText(e.getRawMessage()));
                return null;
            }
        }
    };

    public static final Instruction GET_ALL_CONTENTS = new Instruction(1, 1, false) {

        @Override
        public void apply(CalculationContext context) throws CommandSyntaxException {
            Deque<Either<VariableHolder, Stream<Variable>>> stack = context.stack();
            Either<VariableHolder, Stream<Variable>> value = stack.pop();
            if(value.left().isPresent()) {
                if(!(value.left().get().variable instanceof IndexableVariable indexable)) {
                    if(value.left().get().variable != null) {
                        throw VALUE_NOT_INDEXABLE_EXCEPTION.create();
                    }
                    stack.push(Either.right(Stream.of()));
                    return;
                }
                stack.push(Either.right(indexable.getContents()));
                return;
            }
            if(value.right().isEmpty()) {
                throw new IllegalStateException("Invalid Either passed into all content getter. Neither left nor right were present");
            }
            stack.push(Either.right(value.right().get().flatMap(var -> {
                        if (!(var instanceof IndexableVariable indexable)) {
                            if(var != null) {
                                context.errorConsumer().accept(Texts.toText(VALUE_NOT_INDEXABLE_EXCEPTION.create().getRawMessage()));
                            }
                            return null;
                        }
                        return indexable.getContents();
                    })));
        }
    };

    public static final Instruction GET_INDEXED_CONTENTS = new Instruction(2, 1, false) {

        @Override
        public void apply(CalculationContext context) {
            applyToTwoParameters(context, (value, index) -> {
                if(!(value instanceof IndexableVariable indexable)) {
                    throw VALUE_NOT_INDEXABLE_EXCEPTION.create();
                }
                return indexable.get(indexable.ensureIndexCompatible(index));
            });
        }
    };

    public static final Instruction GET_INDEXED_CONTENTS_COMPARE_MAPS = new Instruction(2, 1, false) {

        @Override
        public void apply(CalculationContext context) throws CommandSyntaxException {
            Deque<Either<VariableHolder, Stream<Variable>>> stack = context.stack();
            Either<VariableHolder, Stream<Variable>> right = stack.pop(), left = stack.pop();
            if(left.left().isPresent()) {
                if(left.left().get().variable == null) {
                    stack.push(Either.right(Stream.of()));
                    return;
                }
                if(!(left.left().get().variable instanceof IndexableVariable indexable)) {
                    throw VALUE_NOT_INDEXABLE_EXCEPTION.create();
                }
                if(right.left().isPresent()) {
                    stack.push(right.left().get().variable == null ? Either.right(Stream.of()) : PathChildrenAccessor.IndexedPathChildrenAccessor.get(indexable, right.left().get().variable, true));
                    return;
                }
                if(right.right().isEmpty()) {
                    throw new IllegalStateException("Index element was an invalid Either when a map comparing index was called. Neither left nor right were present");
                }
                stack.push(Either.right(right.right().get().flatMap(index -> {
                    if(index == null) {
                        return Stream.of();
                    }
                    try {
                        return PathChildrenAccessor.IndexedPathChildrenAccessor.get(indexable, index, true)
                                .map(
                                        holder -> Stream.of(holder.variable),
                                        stream -> stream
                                );
                    } catch (CommandSyntaxException e) {
                        context.errorConsumer().accept(Texts.toText(e.getRawMessage()));
                        return Stream.of();
                    }
                })));
                return;
            }
            if(left.right().isEmpty()) {
                throw new IllegalStateException("Indexable element was an invalid Either when a map comparing index was called. Neither left nor right were present");
            }
            if(right.left().isPresent()) {
                Variable index = right.left().get().variable;
                if(index == null) {
                    stack.push(Either.right(Stream.of()));
                }
                stack.push(Either.right(left.right().get().flatMap(var -> {
                    if(!(var instanceof IndexableVariable indexable)) {
                        if(var != null) {
                            context.errorConsumer().accept(Texts.toText(VALUE_NOT_INDEXABLE_EXCEPTION.create().getRawMessage()));
                        }
                        return Stream.of();
                    }
                    try {
                        return PathChildrenAccessor.IndexedPathChildrenAccessor.get(indexable, index, true)
                                .map(
                                        holder -> Stream.of(holder.variable),
                                        stream -> stream
                                );
                    } catch (CommandSyntaxException e) {
                        context.errorConsumer().accept(Texts.toText(e.getRawMessage()));
                        return Stream.of();
                    }
                })));
                return;
            }
            if(right.right().isEmpty()) {
                throw new IllegalStateException("Index element was an invalid Either when a map comparing index was called. Neither left nor right were present");
            }
            Iterator<Variable> indices = right.right().get().iterator(), vars = left.right().get().iterator();
            stack.push(Either.right(StreamSupport.stream(Spliterators.spliteratorUnknownSize(new AbstractIterator<Either<VariableHolder, Stream<Variable>>>() {

                @Override
                protected Either<VariableHolder, Stream<Variable>> computeNext() {
                    while (true) {
                        if (!(indices.hasNext() && vars.hasNext())) {
                            return endOfData();
                        }
                        Variable nextVar = vars.next();
                        Variable nextIndex = indices.next();
                        if(!(nextVar instanceof IndexableVariable indexable)) {
                            if(nextVar != null) {
                                context.errorConsumer().accept(Texts.toText(VALUE_NOT_INDEXABLE_EXCEPTION.create().getRawMessage()));
                            }
                            continue;
                        }
                        if(nextIndex == null) {
                            continue;
                        }
                        try {
                            return PathChildrenAccessor.IndexedPathChildrenAccessor.get(indexable, nextIndex, true);
                        } catch (CommandSyntaxException e) {
                            context.errorConsumer().accept(Texts.toText(e.getRawMessage()));
                        }
                    }
                }
            }, 0), false)
                    .flatMap(
                            value -> value.map(
                                    holder -> Stream.of(holder.variable),
                                    stream -> stream
                            )
                    )));
        }
    };

    public static final Instruction GET_RANGE_INDEXED_CONTENTS = new Instruction(2, 1, false) {

        @Override
        public void apply(CalculationContext context) throws CommandSyntaxException {
            Deque<Either<VariableHolder, Stream<Variable>>> stack = context.stack();
            Either<VariableHolder, Stream<Variable>> right = stack.pop(), left = stack.pop();
            if(left.left().isPresent()) {
                if(!(left.left().get().variable instanceof ListVariable list)) {
                    throw VALUE_NOT_LIST_EXCEPTION.create();
                }
                if(right.left().isPresent()) {
                    stack.push(Either.right(getValues(list, right.left().get().variable.intValue())));
                    return;
                }
                if(right.right().isEmpty()) {
                    throw new IllegalStateException("Index element was an invalid Either when a range index was called. Neither left nor right were present");
                }
                stack.push(Either.right(right.right().get().filter(Objects::nonNull).flatMap(index -> getValues(list, index.intValue()))));
                return;
            }
            if(left.right().isEmpty()) {
                throw new IllegalStateException("Indexable element was an invalid Either when a range index was called. Neither left nor right were present");
            }
            if(right.left().isPresent()) {
                Variable indexVar = right.left().get().variable;
                if(indexVar == null) {
                    stack.push(Either.right(Stream.of()));
                    return;
                }
                int index = indexVar.intValue();
                stack.push(Either.right(left.right().get().flatMap(var -> {
                    if(!(var instanceof ListVariable indexable)) {
                        if(var != null) {
                            context.errorConsumer().accept(Texts.toText(VALUE_NOT_LIST_EXCEPTION.create().getRawMessage()));
                        }
                        return Stream.of();
                    }
                    return getValues(indexable, index);
                })));
                return;
            }
            if(right.right().isEmpty()) {
                throw new IllegalStateException("Index element was an invalid Either when a range index was called. Neither left nor right were present");
            }
            Iterator<Variable> indices = right.right().get().iterator(), vars = left.right().get().iterator();
            stack.push(Either.right(StreamSupport.stream(Spliterators.spliteratorUnknownSize(new AbstractIterator<Stream<Variable>>() {

                        @Override
                        protected Stream<Variable> computeNext() {
                            while (true) {
                                if (!(indices.hasNext() && vars.hasNext())) {
                                    return endOfData();
                                }
                                Variable nextVar = vars.next();
                                Variable nextIndex = indices.next();
                                if(!(nextVar instanceof ListVariable indexable)) {
                                    if(nextVar != null) {
                                        context.errorConsumer().accept(Texts.toText(VALUE_NOT_LIST_EXCEPTION.create().getRawMessage()));
                                    }
                                    continue;
                                }
                                if(nextIndex == null) {
                                    continue;
                                }
                                return getValues(indexable, nextIndex.intValue());
                            }
                        }
                    }, 0), false).flatMap(
                            stream -> stream
            )));
        }

        private Stream<Variable> getValues(ListVariable list, int rangeStart) {
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new AbstractIterator<>() {

                private final IntVariable index = new IntVariable(rangeStart);

                @Nullable
                @Override
                protected Variable computeNext() {
                    if(list.intValue() <= index.intValue()) {
                        return endOfData();
                    }
                    Variable result = list.get(index);
                    index.add(1);
                    return result;
                }
            }, 0), false);
        }
    };

    private static final SimpleCommandExceptionType VALUE_NOT_INDEXABLE_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Value is not indexable"));
    private static final SimpleCommandExceptionType VALUE_NOT_LIST_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Value is not a list"));

    public static Instruction IT_COPY = new Instruction(1, 1, false) {
        @Override
        public void apply(CalculationContext context) throws CommandSyntaxException {
            Either<VariableHolder, Stream<Variable>> value = context.stack().pop();
            if(value.left().isPresent()) {
                Variable var = value.left().get().variable;
                if(!(var instanceof IteratorVariable it)) {
                    throw VALUE_NOT_ITERATOR_EXCEPTION.create();
                }
                context.stack().push(Either.left(new VariableHolder(it.copy())));
                return;
            }
            if(value.right().isEmpty()) {
                throw new IllegalStateException("Invalid Either on stack when 'it_copy' was called. Neither left nor right were present");
            }
            context.stack().push(Either.right(value.right().get().flatMap(var -> {
                if(!(var instanceof IteratorVariable it)) {
                    context.errorConsumer().accept(Texts.toText(VALUE_NOT_ITERATOR_EXCEPTION.create().getRawMessage()));
                    return Stream.empty();
                }
                return Stream.of(it.copy());
            })));
        }
    };

    public static Instruction IT_ALL = new Instruction(1, 1, true) {
        @Override
        public void apply(CalculationContext context) throws CommandSyntaxException {
            Either<VariableHolder, Stream<Variable>> value = context.stack().pop();
            if(value.left().isPresent()) {
                Variable var = value.left().get().variable;
                if(!(var instanceof IteratorVariable it)) {
                    throw VALUE_NOT_ITERATOR_EXCEPTION.create();
                }
                context.stack().push(Either.right(it.all(context.errorConsumer())));
                return;
            }
            if(value.right().isEmpty()) {
                throw new IllegalStateException("Invalid Either on stack when 'it_all' was called. Neither left nor right were present");
            }
            context.stack().push(Either.right(value.right().get().flatMap(var -> {
                if(!(var instanceof IteratorVariable it)) {
                    context.errorConsumer().accept(Texts.toText(VALUE_NOT_ITERATOR_EXCEPTION.create().getRawMessage()));
                    return Stream.empty();
                }
                return it.all(context.errorConsumer());
            })));
        }
    };

    private static final SimpleCommandExceptionType ITERATOR_EMPTY_EXCEPTION = new SimpleCommandExceptionType(Text.of("The iterator contains no next element"));

    public static final Instruction IT_NEXT = new Instruction(1, 1, true) {
        @Override
        public void apply(CalculationContext context) throws CommandSyntaxException {
            Either<VariableHolder, Stream<Variable>> value = context.stack().pop();
            if(value.left().isPresent()) {
                if(!(value.left().get().variable instanceof IteratorVariable iterator)) {
                    throw VALUE_NOT_ITERATOR_EXCEPTION.create();
                }
                if(iterator.empty()) {
                    throw ITERATOR_EMPTY_EXCEPTION.create();
                }
                context.stack().push(Either.left(
                        new VariableHolder(iterator.next())
                ));
                return;
            }
            if(value.right().isPresent()) {
                throw new IllegalStateException("Invalid Either on stack when 'it_next' was called. Neither left nor right were present");
            }
            context.stack().push(Either.right(value.right().get().flatMap(var -> {
                try {
                    if(!(var instanceof IteratorVariable iterator)) {
                        throw VALUE_NOT_ITERATOR_EXCEPTION.create();
                    }
                    return Stream.of(iterator.next());
                } catch(CommandSyntaxException e) {
                    context.errorConsumer().accept(Texts.toText(e.getRawMessage()));
                    return Stream.of();
                }
            })));
        }
    };

    public static final Instruction IT_MULTIPLE_NEXT = new Instruction(1, 2, true) {
        @Override
        public void apply(CalculationContext context) throws CommandSyntaxException {
            Either<VariableHolder, Stream<Variable>>
                    countValue = context.stack().pop(),
                    iteratorValue = context.stack().pop();
            if(iteratorValue.left().isPresent()) {
                if(iteratorValue.left().get().variable == null) {
                    context.stack().push(Either.right(Stream.of()));
                }
                if(!(iteratorValue.left().get().variable instanceof IteratorVariable iterator)) {
                    throw VALUE_NOT_ITERATOR_EXCEPTION.create();
                }
                if(countValue.left().isPresent()) {
                    Variable count = countValue.left().get().variable;
                    context.stack().push(Either.right(count == null ? Stream.of() : iterator.next(count.intValue(), context.errorConsumer())));
                    return;
                }
                if(countValue.right().isEmpty()) {
                    throw new IllegalArgumentException("First (count) element of stack was an invalid Either when 'it_next' was called. Neither left nor right were present");
                }
                context.stack().push(Either.right(countValue.right().get().flatMap(
                        count -> count == null
                                ? Stream.of()
                                : iterator.next(count.intValue(), context.errorConsumer()))));
                return;
            }
            if(iteratorValue.right().isEmpty()) {
                throw new IllegalArgumentException("Second (iterator) element of stack was an invalid Either when 'it_next' was called. Neither left nor right were present");
            }
            Stream<Variable> iteratorStream = iteratorValue.right().get();
            context.stack().push(Either.right(countValue.map(
                    countHolder -> {
                        if (countHolder.variable == null) {
                            return Stream.of();
                        }
                        int count = countHolder.variable.intValue();
                        return iteratorStream.flatMap(var -> {
                            if (!(var instanceof IteratorVariable iterator)) {
                                if (var != null) {
                                    context.errorConsumer().accept(Texts.toText(VALUE_NOT_ITERATOR_EXCEPTION.create().getRawMessage()));
                                }
                                return Stream.of();
                            }
                            return iterator.next(count, context.errorConsumer());
                        });
                    },
                    countStream -> {
                        Iterator<Variable> countIterator = countStream.iterator(), iteratorIterator = iteratorStream.iterator();
                        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new AbstractIterator<Stream<Variable>>() {
                            @Nullable
                            @Override
                            protected Stream<Variable> computeNext() {
                                while (true) {
                                    if (!(countIterator.hasNext() && iteratorIterator.hasNext())) {
                                        return endOfData();
                                    }
                                    try {
                                        Variable iteratorVar = iteratorIterator.next();
                                        Variable countVar = countIterator.next();
                                        if (iteratorVar == null || countVar == null) {
                                            continue;
                                        }
                                        if (!(iteratorVar instanceof IteratorVariable iterator)) {
                                            throw VALUE_NOT_ITERATOR_EXCEPTION.create();
                                        }
                                        return iterator.next(countVar.intValue(), context.errorConsumer());

                                    } catch (CommandSyntaxException e) {
                                        context.errorConsumer().accept(Texts.toText(e.getRawMessage()));
                                    }
                                }
                            }
                        }, 0), false).flatMap(stream -> stream);
                    }
            )));
        }
    };

    private static final SimpleCommandExceptionType VALUE_NOT_ITERATOR_EXCEPTION = new SimpleCommandExceptionType(new LiteralMessage("Value is not an iterator"));

    public static Instruction getCast(Variable.VariableType type) {
        return new Cast(type);
    }

    public static class Cast extends Instruction {

        private final Variable.VariableType type;

        private Cast(Variable.VariableType type) {
            super(1, 1, false);
            this.type = type;
        }

        @Override
        public void apply(CalculationContext context) {
            context.stack().push(context.stack().pop().mapBoth(
                    var -> new VariableHolder(castSafe(var.variable, type, context.errorConsumer())),
                    varStream -> varStream.map(var -> castSafe(var, type, context.errorConsumer()))));
        }

        private static Variable castSafe(Variable var, Variable.VariableType type, Consumer<Text> errorConsumer) {
            try {
                return VariableManager.castVariable(type, var);
            } catch (CommandSyntaxException e) {
                errorConsumer.accept(Texts.toText(e.getRawMessage()));
                return null;
            }
        }
    }

    public static Instruction getBuildListOrMap(int length) {
        return new BuildListOrMap(length);
    }

    /**
     * Takes a value from the stack for every entry of a list with the given list and lowers their types.
     * If the lowered type is a map entry type, a map with contents corresponding to the entries is build instead. Casting it to a list
     * will give a list of the entries
     */
    public static class BuildListOrMap extends Instruction {

        private final int length;

        public BuildListOrMap(int length) {
            super(length, 1, false);
            this.length = length;
        }

        @Override
        public void apply(CalculationContext context) throws CommandSyntaxException {
            Deque<Either<VariableHolder, Stream<Variable>>> stack = context.stack();
            List<Either<VariableHolder, Iterator<Variable>>> content = new ArrayList<>(length);
            boolean hasIterator = false;
            for(int i = 0; i < length; ++i) {
                Either<VariableHolder, Stream<Variable>> var = stack.pop();
                if(var.right().isPresent()) {
                    hasIterator = true;
                }
                content.add(var.mapRight(Stream::iterator));
            }
            Variable.VariableType[] contentTypes = new Variable.VariableType[length];
            if(!hasIterator) {
                for(int i = 0; i < length; ++i) {
                    //noinspection OptionalGetWithoutIsPresent
                    Variable var = content.get(i).left().get().variable;
                    contentTypes[i] = var == null ? null : var.getType();
                }
                Variable.VariableType.LoweredType loweredContentType = Variable.VariableType.getLoweredType(contentTypes);
                if(loweredContentType == null) {
                    throw INCOMPATIBLE_TYPES_EXCEPTION.create();
                }
                VariableManager.Caster childCaster = loweredContentType.type != null ? loweredContentType.type.getTemplate().caster : null;
                if(childCaster != null) {
                    if (loweredContentType.type instanceof MapEntryVariable.MapEntryVariableType mapType) {
                        MapVariable var = new MapVariable(new MapVariable.MapVariableType(mapType.key, mapType.value));
                        for (int i = 0; i < length; ++i) {
                            //noinspection OptionalGetWithoutIsPresent
                            MapEntryVariable entry = (MapEntryVariable) childCaster.cast(loweredContentType.type, content.get(i).left().get().variable);
                            var.set(entry.key, entry.value);
                        }
                        stack.push(Either.left(new VariableHolder(var)));
                        return;
                    }
                }
                ListVariable var = new ListVariable(new ListVariable.ListVariableType(loweredContentType.type));
                IntVariable index = new IntVariable();
                index.setValue(length - 1);
                var.ensureIndexExists(index);
                for(int i = 0; i < length; ++i) {
                    index.setValue(i);
                    //noinspection OptionalGetWithoutIsPresent
                    Variable value = content.get(i).left().get().variable;
                    var.set(index, childCaster != null ? childCaster.cast(loweredContentType.type, value) : null);
                }
                stack.push(Either.left(new VariableHolder(var)));
                return;
            }
            stack.push(Either.right(StreamSupport.stream(Spliterators.spliteratorUnknownSize(new AbstractIterator<>() {

                private final Variable[] currentValues = new Variable[length];

                @Nullable
                @Override
                protected Variable computeNext() {
                    for (int i = 0; i < length; i++) {
                        Either<VariableHolder, Iterator<Variable>> value = content.get(i);
                        if(value.left().isPresent()) {
                            currentValues[i] = value.left().get().variable;
                            continue;
                        }
                        //noinspection OptionalGetWithoutIsPresent
                        if(value.right().get().hasNext()) {
                            //noinspection OptionalGetWithoutIsPresent
                            currentValues[i] = value.right().get().next();
                            continue;
                        }
                        return endOfData();
                    }
                    for(int i = 0; i < length; ++i) {
                        contentTypes[i] = currentValues[i].getType();
                    }
                    Variable.VariableType.LoweredType loweredContentType = Variable.VariableType.getLoweredType(contentTypes);
                    if(loweredContentType == null) {
                        context.errorConsumer().accept(Texts.toText(INCOMPATIBLE_TYPES_EXCEPTION.create().getRawMessage()));
                        return null;
                    }
                    VariableManager.Caster childCaster = loweredContentType.type.getTemplate().caster;
                    if(loweredContentType.type instanceof MapEntryVariable.MapEntryVariableType mapType) {
                        MapVariable result = new MapVariable(new MapVariable.MapVariableType(mapType.key, mapType.value));
                        for(int i = 0; i < length; ++i) {
                            try {
                                //noinspection OptionalGetWithoutIsPresent
                                MapEntryVariable entry = (MapEntryVariable) childCaster.cast(loweredContentType.type, content.get(i).left().get().variable);
                                result.set(entry.key, entry.value);
                            } catch (CommandSyntaxException e) {
                                context.errorConsumer().accept(Texts.toText(e.getRawMessage()));
                            }
                        }
                        return result;
                    }
                    ListVariable result = new ListVariable(new ListVariable.ListVariableType(loweredContentType.type));
                    IntVariable index = new IntVariable();
                    index.setValue(length - 1);
                    result.ensureIndexExists(index);
                    for(int i = 0; i < length; ++i) {
                        index.setValue(i);
                        try {
                            result.set(index, childCaster.cast(loweredContentType.type, currentValues[i]));
                        } catch (CommandSyntaxException e) {
                            context.errorConsumer().accept(Texts.toText(e.getRawMessage()));
                            result.set(index, null);
                        }
                    }
                    return result;
                }
            }, 0), false)));
        }
    }

    public static Instruction getLoadConstant(Variable constant) {
        return new LoadConstant(constant);
    }

    /**
     * Puts a constant onto the stack
     */
    public static class LoadConstant extends Instruction {

        public final Either<VariableHolder, List<Variable>> value;

        public LoadConstant(Variable constant) {
            super(0, 1, false);
            value = Either.left(new VariableHolder(constant));
        }

        public LoadConstant(List<Variable> value) {
            super(0, 1, false);
            this.value = Either.right(value);
        }

        @Override
        public void apply(CalculationContext context) {
            context.stack().push(value.mapBoth(
                    holder -> holder,
                    List::stream
            ));
        }
    }

    public static Instruction getLoadVariable(VariableIdentifier id) {
        return new LoadVariable(id);
    }

    /**
     * Loads a value from the server using the command context and puts its value onto the stack
     */
    public static class LoadVariable extends Instruction {

        private final VariableIdentifier id;

        public LoadVariable(VariableIdentifier id) {
            super(0, 1, true);
            this.id = id;
        }

        @Override
        public void apply(CalculationContext context) throws CommandSyntaxException {
            context.stack().push(Either.left(new VariableHolder(((VariableManagerContainer)context.commandContext().getSource().getServer()).command_expander$getVariableManager().getReadonly(id).var)));
        }
    }

    public static Instruction getLoadFromEntitySelector(EntitySelector selector) {
        return new LoadFromEntitySelector(selector);
    }

    /**
     * Gets all entities selected by the selector from the server using the command context and puts them onto the stack
     */
    public static class LoadFromEntitySelector extends Instruction {

        private final EntitySelector entitySelector;

        public LoadFromEntitySelector(EntitySelector entitySelector) {
            super(0, 1, true);
            this.entitySelector = entitySelector;
        }

        @Override
        public void apply(CalculationContext context) throws CommandSyntaxException {
            if(entitySelector.getLimit() == 1) {
                context.stack().push(Either.left(
                        new VariableHolder(
                                new EntityVariable(
                                        entitySelector.getEntity(context.commandContext().getSource())
                                                .getUuid()))
                ));
                return;
            }
            context.stack().push(Either.right(
                    entitySelector.getEntities(context.commandContext().getSource())
                            .stream().map(entity ->
                                    new EntityVariable(entity.getUuid()))));
        }
    }

    public static Instruction getLoadStreamAll(int count) {
        return new LoadStreamAll(count);
    }

    /**
     * Takes a with "count" specified amount of entries from the stack and adds a stream of them to the stack.<br/>
     * If the entries are streams themselves, the values of that stream are used
     */
    public static class LoadStreamAll extends Instruction {

        private final int count;

        public LoadStreamAll(int count) {
            super(count, 1, false);
            this.count = count;
        }

        @Override
        public void apply(CalculationContext context) {
            List<Either<VariableHolder, Stream<Variable>>> data = new ArrayList<>(count);
            for(int i = 0; i < count; ++i) {
                data.add(0, context.stack().pop());
            }
            context.stack().push(Either.right(data.stream().flatMap(entry -> entry.map(
                    holder -> Stream.of(holder.variable),
                    stream -> stream))));
        }
    }
}
