package net.papierkorb2292.command_expander.mixin_method_interfaces;

import net.minecraft.command.EntitySelectorReader;

public interface EntitySelectorReaderEqualityChecker {

    boolean command_expander$isEqual(EntitySelectorReader other);
}
