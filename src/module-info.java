module watamebot.rolestorage {
	exports net.foxgenesis.rolestorage;

	requires transitive net.dv8tion.jda;
	requires transitive watamebot;
	requires transitive com.zaxxer.hikari;
	requires transitive java.sql;
	requires transitive org.slf4j;

	provides net.foxgenesis.watame.plugin.Plugin with net.foxgenesis.rolestorage.RoleStoragePlugin;
}