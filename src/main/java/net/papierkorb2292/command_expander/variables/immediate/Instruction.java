package net.papierkorb2292.command_expander.variables.immediate;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.papierkorb2292.command_expander.variables.Variable;
import net.papierkorb2292.command_expander.variables.VariableHolder;

import java.util.Deque;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * An instruction of an immediate value performing an action on a stack. Most immediate values use a list of instructions
 * to evaluate the result, this may change through optimization.
 */
public abstract class Instruction {

    public final int consumedStackEntries, suppliedStackEntries;
    public final boolean notPreDeterminable;

    /**
     * @param consumedStackEntries The amount of entries taken from the stack. Important for finding instructions this instruction
     *                             depends on for optimization.
     * @param suppliedStackEntries The amount of entries put onto the stack. Important for finding instructions depending
     *                             on this instruction for optimization.
     * @param notPreDeterminable When this is set to 'true', the instruction shouldn't be evaluated as part of the
     *                           optimization process. For example, an entity selector can't be optimized, because the result
     *                           depends on when it's evaluated.
     */
    public Instruction(int consumedStackEntries, int suppliedStackEntries, boolean notPreDeterminable) {
        this.consumedStackEntries = consumedStackEntries;
        this.suppliedStackEntries = suppliedStackEntries;
        this.notPreDeterminable = notPreDeterminable;
    }

    /**
     * Performs the action on the stack with the {@link CommandContext} and an error consumer for exceptions that can't be thrown
     * (for example when used in a functional interface that doesn't have the exception in its method signature)
     * @param context The context of the calculation containing the stack, command context and error consumer
     * @throws CommandSyntaxException An exception occurred when performing the instruction
     */
    public abstract void apply(CalculationContext context) throws CommandSyntaxException;

    /**
     * The context of the evaluation of an immediate value containing the stack, command context and error consumer for exceptions that can't be thrown
     * (for example when used in a functional interface that doesn't have the exception in its method signature)
     * @see #apply
     */
    public static record CalculationContext(Deque<Either<VariableHolder, Stream<Variable>>> stack, CommandContext<ServerCommandSource> commandContext, Consumer<Text> errorConsumer) {

        public CalculationContext(Deque<Either<VariableHolder, Stream<Variable>>> stack, CommandContext<ServerCommandSource> commandContext) {
            this(stack, commandContext, ImmediateValue.getCaughtErrorConsumer(commandContext));
        }
    }
}
