package net.papierkorb2292.command_expander;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.papierkorb2292.command_expander.commands.VarCommand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CommandExpander implements ModInitializer {

	public static final Logger LOGGER = LogManager.getLogger();

	@Override
	public void onInitialize() {

		CommandRegistrationCallback.EVENT.register(VarCommand::register);

		LOGGER.info("Loaded Command Expander");
	}
}
