package net.papierkorb2292.command_expander.mixin.immediateValues;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(NbtCompound.class)
public interface NbtCompoundAccessor {

    @Accessor Map<String, NbtElement> getEntries();
}
