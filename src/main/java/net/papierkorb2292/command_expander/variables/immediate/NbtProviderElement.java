package net.papierkorb2292.command_expander.variables.immediate;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtType;
import net.minecraft.nbt.scanner.NbtScanner;
import net.minecraft.nbt.visitor.NbtElementVisitor;
import net.minecraft.server.command.ServerCommandSource;

import java.io.DataOutput;
import java.io.IOException;
import java.util.function.Function;

public class NbtProviderElement implements NbtElement, NbtProvider<NbtElement> {

    private final Function<CommandContext<ServerCommandSource>, NbtElement> rootSupplier;

    public NbtProviderElement(Function<CommandContext<ServerCommandSource>, NbtElement> rootSupplier) {
        this.rootSupplier = rootSupplier;
    }

    public NbtElement applyContext(CommandContext<ServerCommandSource> context) {
        return rootSupplier.apply(context);
    }

    @Override
    public void write(DataOutput output) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte getType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public NbtType<?> getNbtType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public NbtElement copy() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void accept(NbtElementVisitor visitor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public NbtScanner.Result doAccept(NbtScanner visitor) {
        throw new UnsupportedOperationException();
    }
}
