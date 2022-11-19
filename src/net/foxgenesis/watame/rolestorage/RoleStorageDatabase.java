package net.foxgenesis.watame.rolestorage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.sql.DataSource;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.foxgenesis.watame.rolestorage.BatchWorker.BatchData;
import net.foxgenesis.watame.sql.AbstractDatabase;
import net.foxgenesis.watame.sql.DatabaseProperties;

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
	@Nonnull
	private static final String INSERT_ROLE_KEY = "rolelist_insert_role";

	/**
	 * Statement key to remove a role from a guild member. <br>
	 * <br>
	 * {@value}
	 */
	@Nonnull
	private static final String REMOVE_ROLE_KEY = "rolelist_remove_role";

	/**
	 * Create a new instance using the provided DataSource and a threshold of
	 * {@code 1000}.
	 * <p>
	 * This constructor is effectively equivalent to <blockquote>
	 * 
	 * <pre>
	 * new RoleStorageDatabase(provider, 1000)
	 * </pre>
	 * 
	 * </blockquote>
	 * </p>
	 * 
	 * @param provider - Provider to create a {@link DataSource} to be used in this
	 *                 database
	 */
	public RoleStorageDatabase(@Nonnull Supplier<DataSource> provider) { this(provider, 1000); }

	/**
	 * Create a new instance using the provided DataSource and specified threshold.
	 * 
	 * @param provider  - Provider to create a {@link DataSource} to be used in this
	 *                  database
	 * @param batchSize - threshold for batch updates
	 */
	public RoleStorageDatabase(@Nonnull Supplier<DataSource> provider, int batchSize) {
		super(new DatabaseProperties(provider, RoleStorageDatabase.class.getResource("/assets/createRoleTable.sql"),
				RoleStorageDatabase.class.getResource("/assets/sql statements.kvp"), "Role Storage Database"));
	}

	/**
	 * Retrieve all role entries for a guld member.
	 * 
	 * @param member - guild member to retrieve roles for
	 * @return A {@link List} of {@link Role Roles} for {@code member}
	 */
	@CheckForNull
	public List<Role> getAllMemberRolesInGuild(@Nonnull Member member) {
		Guild guild = Objects.requireNonNull(member).getGuild();

		// Open a new connection with a prepared statement
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
					return Arrays.stream(roleCol.split(",")).map(roleStr -> guild.getRoleById(roleStr)).toList();
				}

				// No row was present
				return null;
			}
		}, error -> logger.error("Error while getting member roles", error));
	}

	/**
	 * Remove all database entries for a guild.
	 * 
	 * @param guild - guild to remove
	 */
	public void removeGuild(@Nonnull Guild guild) {
		// Open a new connection with a prepared statement
		callStatement("rolelist_remove_guild", statement -> {
			statement.setLong(1, guild.getIdLong());

			logger.trace(statement.toString());

			statement.executeUpdate();
		}, error -> logger.error("Error while removing guild", error));
	}

	/**
	 * Remove all roles for a guild member in the database.
	 * 
	 * @param member - guild member to remove all roles from
	 */
	public void removeAllMemberRoles(@Nonnull Member member) {
		// Open a new connection with a prepared statement
		callStatement("rolelist_remove_role_all", statement -> {

			try {
				statement.setLong(1, member.getIdLong());
				statement.setLong(2, member.getGuild().getIdLong());

				logger.trace(statement.toString());

				statement.executeUpdate();
			} catch (SQLException e) {
				logger.error("Error while removing guild", e);
			}
		});
	}

	/**
	 * Add roles to a member in the database.
	 * 
	 * @param member - member to add roles to
	 * @param roles  - a list of roles to add
	 * @throws IllegalArgumentException If {@code roles.size() < 0}
	 */
	public void addMemberRoles(@Nonnull Member member, @Nonnull Collection<Role> roles) {
		if (roles.size() < 0)
			throw new IllegalArgumentException("Unable to use empty list of roles");

		// Open a new connection with a prepared statement
		callStatement(INSERT_ROLE_KEY, statement -> {
			try {
				for (Role role : roles) {
					statement.setLong(1, member.getIdLong());
					statement.setLong(2, member.getGuild().getIdLong());
					statement.setLong(3, role.getIdLong());
				}

				logger.trace(statement.toString());

				statement.executeBatch();
			} catch (SQLException e) {
				logger.error("Error while updating member roles", e);
			}
		});
	}

	/**
	 * Remove roles from a member in the database.
	 * 
	 * @param member - member to remove roles from
	 * @param roles  - a list of roles to remove
	 * @throws IllegalArgumentException If {@code roles.size() < 0}
	 */
	public void removeMemberRoles(@Nonnull Member member, @Nonnull Collection<Role> roles) {
		if (roles.size() < 0)
			throw new IllegalArgumentException("Unable to use empty list of roles");

		// Open a new connection with a prepared statement
		callStatement(REMOVE_ROLE_KEY, statement -> {
			try {
				for (Role role : roles) {
					statement.setLong(1, member.getIdLong());
					statement.setLong(2, member.getGuild().getIdLong());
					statement.setLong(3, role.getIdLong());
				}

				logger.trace(statement.toString());

				statement.executeBatch();
			} catch (SQLException e) {
				logger.error("Error while updating member roles", e);
			}
		});
	}

	/**
	 * Create a new worker to insert/delete roles in mass.
	 * 
	 * @return A {@link BatchWorker} used to insert/delete roles in mass
	 */
	public BatchWorker getBatchWorker() {
		BatchWorker worker = new BatchWorker(new BatchData<>(this.source, new LinkedList<>(), new LinkedList<>(),
				() -> assertRawStatement(INSERT_ROLE_KEY), () -> assertRawStatement(INSERT_ROLE_KEY), 1_000));
		worker.start();
		return worker;
	}
}
