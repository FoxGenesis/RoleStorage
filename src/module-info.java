module watamebot.rolestorage {
	exports net.foxgenesis.rolestorage;
	requires transitive net.dv8tion.jda;
	requires transitive watamebot;
	requires com.zaxxer.hikari;
	requires java.sql;
	requires org.slf4j;
	requires jsr305;
	
	provides net.foxgenesis.watame.plugin.Plugin with net.foxgenesis.rolestorage.RoleStoragePlugin;
}