package net.foxgenesis.rolestorage;

import java.io.IOException;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.foxgenesis.config.fields.BooleanField;
import net.foxgenesis.watame.ProtectedJDABuilder;
import net.foxgenesis.watame.WatameBot;
import net.foxgenesis.watame.plugin.IPlugin;
import net.foxgenesis.watame.plugin.PluginProperties;

/**
 * A {@link WatameBot} plugin used for storing roles of guild members.
 * 
 * @author Ashley
 *
 */
@PluginProperties(name = "Role Storage", description = "Stores each users role for safe keeping", version = "0.0.1", providesCommands = false)
public class RoleStoragePlugin implements IPlugin {
	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger("RoleStorage");

	/**
	 * A boolean storage field to check if the plugin is enabled for a guild. <br>
	 * <br>
	 * <b>Key</b> = {@code rolestorage.enabled} <br>
	 * <b>Default</b> = {@code true} <br>
	 * <b>Editable</b> = {@code true} <br>
	 */
	static final BooleanField enabled = new BooleanField("rolestorage-enabled", guild -> true, true);
	// ===================================================

	/**
	 * Listener for role updates
	 */
	private GuildListener guildListener;

	@Override
	public void preInit() {
		try {
			RoleStorageDatabase database = new RoleStorageDatabase();
			WatameBot.getInstance().getDatabaseManager().register(database);
			guildListener = new GuildListener(database);
		} catch (UnsupportedOperationException | SQLException | IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void init(ProtectedJDABuilder builder) { builder.addEventListeners(guildListener); }

	@Override
	public void postInit(WatameBot bot) {

	}

	@Override
	public void onReady(WatameBot bot) {
		// Perform initial scan of all guilds in cache
		logger.info("Performing initial guild scan");
		guildListener.initialScan(bot.getJDA().getGuildCache());
	}

	@Override
	public void close() throws Exception { guildListener.close(); }

}
