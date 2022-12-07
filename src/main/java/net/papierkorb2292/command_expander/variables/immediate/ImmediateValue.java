package net.papierkorb2292.command_expander.variables.immediate;

import com.google.common.collect.AbstractIterator;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.papierkorb2292.command_expander.CommandExpander;
import net.papierkorb2292.command_expander.variables.Variable;
import net.papierkorb2292.command_expander.variables.VariableHolder;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * <p>Interface for immediate values defining the calculate method.</p>
 * <p>
 *     An immediate value defines a series of instructions for calculating a value that can also depend on variables.<br/>
 *     For example just "2" could be an immediate value, but also "sin(2 + 0.5 + 6)" or "sin(minecraft:my_var)"
 * </p>
 * An immediate value might be optimized to not
 * need the list of instructions, so different implementations can exist.
 */
public interface ImmediateValue {

    /**
     * Calculates the value of this immediate value
     * @param cc The command context this immediate value is used in
     * @return The resulting value
     * @throws CommandSyntaxException An exception occurred when calculating the value
     */
    Either<VariableHolder, Stream<Variable>> calculate(CommandContext<ServerCommandSource> cc) throws CommandSyntaxException;

    default void throwIfFeatureDisabled(CommandContext<ServerCommandSource> cc) throws CommandSyntaxException {
        if(!CommandExpander.isFeatureEnabled(cc.getSource().getServer(), CommandExpander.VARIABLE_FEATURE)) {
            throw CommandExpander.USED_DISABLED_FEATURE.create(CommandExpander.VARIABLE_FEATURE);
        }
    }

    /**
     * Creates the error consumer for a {@link Instruction.CalculationContext} that sends the error to the command context
     * @param cc The command context to send the error to
     * @return The error consumer
     */
    static Consumer<Text> getCaughtErrorConsumer(CommandContext<ServerCommandSource> cc) {
        ServerCommandSource source = cc.getSource();
        return source::sendError;
    }



    @FunctionalInterface
    interface CommandBiFunction {

        Variable apply(Variable left, Variable right) throws CommandSyntaxException;

        /**
         * <p>Applies the function to two values that either are a single variable or a stream of variables:</p>
         * <p>
         *     If both values are only a single values, the function is applied once to them.<br/>
         *     If one value is a stream and the other a single value, the function is applied for every value in the stream together with
         *     the single value.<br/>
         *     If both values are streams, the iterators of them are used and the function is applied to every pair of values until at least
         *     one iterator has no next value.
         * </p>
         * @param left The left parameter
         * @param right The right parameter
         * @param errorConsumer Consumer for errors thrown by the function
         */
        default Either<VariableHolder, Stream<Variable>> applyToTwoParameters(Either<VariableHolder, Stream<Variable>> left, Either<VariableHolder, Stream<Variable>> right, Consumer<Text> errorConsumer) {
            return right.map(
                    rightHolder -> left.map(
                            leftHolder -> {
                                try {
                                    return Either.left(new VariableHolder(apply(leftHolder.variable, rightHolder.variable)));
                                } catch (CommandSyntaxException e) {
                                    errorConsumer.accept(Texts.toText(e.getRawMessage()));
                                    return Either.right(Stream.empty());
                                }
                            },
                            leftStream -> Either.right(leftStream.flatMap(var -> {
                                try {
                                    return Stream.of(apply(var, rightHolder.variable));
                                } catch (CommandSyntaxException e) {
                                    errorConsumer.accept(Texts.toText(e.getRawMessage()));
                                    return Stream.empty();
                                }
                            }))
                    ),
                    rightStream -> left.map(
                            leftHolder -> Either.right(rightStream.flatMap(var -> {
                                try {
                                    return Stream.of(apply(leftHolder.variable, var));
                                } catch (CommandSyntaxException e) {
                                    errorConsumer.accept(Texts.toText(e.getRawMessage()));
                                    return Stream.empty();
                                }
                            })),
                            leftStream -> {
                                Iterator<Variable> itLeft = leftStream.iterator(), itRight = rightStream.iterator();

                                return Either.right(StreamSupport.stream(Spliterators.spliteratorUnknownSize(new AbstractIterator<>() {
                                    @Nullable
                                    @Override
                                    protected Variable computeNext() {
                                        while (true) {
                                            if (!(itLeft.hasNext() && itRight.hasNext())) {
                                                return endOfData();
                                            }
                                            try {
                                                return apply(itLeft.next(), itRight.next());
                                            } catch (CommandSyntaxException e) {
                                                errorConsumer.accept(Texts.toText(e.getRawMessage()));
                                            }
                                        }
                                    }
                                }, 0), false));
                            }
                    )
            );
        }
    }
}
