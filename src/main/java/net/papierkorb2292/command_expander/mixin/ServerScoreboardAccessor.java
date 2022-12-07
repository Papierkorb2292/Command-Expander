package net.papierkorb2292.command_expander.mixin;

import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerScoreboard.class)
public interface ServerScoreboardAccessor {

    @Accessor MinecraftServer getServer();
}
