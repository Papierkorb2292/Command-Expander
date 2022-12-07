package net.papierkorb2292.command_expander.mixin;

import com.mojang.brigadier.ResultConsumer;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ExecuteCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.text.TranslatableText;
import net.papierkorb2292.command_expander.CommandExpander;
import net.papierkorb2292.command_expander.commands.ExecuteVarAllPredicateCountHolder;
import net.papierkorb2292.command_expander.commands.VariableImmediateValueArgumentType;
import net.papierkorb2292.command_expander.commands.VariablePathArgumentType;
import net.papierkorb2292.command_expander.commands.VariableTypeArgumentType;
import net.papierkorb2292.command_expander.variables.ByteVariable;
import net.papierkorb2292.command_expander.variables.IntVariable;
import net.papierkorb2292.command_expander.variables.Variable;
import net.papierkorb2292.command_expander.variables.VariableHolder;
import net.papierkorb2292.command_expander.variables.path.VariablePath;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mixin(ExecuteCommand.class)
public abstract class ExecuteCommandMixin {

    private static @Final @Shadow SimpleCommandExceptionType CONDITIONAL_FAIL_EXCEPTION;
    private static @Final @Shadow DynamicCommandExceptionType CONDITIONAL_FAIL_COUNT_EXCEPTION;
    private static @Final @Shadow BinaryOperator<ResultConsumer<ServerCommandSource>> BINARY_RESULT_CONSUMER;

    private static final Dynamic2CommandExceptionType COMMAND_EXPANDER$CONDITIONAL_VAR_ALL_FAIL_EXCEPTION = new Dynamic2CommandExceptionType((count, filteredCount) -> Text.of(String.format("Test failed, count: %s of %s", filteredCount, count)));

    private static @Shadow Collection<ServerCommandSource> getSourceOrEmptyForConditionFork(CommandContext<ServerCommandSource> context, boolean positive, boolean value) {
        throw new AssertionError();
    }

    @Inject(
            method = "addConditionArguments",
            at = @At("HEAD")
    )
    private static void command_expander$addVariableCondition(CommandNode<ServerCommandSource> root, LiteralArgumentBuilder<ServerCommandSource> builder, boolean positive, CallbackInfoReturnable<ArgumentBuilder<ServerCommandSource, ?>> cir) {
        builder.then(CommandManager.literal("var")
                .requires(source -> CommandExpander.isFeatureEnabled(source.getServer(), CommandExpander.VARIABLE_FEATURE))
                .then(CommandManager.argument("value", VariableImmediateValueArgumentType.variableImmediateValue())
                        .then(command_expander$addVariableConditionOptions(root, CommandManager.literal("any"), positive, false, "value"))
                        .then(command_expander$addVariableConditionOptions(root, CommandManager.literal("all"), positive, true, "value"))
                ));
    }

    @Inject(
            method = "addStoreArguments",
            at = @At("HEAD")
    )
    private static void command_expander$addVariableStore(LiteralCommandNode<ServerCommandSource> node, LiteralArgumentBuilder<ServerCommandSource> builder, boolean requestResult, CallbackInfoReturnable<ArgumentBuilder<ServerCommandSource, ?>> cir) {
        builder.then(CommandManager.literal("var")
                .requires(source -> CommandExpander.isFeatureEnabled(source.getServer(), CommandExpander.VARIABLE_FEATURE))
                .then(CommandManager.argument("path", VariablePathArgumentType.variablePath())
                        .redirect(node, redirectContext -> {
                            VariablePath destination = VariablePathArgumentType.getVariablePath(redirectContext, "path");
                            return redirectContext.getSource().mergeConsumers((context, success, result) -> {
                                try {
                                    destination.set(
                                            Either.left(new VariableHolder(requestResult ?
                                                    new IntVariable(result) :
                                                    new ByteVariable((byte) (success ? 1 : 0)))),
                                            context);
                                } catch (CommandSyntaxException e) {
                                    context.getSource().sendError(Texts.toText(e.getRawMessage()));
                                }
                            }, BINARY_RESULT_CONSUMER);
                        })));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command_expander$addVariableConditionOptions(CommandNode<ServerCommandSource> root, LiteralArgumentBuilder<ServerCommandSource> builder, boolean positive, boolean all, String valueName) {
        return builder
                .then(CommandManager.literal("match")
                        .then(CommandManager.argument("value2", VariableImmediateValueArgumentType.variableImmediateValue())
                                .executes(positive
                                        ? (all
                                                ? context -> command_expander$executeAllPositiveMatch(valueName, "value2", context)
                                                : context -> command_expander$executeAnyPositiveMatch(valueName, "value2", context))
                                        : (all
                                                ? context -> command_expander$executeAllNegativeMatch(valueName, "value2", context)
                                                : context -> command_expander$executeAnyNegativeMatch(valueName, "value2", context)))
                                .fork(root, context -> {
                                    Either<VariableHolder, Stream<Variable>>
                                            firstValue = VariableImmediateValueArgumentType.getImmediateValue(context, valueName).calculate(context),
                                            secondValue = VariableImmediateValueArgumentType.getImmediateValue(context, "value2").calculate(context);
                                    return firstValue.map(
                                            firstHolder -> secondValue.map(
                                                    secondHolder -> getSourceOrEmptyForConditionFork(context, positive, firstHolder.variable.equals(secondHolder.variable)),
                                                    secondStream -> getSourceOrEmptyForConditionFork(context, positive, secondStream.anyMatch(second -> firstHolder.variable.equals(second)))
                                            ),
                                            firstStream -> secondValue.map(
                                                    secondHolder -> getSourceOrEmptyForConditionFork(context, positive,
                                                            all && firstStream.allMatch(first -> first.equals(secondHolder.variable)) ||
                                                                    !all && firstStream.anyMatch(first -> first.equals(secondHolder.variable))),
                                                    secondStream -> {
                                                        Set<Variable> secondValues = secondStream.collect(Collectors.toSet());
                                                        return getSourceOrEmptyForConditionFork(context, positive,
                                                                all && firstStream.allMatch(secondValues::contains) ||
                                                                        !all && firstStream.anyMatch(secondValues::contains));
                                                    }
                                            )
                                    );
                                })))
                .then(CommandManager.literal("of_type")
                        .then(CommandManager.argument("type", VariableTypeArgumentType.variableType(true))
                                .executes(positive
                                        ? (all
                                            ? context -> command_expander$executeAllPositiveOfType(valueName, "type", context)
                                            : context -> command_expander$executeAnyPositiveOfType(valueName, "type", context))
                                        : (all
                                            ? context -> command_expander$executeAllNegativeOfType(valueName, "type", context)
                                            : context -> command_expander$executeAnyNegativeOfType(valueName, "type", context)))
                                .fork(root, context -> {
                                    Variable.VariableType type = VariableTypeArgumentType.getType(context, "type");
                                    return VariableImmediateValueArgumentType.getImmediateValue(context, valueName).calculate(context).map(
                                            holder -> getSourceOrEmptyForConditionFork(context, positive, holder.getVariable().getType().instanceOf(type)),
                                            stream -> getSourceOrEmptyForConditionFork(context, positive,
                                                    all && stream.allMatch(var -> var.getType().instanceOf(type)) ||
                                                            !all && stream.anyMatch(var -> var.getType().instanceOf(type)))
                                    );
                                })));
    }

    private static int command_expander$executeAnyNegativeMatch(String firstValueName, String secondValueName, CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Either<VariableHolder, Stream<Variable>>
                firstValue = VariableImmediateValueArgumentType.getImmediateValue(context, firstValueName).calculate(context),
                secondValue = VariableImmediateValueArgumentType.getImmediateValue(context, secondValueName).calculate(context);
        int result = command_expander$getMatchingCount(firstValue, secondValue);
        if (result == 0) {
            context.getSource().sendFeedback(new TranslatableText("commands.execute.conditional.pass"), false);
            return 1;
        }
        throw CONDITIONAL_FAIL_COUNT_EXCEPTION.create(result);
    }

    private static int command_expander$executeAnyPositiveMatch(String firstValueName, String secondValueName, CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Either<VariableHolder, Stream<Variable>>
                firstValue = VariableImmediateValueArgumentType.getImmediateValue(context, firstValueName).calculate(context),
                secondValue = VariableImmediateValueArgumentType.getImmediateValue(context, secondValueName).calculate(context);
        int result = command_expander$getMatchingCount(firstValue, secondValue);
        if (result == 0) {
            throw CONDITIONAL_FAIL_EXCEPTION.create();
        }
        context.getSource().sendFeedback(new TranslatableText("commands.execute.conditional.pass_count", result), false);
        return result;
    }

    private static int command_expander$executeAllPositiveMatch(String firstValueName, String secondValueName, CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Either<VariableHolder, Stream<Variable>>
            firstValue = VariableImmediateValueArgumentType.getImmediateValue(context, firstValueName).calculate(context),
            secondValue = VariableImmediateValueArgumentType.getImmediateValue(context, secondValueName).calculate(context);
        ExecuteVarAllPredicateCountHolder count = command_expander$getMatchingAndTotalCount(firstValue, secondValue);
        if(count.count() != count.filteredCount()) {
            throw COMMAND_EXPANDER$CONDITIONAL_VAR_ALL_FAIL_EXCEPTION.create(count.count(), count.filteredCount());
        }
        context.getSource().sendFeedback(Text.of(String.format("Test passed, count: %s", count.filteredCount())), false);
        return count.count();
    }

    private static int command_expander$executeAllNegativeMatch(String firstValueName, String secondValueName, CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Either<VariableHolder, Stream<Variable>>
                firstValue = VariableImmediateValueArgumentType.getImmediateValue(context, firstValueName).calculate(context),
                secondValue = VariableImmediateValueArgumentType.getImmediateValue(context, secondValueName).calculate(context);
        ExecuteVarAllPredicateCountHolder count = command_expander$getMatchingAndTotalCount(firstValue, secondValue);
        if(count.count() == count.filteredCount()) {
            throw CONDITIONAL_FAIL_EXCEPTION.create();
        }
        context.getSource().sendFeedback(Text.of(String.format("Test passed, count: %s", count.filteredCount())), false);
        return count.count();
    }

    private static ExecuteVarAllPredicateCountHolder command_expander$getMatchingAndTotalCount(Either<VariableHolder, Stream<Variable>> firstValue, Either<VariableHolder, Stream<Variable>> secondValue) {
        return firstValue.map(
                firstHolder -> secondValue.map(
                        secondHolder -> new ExecuteVarAllPredicateCountHolder(1, firstHolder.variable.lowerAndEquals(secondHolder.variable) ? 1 : 0),
                        secondStream -> new ExecuteVarAllPredicateCountHolder(1, secondStream.anyMatch(firstHolder.variable::lowerAndEquals) ? 1 : 0)
                ),
                firstStream -> secondValue.map(
                        secondHolder -> firstStream.reduce(
                                ExecuteVarAllPredicateCountHolder.IDENTITY,
                                (holder, var) -> new ExecuteVarAllPredicateCountHolder(
                                        holder.count() + 1,
                                        holder.filteredCount() + (var.lowerAndEquals(secondHolder.variable) ? 1 : 0)),
                                ExecuteVarAllPredicateCountHolder::add),
                        secondStream -> {
                            Set<Variable> secondValues = secondStream.collect(Collectors.toSet());
                            return firstStream.reduce(
                                    ExecuteVarAllPredicateCountHolder.IDENTITY,
                                    (holder, var) -> new ExecuteVarAllPredicateCountHolder(
                                            holder.count() + 1,
                                            holder.filteredCount() + (secondValues.contains(var) ? 1 : 0)),
                                    ExecuteVarAllPredicateCountHolder::add);
                        }
                )
        );
    }

    private static int command_expander$getMatchingCount(Either<VariableHolder, Stream<Variable>> firstValue, Either<VariableHolder, Stream<Variable>> secondValue) {
        return firstValue.map(
                firstHolder -> secondValue.map(
                        secondHolder -> firstHolder.variable.lowerAndEquals(secondHolder.variable) ? 1 : 0,
                        secondStream -> secondStream.anyMatch(firstHolder.variable::lowerAndEquals) ? 1 : 0
                ),
                firstStream -> secondValue.map(
                        secondHolder -> (int)firstStream.filter(first -> first.lowerAndEquals(secondHolder.variable)).count(),
                        secondStream -> {
                            Set<Variable> secondValues = secondStream.collect(Collectors.toSet());
                            return (int)firstStream.filter(secondValues::contains).count();
                        }
                )
        );
    }

    private static int command_expander$executeAnyPositiveOfType(String valueName, String typeName, CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Either<VariableHolder, Stream<Variable>> value = VariableImmediateValueArgumentType.getImmediateValue(context, valueName).calculate(context);
        Variable.VariableType type = VariableTypeArgumentType.getType(context, typeName);
        int result = command_expander$getOfTypeCount(value, type);
        if (result == 0) {
            throw CONDITIONAL_FAIL_EXCEPTION.create();
        }
        context.getSource().sendFeedback(new TranslatableText("commands.execute.conditional.pass_count", result), false);
        return result;
    }

    private static int command_expander$executeAnyNegativeOfType(String valueName, String typeName, CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Either<VariableHolder, Stream<Variable>> value = VariableImmediateValueArgumentType.getImmediateValue(context, valueName).calculate(context);
        Variable.VariableType type = VariableTypeArgumentType.getType(context, typeName);
        int result = command_expander$getOfTypeCount(value, type);
        if (result == 0) {
            context.getSource().sendFeedback(new TranslatableText("commands.execute.conditional.pass"), false);
            return 1;
        }
        throw CONDITIONAL_FAIL_COUNT_EXCEPTION.create(result);
    }

    private static int command_expander$executeAllPositiveOfType(String valueName, String typeName, CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Either<VariableHolder, Stream<Variable>> value = VariableImmediateValueArgumentType.getImmediateValue(context, valueName).calculate(context);
        Variable.VariableType type = VariableTypeArgumentType.getType(context, typeName);
        ExecuteVarAllPredicateCountHolder count = command_expander$getOfTypeAndTotalCount(value, type);
        if(count.count() != count.filteredCount()) {
            throw COMMAND_EXPANDER$CONDITIONAL_VAR_ALL_FAIL_EXCEPTION.create(count.count(), count.filteredCount());
        }
        context.getSource().sendFeedback(Text.of(String.format("Test passed, count: %s", count.filteredCount())), false);
        return count.count();
    }

    private static int command_expander$executeAllNegativeOfType(String valueName, String typeName, CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Either<VariableHolder, Stream<Variable>> value = VariableImmediateValueArgumentType.getImmediateValue(context, valueName).calculate(context);
        Variable.VariableType type = VariableTypeArgumentType.getType(context, typeName);
        ExecuteVarAllPredicateCountHolder count = command_expander$getOfTypeAndTotalCount(value, type);
        if(count.count() == count.filteredCount()) {
            throw CONDITIONAL_FAIL_EXCEPTION.create();
        }
        context.getSource().sendFeedback(Text.of(String.format("Test passed, count: %s", count.filteredCount())), false);
        return count.count();
    }

    private static int command_expander$getOfTypeCount(Either<VariableHolder, Stream<Variable>> value, Variable.VariableType type) {
        return value.map(
                holder -> holder.variable.getType().instanceOf(type) ? 1 : 0,
                stream -> (int)stream.filter(var -> var.getType().instanceOf(type)).count()
        );
    }

    private static ExecuteVarAllPredicateCountHolder command_expander$getOfTypeAndTotalCount(Either<VariableHolder, Stream<Variable>> value, Variable.VariableType type) {
        return value.map(
                holder -> new ExecuteVarAllPredicateCountHolder(1, holder.variable.getType().instanceOf(type) ? 1 : 0),
                stream -> stream.reduce(
                        ExecuteVarAllPredicateCountHolder.IDENTITY,
                        (holder, var) -> new ExecuteVarAllPredicateCountHolder(
                                holder.count() + 1,
                                holder.filteredCount() + (var.getType().instanceOf(type) ? 1 : 0)),
                        ExecuteVarAllPredicateCountHolder::add
                )
        );
    }

}
