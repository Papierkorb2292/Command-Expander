package net.papierkorb2292.command_expander.variables.immediate;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_expander.mixin.immediateValues.NbtCompoundAccessor;

import java.util.Map;

public class NbtProviderCompound extends NbtCompound implements NbtProvider<NbtCompound> {

    public NbtProviderCompound(NbtCompound original) {
        super(((NbtCompoundAccessor)original).getEntries());
    }

    public static NbtCompound createProviderIfNeeded(NbtCompound original) {
        if(((NbtCompoundAccessor)original).getEntries().values().stream().anyMatch(NbtProvider.INSTANCE_OF)) {
            return new NbtProviderCompound(original);
        }
        return original;
    }

    @Override
    public NbtCompound applyContext(CommandContext<ServerCommandSource> context) {
        NbtCompound result = new NbtCompound();
        for(Map.Entry<String, NbtElement> entry : ((NbtCompoundAccessor)this).getEntries().entrySet()) {
            NbtElement value = entry.getValue();
            if(value instanceof NbtProvider<?> provider) {
                value = provider.applyContext(context);
            }
            result.put(entry.getKey(), value);
        }
        return result;
    }
}
