package net.papierkorb2292.command_expander.variables.immediate;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.command.ServerCommandSource;

import java.util.function.Predicate;

public interface NbtProvider<T extends NbtElement> {

    Predicate<NbtElement> INSTANCE_OF = element -> element instanceof NbtProvider<?>;
    T applyContext(CommandContext<ServerCommandSource> context);
}
