package net.papierkorb2292.command_expander.mixin;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.command.argument.ArgumentTypes;
import net.minecraft.command.argument.serialize.ArgumentSerializer;
import net.minecraft.command.argument.serialize.ConstantArgumentSerializer;
import net.papierkorb2292.command_expander.commands.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArgumentTypes.class)
public abstract class ArgumentTypesMixin {

    @Shadow
    public static <T extends ArgumentType<?>> void register(String id, Class<T> argClass, ArgumentSerializer<T> serializer) {
        throw new AssertionError();
    }

    @Inject(
            method = "register()V",
            at = @At("HEAD")
    )
    private static void command_expander$registerArgumentTypes(CallbackInfo ci) {
        register("command_expander:axis", AxisArgumentType.class, new ConstantArgumentSerializer<>(AxisArgumentType::axis));
        register("command_expander:multi_objective_criteria", MultiScoreboardCriterionArgumentType.class, new ConstantArgumentSerializer<>(MultiScoreboardCriterionArgumentType::scoreboardCriterion));
        register("command_expander:variable_type", VariableTypeArgumentType.class, new VariableTypeArgumentType.Serializer());
        register("command_expander:variable_path", VariablePathArgumentType.class, new ConstantArgumentSerializer<>(VariablePathArgumentType::variablePath));
        register("command_expander:variable_name", VariableNameArgumentType.class, new ConstantArgumentSerializer<>(VariableNameArgumentType::variableName));
        register("command_expander:variable_immediate_value", VariableImmediateValueArgumentType.class, new ConstantArgumentSerializer<>(VariableImmediateValueArgumentType::variableImmediateValue));
    }
}
