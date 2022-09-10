package net.papierkorb2292.command_expander.mixin;

import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.papierkorb2292.command_expander.mixin_method_interfaces.VariableManagerContainer;
import net.papierkorb2292.command_expander.variables.VariableManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(Scoreboard.class)
public class ScoreboardMixin {

    @Inject(method = "forEachScore", at = @At("HEAD"))
    private void command_expander$updateBoundVariables(ScoreboardCriterion criterion, String player, Consumer<ScoreboardPlayerScore> action, CallbackInfo ci) {
        //noinspection ConstantConditions <- Complaint about ScoreboardMixin not being an instance of ServerScoreboard
        if((Object)this instanceof ServerScoreboard) {
            MinecraftServer server = ((ServerScoreboardAccessor) this).getServer();
            VariableManager manager = ((VariableManagerContainer) server).command_expander$getVariableManager();
            manager.updateBoundVariables(criterion, player, action);
        }
    }
}
