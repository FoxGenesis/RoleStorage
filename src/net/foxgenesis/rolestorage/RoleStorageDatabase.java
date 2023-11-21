package net.foxgenesis.rolestorage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import net.foxgenesis.database.AbstractDatabase;
import net.foxgenesis.rolestorage.BatchWorker.BatchData;
import net.foxgenesis.util.resource.ModuleResource;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

/**
 * Custom database class used for storing roles of guild members.
 * 
 * @author Ashley
 *
 */
public class RoleStorageDatabase extends AbstractDatabase {
	/**
	 * Statement key to insert a new role for a guild member. <br>
	 * <br>
	 * {@value}
	 */

	private static final String INSERT_ROLE_KEY = "rolelist_insert_role";

	/**
	 * Statement key to remove a role from a guild member. <br>
	 * <br>
	 * {@value}
	 */

	private static final String REMOVE_ROLE_KEY = "rolelist_remove_role";

	private final int batchSize;

	public RoleStorageDatabase() {
		this(1000);
	}

	/**
	 * Create a new instance using the provided DataSource and specified threshold.
	 * 
	 * @param batchSize - threshold for batch updates
	 */
	public RoleStorageDatabase(int batchSize) {
		super("RoleStorage Database", new ModuleResource("watamebot.rolestorage", "/META-INF/sql statements.kvp"),
				new ModuleResource("watamebot.rolestorage", "/META-INF/createRoleTable.sql"));
		this.batchSize = batchSize;
	}

	/**
	 * Retrieve all role entries for a guld member.
	 * 
	 * @param member - guild member to retrieve roles for
	 * 
	 * @return A {@link List} of {@link Role Roles} for {@code member}
	 */

	public List<Role> getAllMemberRolesInGuild(Member member, Predicate<Role> filter) {
		Guild guild = Objects.requireNonNull(member).getGuild();

		// Open a new connection with a prepared statement
		try {
			return mapStatement("rolelist_get_all_roles", statement -> {
				statement.setLong(1, member.getIdLong());
				statement.setLong(2, guild.getIdLong());

				logger.trace(statement.toString());

				// Execute query
				try (ResultSet result = statement.executeQuery()) {

					// Get first row if present
					if (result.next()) {

						// Get roles column
						String roleCol = result.getString("Roles");

						// If empty or column wasn't found then return null
						if (roleCol == null || roleCol.isEmpty())
							return null;

						// Split role column and then map them to the role objects of the guild
						return Arrays.stream(roleCol.split(",")).map(roleStr -> guild.getRoleById(roleStr))
								.filter(Objects.requireNonNullElse(filter, role -> true)).toList();
					}

					// No row was present
					return null;
				}
			}).orElse(List.of());
		} catch (SQLException e) {
			logger.error("Error while getting member roles", e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * Remove all database entries for a guild.
	 * 
	 * @param guild - guild to remove
	 */
	public void removeGuild(Guild guild) {
		// Open a new connection with a prepared statement
		try {
			prepareStatement("rolelist_remove_guild", statement -> {
				statement.setLong(1, guild.getIdLong());

				logger.trace(statement.toString());

				statement.executeUpdate();
			});
		} catch (SQLException e) {
			logger.error("Error while removing guild", e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * Remove all roles for a guild member in the database.
	 * 
	 * @param member - guild member to remove all roles from
	 */
	public void removeAllMemberRoles(Member member) {
		// Open a new connection with a prepared statement
		try {
			prepareStatement("rolelist_remove_role_all", statement -> {
				statement.setLong(1, member.getIdLong());
				statement.setLong(2, member.getGuild().getIdLong());

				logger.trace(statement.toString());

				statement.executeUpdate();
			});
		} catch (SQLException e) {
			logger.error("Error while removing guild", e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * Add roles to a member in the database.
	 * 
	 * @param member - member to add roles to
	 * @param roles  - a list of roles to add
	 * 
	 * @throws IllegalArgumentException If {@code roles.size() < 0}
	 */
	public void addMemberRoles(Member member, Collection<Role> roles) {
		if (roles.size() < 0)
			throw new IllegalArgumentException("Unable to use empty list of roles");

		// Open a new connection with a prepared statement
		try {
			prepareStatement(INSERT_ROLE_KEY, statement -> {
				for (Role role : roles) {
					statement.setLong(1, member.getIdLong());
					statement.setLong(2, member.getGuild().getIdLong());
					statement.setLong(3, role.getIdLong());
				}

				logger.trace(statement.toString());

				statement.executeBatch();
			});
		} catch (SQLException e) {
			logger.error("Error while updating member roles", e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * Remove roles from a member in the database.
	 * 
	 * @param member - member to remove roles from
	 * @param roles  - a list of roles to remove
	 * 
	 * @throws IllegalArgumentException If {@code roles.size() < 0}
	 */
	public void removeMemberRoles(Member member, Collection<Role> roles) {
		if (roles.size() < 0)
			throw new IllegalArgumentException("Unable to use empty list of roles");

		// Open a new connection with a prepared statement
		try {
			prepareStatement(REMOVE_ROLE_KEY, statement -> {
				for (Role role : roles) {
					statement.setLong(1, member.getIdLong());
					statement.setLong(2, member.getGuild().getIdLong());
					statement.setLong(3, role.getIdLong());
				}

				logger.trace(statement.toString());

				statement.executeBatch();
			});
		} catch (SQLException e) {
			logger.error("Error while updating member roles", e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * Create a new worker to insert/delete roles in mass.
	 * 
	 * @return A {@link BatchWorker} used to insert/delete roles in mass
	 */
	public BatchWorker getBatchWorker() {
		try {
			BatchWorker worker = new BatchWorker(
					new BatchData<>(openConnection(), new LinkedList<>(), new LinkedList<>(),
							() -> getRawStatement(INSERT_ROLE_KEY), () -> getRawStatement(INSERT_ROLE_KEY), batchSize));
			worker.start();
			return worker;
		} catch (Exception e) {

			return null;
		}
	}

	@Override
	public void close() throws Exception {}

	@Override
	protected void onReady() {}
}
