package net.foxgenesis.rolestorage;

import net.foxgenesis.util.resource.ConfigType;
import net.foxgenesis.watame.WatameBot;
import net.foxgenesis.watame.plugin.IEventStore;
import net.foxgenesis.watame.plugin.Plugin;
import net.foxgenesis.watame.plugin.PluginConfiguration;
import net.foxgenesis.watame.plugin.SeverePluginException;

import org.apache.commons.configuration2.Configuration;

/**
 * A {@link WatameBot} plugin used for storing roles of guild members.
 * 
 * @author Ashley
 *
 */
@PluginConfiguration(defaultFile = "/META-INF/worker.ini", identifier = "worker", outputFile = "worker.ini", type = ConfigType.INI)
public class RoleStoragePlugin extends Plugin {

	/**
	 * Listener for role updates
	 */
	private GuildListener guildListener;
	private RoleStorageDatabase database;
	private final int batchSize;

	public RoleStoragePlugin() {
		super();
		int size = 1000;

		for (String id : configurationKeySet()) {
			Configuration config = getConfiguration(id);
			switch (id) {
				case "worker" -> { size = config.getInt("batchSize", size); }
			}
		}

		this.batchSize = size;
	}

	@Override
	protected void preInit() {
		try {
			database = new RoleStorageDatabase(batchSize);
			WatameBot.INSTANCE.getDatabaseManager().register(this, database);
		} catch (Exception e) {
			throw new SeverePluginException(e, true);
		}
	}

	@Override
	protected void init(IEventStore builder) {
		guildListener = new GuildListener(this, getPropertyProvider(), database);
		builder.registerListeners(this, guildListener);
	}

	@Override
	protected void postInit(WatameBot bot) {}

	@Override
	protected void onReady(WatameBot bot) {
		// Perform initial scan of all guilds in cache
		guildListener.initialScan(bot.getJDA().getGuildCache());
	}

	@Override
	protected void close() throws Exception {
		if (guildListener != null)
			guildListener.close();
	}
}
