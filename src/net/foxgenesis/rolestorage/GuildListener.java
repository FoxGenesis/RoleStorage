package net.foxgenesis.rolestorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.foxgenesis.property.PropertyMapping;
import net.foxgenesis.property.PropertyType;
import net.foxgenesis.watame.plugin.Plugin;
import net.foxgenesis.watame.property.PluginProperty;
import net.foxgenesis.watame.property.PluginPropertyProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.cache.SnowflakeCacheView;

/**
 * Class for listening to role updates in a guild. All role updates are stored
 * into a database.
 * 
 * @author Ashley
 *
 */
public class GuildListener extends ListenerAdapter implements AutoCloseable {

	/**
	 * Logger
	 */
	private static final Logger logger = LoggerFactory.getLogger("Role Storage Listener");

	/**
	 * Property to enable/disable role storage
	 */
	private PluginProperty enabled;

	/**
	 * Database to use for role storage
	 */
	private RoleStorageDatabase database;

	/**
	 * Construct a new listener to listen to guild updates.
	 */
	public GuildListener(Plugin plugin, PluginPropertyProvider provider, RoleStorageDatabase database) {
		this.database = Objects.requireNonNull(database);
		enabled = provider.upsertProperty(plugin, "enabled", true, PropertyType.NUMBER);
	}

	@Override
	public void onGuildJoin(GuildJoinEvent event) {
		scanGuild(event.getGuild());
	}

	@Override
	public void onGuildLeave(GuildLeaveEvent event) {
		database.removeGuild(event.getGuild());
	}

	@Override
	public void onGuildMemberJoin(GuildMemberJoinEvent event) {
		Guild guild = event.getGuild();
		if (enabled.get(guild, () -> false, PropertyMapping::getAsBoolean)) {
			Member member = event.getMember();
			Member bot = guild.getSelfMember();

			if (bot.hasPermission(Permission.MANAGE_ROLES)) {
				List<Role> roles = database.getAllMemberRolesInGuild(member, role -> role != null && bot.canInteract(role) && !role.isManaged());
				if (!roles.isEmpty()) {
					logger.debug("Giving roles {} to {} in {}", roles, member, guild);
					guild.modifyMemberRoles(member, roles).queue();
				}
			}
		}
	}

	@Override
	public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
		Guild guild = event.getGuild();

		if (enabled.get(guild, () -> false, PropertyMapping::getAsBoolean)) {
			Member member = event.getMember();
			List<Role> roles = new ArrayList<>(event.getRoles());
			roles.removeIf(Role::isManaged);
			logger.debug("Adding roles ({}) for {} in {}", roles, member, guild);
			database.addMemberRoles(member, roles);
		}
	}

	@Override
	public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
		Guild guild = event.getGuild();

		if (enabled.get(guild, () -> false, PropertyMapping::getAsBoolean)) {
			Member member = event.getMember();
			List<Role> roles = event.getRoles();

			logger.debug("Removing roles ({}) for {} in {}", roles, member, guild);
			database.removeMemberRoles(member, roles);
		}
	}

	@Override
	public void close() throws Exception {
		database.close();
	}

	/**
	 * Perform a full scan of all guilds in the cache.
	 * 
	 * @param cache - {@link JDA} guild cache
	 */
	public void initialScan(SnowflakeCacheView<Guild> cache) {
		logger.info("Performing initial guild scan");

		long start = System.currentTimeMillis();
		cache.acceptStream(stream -> stream.forEach(this::scanGuild));
		double end = (System.currentTimeMillis() - start) / 1000D;

		logger.info("Finished inital scan in {}", "%.2f ms".formatted(end));
	}

	/**
	 * Scan a guild for all member roles and upsert them into the database.
	 * 
	 * @param guild - Guild to scan
	 */
	private void scanGuild(Guild guild) {
		if (enabled.get(guild, () -> false, PropertyMapping::getAsBoolean)) {
			logger.info("Scanning {} for roles...", guild.getName());

			try (BatchWorker worker = database.getBatchWorker()) {
				long startTime = System.currentTimeMillis();

				guild.getMemberCache().acceptStream(stream -> {
					stream.forEach(member -> worker.addMemberRoles(member, member.getRoles().stream().filter(r -> !r.isManaged()).toList()));
					double end = (System.currentTimeMillis() - startTime) / 1000D;
					logger.info("Finished scanning {} for roles in {}", guild.getName(), "%.2f ms".formatted(end));
				});
			}
		}
	}
}
