package net.papierkorb2292.command_expander.mixin;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.command.argument.ArgumentTypes;
import net.minecraft.command.argument.serialize.ArgumentSerializer;
import net.minecraft.command.argument.serialize.ConstantArgumentSerializer;
import net.minecraft.util.registry.Registry;
import net.papierkorb2292.command_expander.commands.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArgumentTypes.class)
public abstract class ArgumentTypesMixin {

    @Shadow
    private static <A extends ArgumentType<?>, T extends ArgumentSerializer.ArgumentTypeProperties<A>> ArgumentSerializer<A, T> register(Registry<ArgumentSerializer<?, ?>> registry, String id, Class<? extends A> clazz, ArgumentSerializer<A, T> serializer) {
        throw new AssertionError();
    }

    @Inject(
            method = "register(Lnet/minecraft/util/registry/Registry;)Lnet/minecraft/command/argument/serialize/ArgumentSerializer;",
            at = @At("HEAD")
    )
    private static void command_expander$registerArgumentTypes(Registry<ArgumentSerializer<?, ?>> registry, CallbackInfoReturnable<ArgumentSerializer<?, ?>> cir) {
        register(registry, "command_expander:axis", AxisArgumentType.class, ConstantArgumentSerializer.of(AxisArgumentType::axis));
        register(registry, "command_expander:multi_objective_criteria", MultiScoreboardCriterionArgumentType.class, ConstantArgumentSerializer.of(MultiScoreboardCriterionArgumentType::scoreboardCriterion));
        register(registry, "command_expander:variable_type", VariableTypeArgumentType.class, new VariableTypeArgumentType.Serializer());
        register(registry, "command_expander:variable_path", VariablePathArgumentType.class, ConstantArgumentSerializer.of(VariablePathArgumentType::variablePath));
        register(registry, "command_expander:variable_name", VariableNameArgumentType.class, ConstantArgumentSerializer.of(VariableNameArgumentType::variableName));
        register(registry, "command_expander:variable_immediate_value", VariableImmediateValueArgumentType.class, ConstantArgumentSerializer.of(VariableImmediateValueArgumentType::variableImmediateValue));
    }
}
