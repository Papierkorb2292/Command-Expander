package net.papierkorb2292.command_expander.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentStateManager;
import net.papierkorb2292.command_expander.mixin_method_interfaces.VariableManagerContainer;
import net.papierkorb2292.command_expander.variables.VariableManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin implements VariableManagerContainer {

    VariableManager command_expander$variableManager;

    @ModifyArg(
            method = "createWorlds(Lnet/minecraft/server/WorldGenerationProgressListener;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;initScoreboard(Lnet/minecraft/world/PersistentStateManager;)V"
            )
    )
    private PersistentStateManager command_expander$createVariableManager(PersistentStateManager stateManager) {
        command_expander$variableManager = new VariableManager(stateManager);
        return stateManager;
    }

    @Override
    public VariableManager command_expander$getVariableManager() {
        return command_expander$variableManager;
    }
}
