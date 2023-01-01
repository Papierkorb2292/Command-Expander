package net.papierkorb2292.command_expander.mixin;

import net.minecraft.world.PersistentStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;

@Mixin(PersistentStateManager.class)
public interface PersistentStateManagerAccessor {

    @Accessor
    File getDirectory();
}
