package net.papierkorb2292.command_expander.mixin.immediateValues;

import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(NbtList.class)
public interface NbtListAccessor {

    @Accessor
    List<NbtElement> getValue();
}
