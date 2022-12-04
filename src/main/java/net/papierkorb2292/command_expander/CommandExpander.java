package net.papierkorb2292.command_expander;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.gamerule.v1.CustomGameRuleCategory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_expander.commands.VarCommand;
import net.papierkorb2292.command_expander.mixin_method_interfaces.VariableManagerContainer;
import net.papierkorb2292.command_expander.variables.VariableManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CommandExpander implements ModInitializer {
//TODO: Add command block output watcher
	public static final Logger LOGGER = LogManager.getLogger();
	private static final FeatureManager FEATURE_MANAGER = new FeatureManager(
			new CustomGameRuleCategory(
					new Identifier("command_expander", "features"),
					new LiteralText("Command Expander")
							.styled(style -> style.withColor(Formatting.AQUA))));

	@Override
	public void onInitialize() {

		CommandRegistrationCallback.EVENT.register(VarCommand::register);

		FEATURE_MANAGER.addFeature("Variables", false);

		LOGGER.info("Loaded Command Expander");
	}

	public static VariableManager getVariableManager(CommandContext<ServerCommandSource> context) {
		return ((VariableManagerContainer)context.getSource().getServer()).command_expander$getVariableManager();
	}

	public static Text buildCopyableText(String text) {
		return new LiteralText(text).styled(style ->
				style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text))
						.withUnderline(true)
						.withColor(0x55FF55)
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of("Click to copy"))));
	}

	public static boolean isFeatureEnabled(MinecraftServer server, String name) {
		return FEATURE_MANAGER.isEnabled(server, name);
	}

	@SuppressWarnings("RedundantThrows")
	public static <Return, Throws extends Throwable> Return callThrowingWrapOperation(Operation<Return> op, Object... args) throws Throws {
		return op.call(args);
	}
}
