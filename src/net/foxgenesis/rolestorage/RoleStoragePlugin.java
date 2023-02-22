package net.foxgenesis.rolestorage;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Guild;
import net.foxgenesis.property.IPropertyField;
import net.foxgenesis.watame.ProtectedJDABuilder;
import net.foxgenesis.watame.WatameBot;
import net.foxgenesis.watame.plugin.Plugin;
import net.foxgenesis.watame.plugin.PluginConfiguration;
import net.foxgenesis.watame.property.IGuildPropertyMapping;

/**
 * A {@link WatameBot} plugin used for storing roles of guild members.
 * 
 * @author Ashley
 *
 */
@PluginConfiguration(defaultFile = "/META-INF/worker.ini", identifier = "worker", outputFile = "worker.ini")
public class RoleStoragePlugin extends Plugin {
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
	// static final BooleanField enabled = new BooleanField("rolestorage-enabled",
	// guild -> true, true);
	static final IPropertyField<String, Guild, IGuildPropertyMapping> enabled = WatameBot.getInstance()
			.getPropertyProvider().getProperty("rolestorage-enabled");
	// ===================================================

	/**
	 * Listener for role updates
	 */
	private GuildListener guildListener;
	private int batchSize;

	@Override
	protected void onPropertiesLoaded(Properties properties) {}

	@Override
	protected void onConfigurationLoaded(String id, PropertiesConfiguration properties) {
		switch (id) {
		case "worker" -> { batchSize = properties.getInt("batchSize", 1000); }
		}
	}

	@Override
	protected void preInit() {
		try {
			RoleStorageDatabase database = new RoleStorageDatabase(batchSize);
			WatameBot.getInstance().getDatabaseManager().register(this, database);
			guildListener = new GuildListener(database);
		} catch (UnsupportedOperationException | SQLException | IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void init(ProtectedJDABuilder builder) { builder.addEventListeners(guildListener); }

	@Override
	protected void postInit(WatameBot bot) {

	}

	@Override
	protected void onReady(WatameBot bot) {
		// Perform initial scan of all guilds in cache
		logger.info("Performing initial guild scan");
		guildListener.initialScan(bot.getJDA().getGuildCache());
	}

	@Override
	public void close() throws Exception { guildListener.close(); }
}
