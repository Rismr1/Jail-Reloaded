package com.graywolf336.jail;

import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.graywolf336.jail.beans.Cell;
import com.graywolf336.jail.beans.Jail;
import com.graywolf336.jail.beans.Prisoner;
import com.graywolf336.jail.beans.SimpleLocation;
import com.graywolf336.jail.enums.Lang;
import com.graywolf336.jail.enums.Settings;

/**
 * Handles all the saving and loading of the plugin's data.
 *
 * @author graywolf336
 * @since 2.x.x
 * @version 3.0.0
 *
 */
public class JailIO {
    private JailMain pl;
    private FileConfiguration flat, records;
    private Connection con;
    private int storage = -1; //0 = flatfile, 1 = sqlite, 2 = mysql
    private String prefix;
    private boolean changed = false;
    private String storageIdentity;
    private long failures;

    public long getFailureCount() { return failures; }

    private void storageFailure(Exception failure) {
        failures++;
        if(pl.inDebug()) pl.getLogger().log(java.util.logging.Level.WARNING, "[DEBUG] Jail storage operation failed.", failure);
    }

    protected JailIO(JailMain plugin) {
        this.pl = plugin;
    }

    /** Loads the language file from disk, if there is none then we save the default one. */
    protected void loadLanguage() {
        LocaleManager.load(pl);
    }

    /**
     * Prepares the storage engine to be used, returns true if everything went good.
     * 
     * @param doInitialCreations whether to do the initial creations (database tables or files)
     * @return Whether it was successful or not
     */
    protected boolean prepareStorage(boolean doInitialCreations) {
        Connection previousConnection = con;
        int previousStorage = storage;
        String previousPrefix = prefix;
        String previousIdentity = storageIdentity;
        FileConfiguration previousFlat = flat, previousRecords = records;
        boolean previousChanged = changed;
        con = null;
        boolean ready = false;
        try {
            ready = prepareStorageReplacement(doInitialCreations);
            return ready;
        } catch(Exception failure) {
            storageFailure(failure);
            pl.getLogger().warning("Storage preparation failed: " + JailConsole.clean(failure.getMessage()));
            return false;
        } finally {
            if(ready) {
                if(previousConnection != null) {
                    try { previousConnection.close(); }
                    catch(SQLException failure) { storageFailure(failure); pl.getLogger().warning("Unable to close replaced storage connection: " + failure.getMessage()); }
                }
            } else {
                if(con != null) {
                    try { con.close(); } catch(SQLException ignored) { }
                }
                con = previousConnection;
                storage = previousStorage;
                prefix = previousPrefix;
                storageIdentity = previousIdentity;
                flat = previousFlat;
                records = previousRecords;
                changed = previousChanged;
            }
        }
    }

    private boolean prepareStorageReplacement(boolean doInitialCreations) {
        int inital = storage == -1 ? -1 : storage;

        String st = pl.getConfig().getString("storage.type", "sqlite");
        if(!st.equalsIgnoreCase("sqlite") && !st.equalsIgnoreCase("mysql") && !st.equalsIgnoreCase("flatfile")) {
            pl.getLogger().severe("Unknown storage.type: " + st + ". Choose sqlite, mysql, or flatfile.");
            return false;
        }
        if(st.equalsIgnoreCase("sqlite")) {
            storage = 1;
            prefix = pl.getConfig().getString("storage.mysql.prefix");
        }else if(st.equalsIgnoreCase("mysql")) {
            storage = 2;
            prefix = pl.getConfig().getString("storage.mysql.prefix");
        }else {
            storage = 0;
        }

        String identity = st.toLowerCase(java.util.Locale.ROOT);
        if(storage > 0) identity += ":" + prefix;
        if(storage == 2) identity += ":" + pl.getConfig().getString("storage.mysql.host")
                + ":" + pl.getConfig().getString("storage.mysql.port")
                + ":" + pl.getConfig().getString("storage.mysql.database");
        if(storageIdentity != null && !storageIdentity.equals(identity)) changed = true;
        storageIdentity = identity;

        //Determine if we changed storage types midstream
        //this way we can know whether to save EVERYTHING
        //or not afterwards
        if(inital != -1 && inital != storage) {
            pl.debug("We changed storage types! We used to be " + inital + " and changed to " + storage + ".");
            changed = true;
        }

        pl.debug("The storage type " + st + " with the type being " + storage + ".");
        pl.debug("Storage type selected: " + st);

        switch(storage) {
            case 1:
                try {
                    Class.forName("org.sqlite.JDBC");
                    pl.debug("Connecting to SQLite storage.");
                    Connection sqliteConnection = DriverManager.getConnection("jdbc:sqlite:" +  new File(pl.getDataFolder().getPath(), "jail3.sqlite").getPath());
                    sqliteConnection.setAutoCommit(true);
                    this.con = sqliteConnection;
                    pl.debug("Connection created for sqlite.");

                    if(doInitialCreations) createTables();
                } catch (ClassNotFoundException e) {
                    storageFailure(e);

                    pl.getLogger().severe("Sqlite driver not found, disabling the plugin.");
                    return false;
                } catch (SQLException e) {
                    storageFailure(e);

                    pl.getLogger().severe("Unable to connect to the sqlite database, please update your config accordingly.");
                    return false;
                }

                break;
            case 2:
                try {
                    try {
                        Class.forName("com.mysql.cj.jdbc.Driver");
                    } catch (ClassNotFoundException modernDriverMissing) {
                        Class.forName("com.mysql.jdbc.Driver");
                    }
                    pl.debug("Connecting to MySQL storage.");
                    Connection mysqlConnection = DriverManager.getConnection("jdbc:mysql://" + pl.getConfig().getString("storage.mysql.host") + ":"
                            + pl.getConfig().getString("storage.mysql.port") + "/"
                            + pl.getConfig().getString("storage.mysql.database"), pl.getConfig().getString("storage.mysql.username"), pl.getConfig().getString("storage.mysql.password"));
                    mysqlConnection.setAutoCommit(true);
                    this.con = mysqlConnection;
                    pl.debug("Connection created for MySQL.");

                    if(doInitialCreations) createTables();
                } catch(ClassNotFoundException e) {
                    storageFailure(e);

                    pl.getLogger().severe("MySQL driver not found, disabling the plugin.");
                    return false;
                } catch (SQLException e) {
                    storageFailure(e);

                    pl.getLogger().severe("Unable to connect to the MySQL database, please update your config accordingly.");
                    return false;
                }

                break;
            default:
                flat = loadYaml(new File(pl.getDataFolder(), "data.yml"));
                records = loadYaml(new File(pl.getDataFolder(), "records.yml"));
                break;
        }

        if(changed) {
            changed = false;
            long before = failures;
            Connection destination = con;
            byte[] previousFile = null;
            File dataFile = new File(pl.getDataFolder(), "data.yml");
            try {
                if(storage > 0) destination.setAutoCommit(false);
                else if(dataFile.isFile()) previousFile = java.nio.file.Files.readAllBytes(dataFile.toPath());
                saveEverything();
                if(failures != before) throw new SQLException("Could not copy all active jail data to replacement storage");
                if(storage > 0) destination.commit();
            } catch(Exception failure) {
                if(storage > 0) { try { destination.rollback(); } catch(SQLException rollback) { failure.addSuppressed(rollback); } }
                else if(previousFile != null) { try { java.nio.file.Files.write(dataFile.toPath(), previousFile); } catch(IOException rollback) { failure.addSuppressed(rollback); } }
                for(Jail jail : pl.getJailManager().getJails()) {
                    for(Prisoner prisoner : jail.getAllPrisoners().values()) prisoner.setChanged(true);
                    for(Cell cell : jail.getCells()) cell.setChanged(true);
                }
                storageFailure(failure);
                pl.getLogger().warning("Storage migration rejected; previous storage retained.");
                return false;
            } finally {
                if(storage > 0) { try { destination.setAutoCommit(true); } catch(SQLException reset) { storageFailure(reset); } }
            }
        }

        return true;
    }

    /**
     * Gets the connection for the sqlite and mysql, null if flatfile.
     *
     * @return The connection for the sql database.
     */
    private Connection getConnection() {
        if(storage == 0) return null;
        try {
            if(con == null || con.isClosed() || (storage == 2 && !con.isValid(2))) {
                if(!prepareStorage(false) || con == null || con.isClosed())
                    throw new IllegalStateException("Jail storage is unavailable; operation retained for retry");
            }
            return con;
        } catch(SQLException failure) { storageFailure(failure); throw new IllegalStateException("Jail storage is unavailable", failure); }
    }

    private static YamlConfiguration loadYaml(File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        try { if(file.isFile()) yaml.load(file); return yaml; }
        catch(Exception failure) { throw new IllegalStateException("Invalid or unreadable " + file.getName() + "; original file retained", failure); }
    }

    private static void saveYaml(FileConfiguration yaml, File file) throws IOException {
        java.nio.file.Path destination = file.toPath();
        java.nio.file.Files.createDirectories(destination.toAbsolutePath().getParent());
        java.nio.file.Path temporary = java.nio.file.Files.createTempFile(destination.toAbsolutePath().getParent(), file.getName(), ".tmp");
        try {
            java.nio.file.Files.writeString(temporary, yaml.saveToString(), java.nio.charset.StandardCharsets.UTF_8);
            try { java.nio.file.Files.move(temporary, destination, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            catch(java.nio.file.AtomicMoveNotSupportedException unsupported) {
                java.nio.file.Files.move(temporary, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { java.nio.file.Files.deleteIfExists(temporary); }
    }

    /** Closes the sql connection. */
    protected void closeConnection() {
        switch(storage) {
            case 1:
            case 2:
                try {
                    if(con != null) {
                        con.close();
                        con = null;

                        pl.debug("Closed the SQL connection.");
                    }
                } catch (SQLException e) {
                    storageFailure(e);

                    pl.getLogger().severe("Unable to close the SQL connection.");
                }

                break;
            default:
                break;
        }
    }

    private void createTables() throws SQLException {
        if(getConnection() == null) {
            pl.debug("The connection was null when we tried to create a table.");
            throw new SQLException("No database connection is available for table creation.");
        }

        try {
            Statement st = getConnection().createStatement();
            switch(storage){
                case 1:
                    String sqlJailCreateCmd = "CREATE TABLE IF NOT EXISTS `" + prefix + "jails` ("
                            + "`name` VARCHAR PRIMARY KEY NOT NULL,"
                            + "`world` VARCHAR NOT NULL,"
                            + "`top.x` INT NOT NULL,"
                            + "`top.y` INT NOT NULL,"
                            + "`top.z` INT NOT NULL,"
                            + "`bottom.x` INT NOT NULL,"
                            + "`bottom.y` INT NOT NULL,"
                            + "`bottom.z` INT NOT NULL,"
                            + "`tps.in.x` DOUBLE NOT NULL,"
                            + "`tps.in.y` DOUBLE NOT NULL,"
                            + "`tps.in.z` DOUBLE NOT NULL,"
                            + "`tps.in.yaw` DOUBLE NOT NULL,"
                            + "`tps.in.pitch` DOUBLE NOT NULL,"
                            + "`tps.free.world` VARCHAR NOT NULL,"
                            + "`tps.free.x` DOUBLE NOT NULL,"
                            + "`tps.free.y` DOUBLE NOT NULL,"
                            + "`tps.free.z` DOUBLE NOT NULL,"
                            + "`tps.free.yaw` DOUBLE NOT NULL,"
                            + "`tps.free.pitch` DOUBLE NOT NULL);";

                    st.executeUpdate(sqlJailCreateCmd);
                    st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS `" + prefix + "jails_name` on `" + prefix + "jails` (`name`) ");

                    String sqlCellCreateCmd = "CREATE TABLE IF NOT EXISTS `" + prefix + "cells` ("
                            + "`cellid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                            + "`name` VARCHAR NOT NULL,"
                            + "`jail` VARCHAR NOT NULL,"
                            + "`tp.x` DOUBLE NOT NULL,"
                            + "`tp.y` DOUBLE NOT NULL,"
                            + "`tp.z` DOUBLE NOT NULL,"
                            + "`tp.yaw` DOUBLE NOT NULL,"
                            + "`tp.pitch` DOUBLE NOT NULL,"
                            + "`chest.x` INT NULL,"
                            + "`chest.y` INT NULL,"
                            + "`chest.z` INT NULL,"
                            + "`signs` VARCHAR NULL);";

                    st.executeUpdate(sqlCellCreateCmd);
                    st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS `" + prefix + "cells_cellid` on `" + prefix + "cells` (`cellid`) ");

                    String sqlPrisCreateCmd = "CREATE TABLE IF NOT EXISTS `" + prefix + "prisoners` ("
                            + "`uuid` VARCHAR PRIMARY KEY NOT NULL,"
                            + "`name` VARCHAR NOT NULL,"
                            + "`jail` VARCHAR NOT NULL,"
                            + "`cell` VARCHAR NULL,"
                            + "`muted` TINYINT NOT NULL,"
                            + "`time` BIGINT NOT NULL,"
                            + "`offlinePending` TINYINT NOT NULL,"
                            + "`toBeTransferred` TINYINT NOT NULL,"
                            + "`jailer` VARCHAR NOT NULL,"
                            + "`reason` VARCHAR NOT NULL,"
                            + "`inventory` BLOB NULL,"
                            + "`armor` BLOB NULL,"
                            + "`previousLocation` VARCHAR(250) NULL,"
                            + "`previousGameMode` VARCHAR(16) NULL);";

                    st.executeUpdate(sqlPrisCreateCmd);
                    st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS `" + prefix + "prisoners_uuid` on `" + prefix + "prisoners` (`uuid`) ");

                    String sqlProCreateCmd = "CREATE TABLE IF NOT EXISTS `" + prefix + "records` ("
                            + "`recordid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                            + "`uuid` VARCHAR NOT NULL,"
                            + "`username` VARCHAR NOT NULL,"
                            + "`jailer` VARCHAR NOT NULL,"
                            + "`date` VARCHAR NOT NULL,"
                            + "`time` BIGINT NOT NULL,"
                            + "`reason` VARCHAR NOT NULL);";

                    st.executeUpdate(sqlProCreateCmd);
                    st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS `" + prefix + "records_recordid` on `" + prefix + "records` (`recordid`) ");
                    st.close();
                    break;
                case 2:
                    String jailCreateCmd = "CREATE TABLE IF NOT EXISTS `" + prefix + "jails` ("
                            + "`name` VARCHAR(250) NOT NULL,"
                            + "`world` VARCHAR(250) NOT NULL COMMENT 'The world for the top, bottom, and teleport in.',"
                            + "`top.x` INT NOT NULL COMMENT 'The top coordinate x.',"
                            + "`top.y` INT NOT NULL COMMENT 'The top coordinate y.',"
                            + "`top.z` INT NOT NULL COMMENT 'The top coordinate z.',"
                            + "`bottom.x` INT NOT NULL COMMENT 'The bottom coordinate x.',"
                            + "`bottom.y` INT NOT NULL COMMENT 'The bottom coordinate y.',"
                            + "`bottom.z` INT NOT NULL COMMENT 'The bottom coordinate z.',"
                            + "`tps.in.x` DOUBLE NOT NULL COMMENT 'The teleport in x coordinate.',"
                            + "`tps.in.y` DOUBLE NOT NULL COMMENT 'The teleport in y coordinate.',"
                            + "`tps.in.z` DOUBLE NOT NULL COMMENT 'The teleport in z coordinate.',"
                            + "`tps.in.yaw` DOUBLE NOT NULL COMMENT 'The teleport in yaw.',"
                            + "`tps.in.pitch` DOUBLE NOT NULL COMMENT 'The teleport in pitch.',"
                            + "`tps.free.world` VARCHAR(250) NOT NULL COMMENT 'The teleport for being free world.',"
                            + "`tps.free.x` DOUBLE NOT NULL COMMENT 'The teleport for being free x coordinate.',"
                            + "`tps.free.y` DOUBLE NOT NULL COMMENT 'The teleport for being free y coordinate.',"
                            + "`tps.free.z` DOUBLE NOT NULL COMMENT 'The teleport for being free z coordinate.',"
                            + "`tps.free.yaw` DOUBLE NOT NULL COMMENT 'The teleport for being free yaw.',"
                            + "`tps.free.pitch` DOUBLE NOT NULL COMMENT 'The teleport for being free pitch.',"
                            + "PRIMARY KEY (`name`),"
                            + "UNIQUE INDEX `name_UNIQUE` (`name` ASC))"
                            + "COMMENT = 'Holds all the jails for the Bukkit Jail plugin.';";

                    //pl.debug(jailCreateCmd);
                    st.executeUpdate(jailCreateCmd);

                    String cellCreateCmd = "CREATE TABLE IF NOT EXISTS `" + prefix + "cells` ("
                            + "`cellid` INT NOT NULL AUTO_INCREMENT COMMENT 'The cellid for the database.',"
                            + "`name` VARCHAR(250) NOT NULL COMMENT 'The name of the cell.',"
                            + "`jail` VARCHAR(250) NOT NULL COMMENT 'The name of the jail the cell is in.',"
                            + "`tp.x` DOUBLE NOT NULL COMMENT 'The teleport in x coordinate.',"
                            + "`tp.y` DOUBLE NOT NULL COMMENT 'The teleport in y coordinate.',"
                            + "`tp.z` DOUBLE NOT NULL COMMENT 'The teleport in z coordinate.',"
                            + "`tp.yaw` DOUBLE NOT NULL COMMENT 'The teleport in yaw.',"
                            + "`tp.pitch` DOUBLE NOT NULL COMMENT 'The teleport in pitch.',"
                            + "`chest.x` INT NULL COMMENT 'The chest x coordinate.',"
                            + "`chest.y` INT NULL COMMENT 'The chest y coordinate.',"
                            + "`chest.z` INT NULL COMMENT 'The chest z coordinate.',"
                            + "`signs` VARCHAR(250) NULL COMMENT 'A string containing the signs.',"
                            + "PRIMARY KEY (`cellid`),"
                            + "UNIQUE INDEX `cellid_UNIQUE` (`cellid` ASC))"
                            + "COMMENT = 'Contains all the cells for the jails.';";

                    //pl.debug(cellCreateCmd);
                    st.executeUpdate(cellCreateCmd);

                    String prisCreateCmd = "CREATE TABLE IF NOT EXISTS `" + prefix + "prisoners` ("
                            + "`uuid` VARCHAR(36) NOT NULL COMMENT 'The UUID of the prisoner.',"
                            + "`name` VARCHAR(16) NOT NULL COMMENT 'The name of the prisoner.',"
                            + "`jail` VARCHAR(250) NOT NULL COMMENT 'The jail the prisoner is in.',"
                            + "`cell` VARCHAR(250) NULL COMMENT 'The cell the prisoner is in.',"
                            + "`muted` TINYINT NOT NULL COMMENT 'Whether the player is muted or not.',"
                            + "`time` BIGINT NOT NULL COMMENT 'The remaining time the prisoner has.',"
                            + "`offlinePending` TINYINT NOT NULL COMMENT 'Whether the prisoner has something happened to them while they were offline.',"
                            + "`toBeTransferred` TINYINT NOT NULL COMMENT 'Whether the prisoner is to be transferred.',"
                            + "`jailer` VARCHAR(250) NOT NULL COMMENT 'The name of the person who jailed them.',"
                            + "`reason` VARCHAR(250) NOT NULL COMMENT 'The reason they are jailed.',"
                            + "`inventory` LONGBLOB NULL COMMENT 'Their inventory in base64.',"
                            + "`armor` LONGBLOB NULL COMMENT 'The armor in base64.',"
                            + "`previousLocation` VARCHAR(250) NULL COMMENT 'A string of their previous location.',"
                            + "`previousGameMode` VARCHAR(16) NULL COMMENT 'Their previous gamemode before they were jailed.',"
                            + "PRIMARY KEY (`uuid`),"
                            + "UNIQUE INDEX `uuid_UNIQUE` (`uuid` ASC))"
                            + "COMMENT = 'Contains all the prisoners, in cells and jails.';";

                    //pl.debug(prisCreateCmd);
                    st.executeUpdate(prisCreateCmd);
                    // Old BLOB columns were limited to 64 KiB, truncating complex custom items.
                    boolean enlargeItems = false;
                    try(ResultSet columns = getConnection().getMetaData().getColumns(null, null, prefix + "prisoners", "%")) {
                        while(columns.next()) {
                            String column = columns.getString("COLUMN_NAME");
                            if((column.equalsIgnoreCase("inventory") || column.equalsIgnoreCase("armor"))
                                    && !columns.getString("TYPE_NAME").equalsIgnoreCase("LONGBLOB")) enlargeItems = true;
                        }
                    }
                    if(enlargeItems) st.executeUpdate("ALTER TABLE `" + prefix + "prisoners` MODIFY `inventory` LONGBLOB NULL, MODIFY `armor` LONGBLOB NULL");

                    String proCreateCmd = "CREATE TABLE IF NOT EXISTS `" + prefix + "records` ("
                            + "`recordid` INT NOT NULL AUTO_INCREMENT COMMENT 'Auto generated number for the records database.',"
                            + "`uuid` VARCHAR(36) NOT NULL COMMENT 'The UUID of the prisoner.',"
                            + "`username` VARCHAR(16) NOT NULL COMMENT 'The username of the prisoner.',"
                            + "`jailer` VARCHAR(250) NOT NULL COMMENT 'The name of the person who jailed the prisoner.',"
                            + "`date` VARCHAR(32) NOT NULL COMMENT 'A string of the date.',"
                            + "`time` BIGINT NOT NULL COMMENT 'The milliseconds they were jailed for.',"
                            + "`reason` VARCHAR(250) NOT NULL COMMENT 'The reason they were jailed for.',"
                            + "PRIMARY KEY (`recordid`),"
                            + "UNIQUE INDEX `recordid_UNIQUE` (`recordid` ASC))"
                            + "COMMENT = 'Holds a history of all the times prisoners have been jailed.'";

                    //pl.debug(proCreateCmd);
                    st.executeUpdate(proCreateCmd);
                    st.close();
                    break;
                default:
                    break;
            }
        } catch (SQLException e) {
            storageFailure(e);

            pl.getLogger().severe("Error while creating the tables, please check the error and fix what is wrong.");
            throw e;
        }
    }

    /**
     * Loads the jails, this should <strong>only</strong> be called after {@link #prepareStorage(boolean)}.
     */
    protected void loadJails() {
        switch(storage) {
            case 1:
            case 2:
                //load the jails from mysql and sqlite
                long st = System.currentTimeMillis();

                try {
                    PreparedStatement ps = getConnection().prepareStatement("SELECT * FROM " + prefix + "jails");
                    ResultSet set = ps.executeQuery();

                    while(set.next()) {
                        Jail j = new Jail(pl, set.getString("name"));

                        j.setWorld(set.getString("world"));
                        j.setMaxPoint(new int[] { set.getInt("top.x"), set.getInt("top.y"), set.getInt("top.z") });
                        j.setMinPoint(new int[] { set.getInt("bottom.x"), set.getInt("bottom.y"), set.getInt("bottom.z") });
                        j.setTeleportIn(new SimpleLocation(j.getWorldName(), set.getDouble("tps.in.x"),
                                set.getDouble("tps.in.y"), set.getDouble("tps.in.z"),
                                set.getFloat("tps.in.yaw"), set.getFloat("tps.in.pitch")));
                        j.setTeleportFree(new SimpleLocation(set.getString("tps.free.world"), set.getDouble("tps.free.x"),
                                set.getDouble("tps.free.y"), set.getDouble("tps.free.z"),
                                set.getFloat("tps.free.yaw"), set.getFloat("tps.free.pitch")));
                        j.setEnabled(j.getWorld() != null);
                        pl.getJailManager().addJail(j, false);
                    }

                    set.close();
                    ps.close();
                } catch (SQLException e) {
                    storageFailure(e);

                    pl.getLogger().severe("Error while loading the jails, please check the error and fix what is wrong.");
                }

                //This list contains an integer which refers to the cellid column in sql
                //this list only gets populated if there are cells which reference a jail
                //that doesn't exist anymore
                List<Integer> cellsToRemove = new LinkedList<Integer>();

                try {
                    PreparedStatement ps = getConnection().prepareStatement("SELECT * FROM " + prefix + "cells");
                    ResultSet set = ps.executeQuery();

                    while(set.next()) {
                        Jail j = pl.getJailManager().getJail(set.getString("jail"));

                        if(j != null) {
                            {
                                Cell c = new Cell(set.getInt("cellid"), set.getString("name"));
                                c.setTeleport(new SimpleLocation(j.getWorldName(),  set.getDouble("tp.x"), set.getDouble("tp.y"), set.getDouble("tp.z"),
                                        set.getFloat("tp.yaw"), set.getFloat("tp.pitch")));

                                if(set.getObject("chest.x") != null) c.setChestLocation(new SimpleLocation(j.getWorldName(), set.getInt("chest.x"), set.getInt("chest.y"), set.getInt("chest.z")));

                                String cSigns = set.getString("signs");
                                if(!cSigns.isEmpty()) {
                                    String[] signs = cSigns.split(";");
                                    for(String s : signs) {
                                        String[] co = s.split(",");
                                        c.addSign(new SimpleLocation(co[0], co[1], co[2], co[3]));
                                    }
                                }

                                //Since we're loading the data, the cell hasn't officially changed
                                c.setChanged(false);

                                //Try to add the cell to the jail
                                //if this method returns false then
                                //the cell already exists and needs
                                //to be removed from the database
                                if(!j.addCell(c, false)) {
                                    int id = set.getInt("cellid");
                                    cellsToRemove.add(id);
                                    pl.debug("The cell, " + c.getName() + " (" + id + "), is already in jail " + j.getName() + " so we're removing it.");
                                }
                            }
                        }else {
                            cellsToRemove.add(set.getInt("cellid"));
                        }
                    }

                    pl.debug("There are " + cellsToRemove.size() + " cells we need to remove due to being invalid.");

                    set.close();
                    ps.close();
                } catch (SQLException e) {
                    storageFailure(e);

                    pl.getLogger().severe("Error while loading all of the cells, please check the error and fix what is wrong.");
                }

                //Remove the invalid prisoners
                if(!cellsToRemove.isEmpty()) {
                    StringBuilder ids = new StringBuilder();
                    for(int c : cellsToRemove) {
                        if(ids.length() == 0) ids.append("'" + c + "'");
                        else ids.append("," + "'" + c + "'");
                    }

                    try {
                        PreparedStatement cds = getConnection().prepareStatement("delete from " + prefix + "cells where cellid in (" + ids.toString() + ");");

                        pl.debug("Deleting old cells: `delete from " + prefix + "cells where cellid in (" + ids.toString() + ");`");

                        int count = cds.executeUpdate();
                        pl.getLogger().info("Deleted " + count + " cells which were invalid, they either referenced a jail which are no longer valid or were duplicates.");
                        cds.close();
                    } catch (SQLException e) {
                        storageFailure(e);

                        pl.getLogger().severe("Error while deleting the old cells which were invalid (they either referenced a jail which are no longer valid or were duplicates), please check the error and fix what is wrong.");
                    }
                }

                //This list contains a string which refers to the name of the prisoner in sql
                //this list only gets populated if there are prisoners which reference a jail
                //that doesn't exist anymore
                List<String> prisonersToRemove = new LinkedList<String>();

                try {
                    PreparedStatement ps = getConnection().prepareStatement("SELECT * FROM " + prefix + "prisoners");
                    ResultSet set = ps.executeQuery();

                    while(set.next()) {
                        Jail j = pl.getJailManager().getJail(set.getString("jail"));

                        if(j != null) {
                            String cellname = set.getString("cell");
                            Cell c = j.getCell(cellname);

                            Prisoner p = new Prisoner(set.getString("uuid"), set.getString("name"), set.getBoolean("muted"), set.getLong("time"), set.getString("jailer"), set.getString("reason"));
                            p.setOfflinePending(set.getBoolean("offlinePending"));
                            p.setToBeTransferred(set.getBoolean("toBeTransferred"));
                            p.setInventory(set.getBytes("inventory") == null ? "" : new String(set.getBytes("inventory"), java.nio.charset.StandardCharsets.UTF_8));
                            p.setArmor(set.getBytes("armor") == null ? "" : new String(set.getBytes("armor"), java.nio.charset.StandardCharsets.UTF_8));
                            p.setPreviousPosition(set.getString("previousLocation"));
                            p.setPreviousGameMode(set.getString("previousGameMode"));
                            p.setChanged(false);//Since we just loaded the prisoner, we really don't need to save them.

                            if(cellname == null || cellname.isEmpty()) {
                                j.addPrisoner(p);
                            }else if(c != null) {
                                c.setPrisoner(p);
                                c.setChanged(false);
                            }else {
                                //the prisoner is assigned to a cell which doesn't exist, so just put them into the jail
                                j.addPrisoner(p);
                            }
                        } else {
                            //if the jail doesn't exist, do the same as the cells
                            prisonersToRemove.add(set.getString("name"));
                        }
                    }

                    set.close();
                    ps.close();

                    pl.debug("There are " + prisonersToRemove.size() + " prisoners we need to remove due to being invalid.");
                } catch (SQLException e) {
                    storageFailure(e);

                    pl.getLogger().severe("Error while loading all of the prisoners, please check the error and fix what is wrong.");
                }

                //Remove the invalid prisoners
                if(!prisonersToRemove.isEmpty()) {
                    String names = "";
                    for(String s : prisonersToRemove) {
                        if(names.isEmpty()) names = "'" + s + "'";
                        else names += "," + "'" + s + "'";
                    }

                    try {
                        PreparedStatement pds = getConnection().prepareStatement("delete from " + prefix + "prisoners where name in (" + names + ");");

                        pl.debug("Deleting old prisoners: 'delete from " + prefix + "prisoners where name in (" + names + ");'");

                        int count = pds.executeUpdate();
                        pl.getLogger().info("Deleted " + count + " old prisoners which referenced a jail no longer valid: " + names);
                        pds.close();
                    } catch (SQLException e) {
                        storageFailure(e);

                        pl.getLogger().severe("Error while deleting the old prisoners which don't have a valid jail, please check the error and fix what is wrong.");
                    }
                }

                pl.debug("Took " + (System.currentTimeMillis() - st) + " millis to load the jails and all the data.");
                break;
            default:
                //load the jails from flatfile
                if(flat.isConfigurationSection("jails")) {
                    Set<String> jails = flat.getConfigurationSection("jails").getKeys(false);
                    if(!jails.isEmpty()) {
                        for(String name : jails) {
                            loadJailFromFlatFile(name);
                        }
                    }
                }
                break;
        }

        int js = pl.getJailManager().getJails().size();
        pl.debug("Loaded " + js + (js == 1 ? " jail." : " jails."));

        int cs = pl.getJailManager().getAllCells().size();
        pl.debug("Loaded " + cs + (cs == 1 ? " cell." : " cells."));

        int ps = pl.getJailManager().getAllPrisoners().size();
        pl.debug("Loaded " + ps + (ps == 1 ? " prisoner." : " prisoners."));
    }

    private void loadJailFromFlatFile(String name) {
        String node = "jails." + name + ".";
        String cNode = node + "cells.";
        pl.debug("Loading the jail " + name + "; " + node + "; " + cNode);
        Jail j = new Jail(pl, name);

        if(flat.getString(node + "world") == null || flat.getString(node + "world").isEmpty()) {
            pl.getLogger().severe("Failed to load the jail, " + name + ", because the world is not set.");
            return;
        }

        j.setWorld(flat.getString(node + "world"));
        j.setMaxPoint(new int[] {flat.getInt(node + "top.x"), flat.getInt(node + "top.y"), flat.getInt(node + "top.z")});
        j.setMinPoint(new int[] {flat.getInt(node + "bottom.x"), flat.getInt(node + "bottom.y"), flat.getInt(node + "bottom.z")});

        j.setTeleportIn(new SimpleLocation(
                j.getWorldName(),
                flat.getDouble(node + "tps.in.x"),
                flat.getDouble(node + "tps.in.y"),
                flat.getDouble(node + "tps.in.z"),
                (float) flat.getDouble(node + "tps.in.yaw"),
                (float) flat.getDouble(node + "tps.in.pitch")));
        j.setTeleportFree(new SimpleLocation(
                flat.getString(node + "tps.free.world"),
                flat.getDouble(node + "tps.free.x"),
                flat.getDouble(node + "tps.free.y"),
                flat.getDouble(node + "tps.free.z"),
                (float) flat.getDouble(node + "tps.free.yaw"),
                (float) flat.getDouble(node + "tps.free.pitch")));

        if(flat.isConfigurationSection(node + "cells")) {
            Set<String> cells = flat.getConfigurationSection(node + "cells").getKeys(false);
            if(!cells.isEmpty()) {
                for(String cell : cells) {
                    Cell c = new Cell(cell);
                    String cellNode = cNode + cell + ".";

                    c.setTeleport(new SimpleLocation(j.getWorldName(),
                            flat.getDouble(cellNode + "tp.x"),
                            flat.getDouble(cellNode + "tp.y"),
                            flat.getDouble(cellNode + "tp.z"),
                            (float) flat.getDouble(cellNode + "tp.yaw"),
                            (float) flat.getDouble(cellNode + "tp.pitch")));
                    if(flat.contains(cellNode + "chest.x")) c.setChestLocation(new SimpleLocation(j.getWorldName(),
                            flat.getInt(cellNode + "chest.x"),
                            flat.getInt(cellNode + "chest.y"),
                            flat.getInt(cellNode + "chest.z")));

                    for(String sign : flat.getStringList(cellNode + "signs")) {
                        String[] arr = sign.split(",");
                        c.addSign(new SimpleLocation(arr[0],
                                Double.valueOf(arr[1]),
                                Double.valueOf(arr[2]),
                                Double.valueOf(arr[3]),
                                Float.valueOf(arr[4]),
                                Float.valueOf(arr[5])));
                    }

                    if(flat.contains(cellNode + "prisoner")) {
                        Prisoner p = new Prisoner(flat.getString(cellNode + "prisoner.uuid"),
                                flat.getString(cellNode + "prisoner.name"),
                                flat.getBoolean(cellNode + "prisoner.muted"),
                                flat.getLong(cellNode + "prisoner.time"),
                                flat.getString(cellNode + "prisoner.jailer"),
                                flat.getString(cellNode + "prisoner.reason"));
                        p.setOfflinePending(flat.getBoolean(cellNode + "prisoner.offlinePending"));
                        p.setToBeTransferred(flat.getBoolean(cellNode + "prisoner.toBeTransferred"));
                        p.setPreviousPosition(flat.getString(cellNode + "prisoner.previousLocation"));
                        p.setPreviousGameMode(flat.getString(cellNode + "prisoner.previousGameMode"));
                        p.setInventory(flat.getString(cellNode + "prisoner.inventory", ""));
                        p.setArmor(flat.getString(cellNode + "prisoner.armor", ""));
                        p.setChanged(false);//Since we just loaded the prisoner, we really don't need to save them.
                        c.setPrisoner(p);
                    }

                    c.setChanged(false);
                    j.addCell(c, false);
                }
            }
        }

        if(flat.isConfigurationSection(node + "prisoners")) {
            Set<String> prisoners = flat.getConfigurationSection(node + "prisoners").getKeys(false);
            if(!prisoners.isEmpty()) {
                for(String uuid : prisoners) {
                    String pNode = node + "prisoners." + uuid + ".";
                    Prisoner pris = new Prisoner(uuid,
                            flat.getString(pNode + "name"),
                            flat.getBoolean(pNode + "muted"),
                            flat.getLong(pNode + "time"),
                            flat.getString(pNode + "jailer"),
                            flat.getString(pNode + "reason"));
                    pris.setOfflinePending(flat.getBoolean(pNode + "offlinePending"));
                    pris.setToBeTransferred(flat.getBoolean(pNode + "toBeTransferred"));
                    pris.setPreviousPosition(flat.getString(pNode + "previousLocation"));
                    pris.setPreviousGameMode(flat.getString(pNode + "previousGameMode"));
                    pris.setInventory(flat.getString(pNode + "inventory", ""));
                    pris.setArmor(flat.getString(pNode + "armor", ""));
                    pris.setChanged(false);//Since we just loaded the prisoner, we really don't need to save them.
                    j.addPrisoner(pris);
                }
            }
        }

        j.setEnabled(j.getWorld() != null);
        pl.getJailManager().addJail(j, false);
        pl.debug("Loaded jail " + j.getName() + " with " + j.getAllPrisoners().size() + " prisoners and " + j.getCellCount() + " cells" + (j.isEnabled() ? "." : " but the jail is disabled as the world doesn't exist or isn't loaded."));
    }

    /** Saves everything about a jail, don't usually call this. */
    protected void saveEverything() {
        long st = System.currentTimeMillis();

        for(Jail j : pl.getJailManager().getJails()) {
            for(Prisoner prisoner : j.getAllPrisoners().values()) {
                prisoner.setChanged(true);
            }
            saveJail(j);

            //Only save the cells individually
            //when we are not using the flatfile storage
            if(storage != 0) {
                for(Cell c : j.getCells()) {
                    saveCell(j, c, true);
                }
            }
        }

        pl.debug("Saving everything took " + (System.currentTimeMillis() - st) + " millis.");
    }

    /**
     * Saves the provided {@link Jail jail} to the storage system we are using.
     *
     * @param j The jail to save.
     */
    protected void saveJail(Jail j) {
        if(j.getTeleportIn() != null && j.getTeleportFree() != null) {
            switch(storage) {
                case 1:
                case 2:
                    long st = System.currentTimeMillis();

                    try {
                        PreparedStatement ps = getConnection().prepareStatement("REPLACE INTO "
                                + prefix + "jails (`name`, `world`, `top.x`, `top.y`, `top.z`, `bottom.x`, `bottom.y`,"
                                + "`bottom.z`, `tps.in.x`, `tps.in.y`, `tps.in.z`, `tps.in.yaw`, `tps.in.pitch`,"
                                + "`tps.free.world`, `tps.free.x`, `tps.free.y`, `tps.free.z`, `tps.free.yaw`, `tps.free.pitch`)"
                                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");

                        ps.setString(1, j.getName());
                        ps.setString(2, j.getWorldName());
                        ps.setInt(3, j.getMaxPoint().getBlockX());
                        ps.setInt(4, j.getMaxPoint().getBlockY());
                        ps.setInt(5, j.getMaxPoint().getBlockZ());
                        ps.setInt(6, j.getMinPoint().getBlockX());
                        ps.setInt(7, j.getMinPoint().getBlockY());
                        ps.setInt(8, j.getMinPoint().getBlockZ());
                        ps.setDouble(9, j.getTeleportIn().getX());
                        ps.setDouble(10, j.getTeleportIn().getY());
                        ps.setDouble(11, j.getTeleportIn().getZ());
                        ps.setDouble(12, j.getTeleportIn().getYaw());
                        ps.setDouble(13, j.getTeleportIn().getPitch());
                        ps.setString(14, j.getTeleportFreeWorldName());
                        ps.setDouble(15, j.getTeleportFree().getX());
                        ps.setDouble(16, j.getTeleportFree().getY());
                        ps.setDouble(17, j.getTeleportFree().getZ());
                        ps.setDouble(18, j.getTeleportFree().getYaw());
                        ps.setDouble(19, j.getTeleportFree().getPitch());

                        ps.executeUpdate();
                        ps.close();
                    } catch (SQLException e) {
                        storageFailure(e);

                        pl.getLogger().severe("Error while saving the Jail '" + j.getName() + "' (not updating the prisoners), please check the error and fix what is wrong.");
                    }

                    try {
                        for(Cell c : j.getCells()) {
                            saveCell(j, c, false);

                            if(c.hasPrisoner() && c.getPrisoner().wasChanged()) {
                                Prisoner p = c.getPrisoner();
                                PreparedStatement pPS = getConnection().prepareStatement("REPLACE INTO `" + prefix + "prisoners` (`uuid`, `name`, `jail`, `cell`, `muted`, `time`,"
                                        + "`offlinePending`, `toBeTransferred`, `jailer`, `reason`, `inventory`, `armor`, `previousLocation`, `previousGameMode`)"
                                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                                pPS.setString(1, p.getUUID().toString());
                                pPS.setString(2, p.getLastKnownName());
                                pPS.setString(3, j.getName());
                                pPS.setString(4, c.getName());
                                pPS.setBoolean(5, p.isMuted());
                                pPS.setLong(6, p.getRemainingTime());
                                pPS.setBoolean(7, p.isOfflinePending());
                                pPS.setBoolean(8, p.isToBeTransferred());
                                pPS.setString(9, p.getJailer());
                                pPS.setString(10, p.getReason());
                                pPS.setBytes(11, p.getInventory().getBytes());
                                pPS.setBytes(12, p.getArmor().getBytes());
                                pPS.setString(13, p.getPreviousLocationString());
                                pPS.setString(14, p.getPreviousGameMode().toString());

                                pPS.executeUpdate();
                                pPS.close();

                                p.setChanged(false);//Since we just saved the prisoner
                            }
                        }
                    } catch (SQLException e) {
                        storageFailure(e);

                        pl.getLogger().severe("Error while saving the cells of the Jail '" + j.getName() + "', please check the error and fix what is wrong.");
                    }

                    try {
                        for(Prisoner p : j.getPrisonersNotInCells().values()) {
                            if(p.wasChanged()) {
                                PreparedStatement pPS = getConnection().prepareStatement("REPLACE INTO `" + prefix + "prisoners` (`uuid`, `name`, `jail`, `cell`, `muted`, `time`,"
                                        + "`offlinePending`, `toBeTransferred`, `jailer`, `reason`, `inventory`, `armor`, `previousLocation`, `previousGameMode`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                                pPS.setString(1, p.getUUID().toString());
                                pPS.setString(2, p.getLastKnownName());
                                pPS.setString(3, j.getName());
                                pPS.setString(4, "");
                                pPS.setBoolean(5, p.isMuted());
                                pPS.setLong(6, p.getRemainingTime());
                                pPS.setBoolean(7, p.isOfflinePending());
                                pPS.setBoolean(8, p.isToBeTransferred());
                                pPS.setString(9, p.getJailer());
                                pPS.setString(10, p.getReason());
                                pPS.setBytes(11, p.getInventory().getBytes());
                                pPS.setBytes(12, p.getArmor().getBytes());
                                pPS.setString(13, p.getPreviousLocationString());
                                pPS.setString(14, p.getPreviousGameMode().toString());

                                pPS.executeUpdate();
                                pPS.close();
                                p.setChanged(false);//Since we just saved the prisoner
                            }
                        }
                    } catch (SQLException e) {
                        storageFailure(e);

                        pl.getLogger().severe("Error while saving the prisoners of the Jail '" + j.getName() + "', please check the error and fix what is wrong.");
                    }

                    pl.debug("Took " + (System.currentTimeMillis() - st) + " millis to save the jail " + j.getName() + ".");
                    break;
                default:
                    if(flat != null) {
                        String node = "jails." + j.getName() + ".";

                        //Corners
                        flat.set(node + "world", j.getWorldName());
                        flat.set(node + "top.x", j.getMaxPoint().getBlockX());
                        flat.set(node + "top.y", j.getMaxPoint().getBlockY());
                        flat.set(node + "top.z", j.getMaxPoint().getBlockZ());
                        flat.set(node + "bottom.x", j.getMinPoint().getBlockX());
                        flat.set(node + "bottom.y", j.getMinPoint().getBlockY());
                        flat.set(node + "bottom.z", j.getMinPoint().getBlockZ());

                        //Tele in
                        flat.set(node + "tps.in.x", j.getTeleportIn().getX());
                        flat.set(node + "tps.in.y", j.getTeleportIn().getY());
                        flat.set(node + "tps.in.z", j.getTeleportIn().getZ());
                        flat.set(node + "tps.in.yaw", j.getTeleportIn().getYaw());
                        flat.set(node + "tps.in.pitch", j.getTeleportIn().getPitch());

                        //Tele out
                        flat.set(node + "tps.free.world", j.getTeleportFreeWorldName());
                        flat.set(node + "tps.free.x", j.getTeleportFree().getX());
                        flat.set(node + "tps.free.y", j.getTeleportFree().getY());
                        flat.set(node + "tps.free.z", j.getTeleportFree().getZ());
                        flat.set(node + "tps.free.yaw", j.getTeleportFree().getYaw());
                        flat.set(node + "tps.free.pitch", j.getTeleportFree().getPitch());

                        //Set all the cells to nothing, then we save each of them so no cells are left behind
                        flat.set(node + "cells", null);
                        for(Cell c : j.getCells()) {
                            String cNode = node + "cells." + c.getName() + ".";

                            if(c.getTeleport() != null) {
                                flat.set(cNode + "tp.x", c.getTeleport().getX());
                                flat.set(cNode + "tp.y", c.getTeleport().getY());
                                flat.set(cNode + "tp.z", c.getTeleport().getZ());
                                flat.set(cNode + "tp.yaw", c.getTeleport().getYaw());
                                flat.set(cNode + "tp.pitch", c.getTeleport().getPitch());
                            }

                            if(c.getChestLocation() != null) {
                                flat.set(cNode + "chest.x", c.getChestLocation().getBlockX());
                                flat.set(cNode + "chest.y", c.getChestLocation().getBlockY());
                                flat.set(cNode + "chest.z", c.getChestLocation().getBlockZ());
                            }

                            String[] signs = new String[c.getSigns().size()];
                            int count = 0;
                            for(SimpleLocation loc : c.getSigns()) {
                                signs[count] = loc.toString();
                                count++;
                            }

                            flat.set(cNode + "signs", signs);

                            if(c.getPrisoner() != null) {
                                Prisoner p = c.getPrisoner();
                                flat.set(cNode + "prisoner.uuid", p.getUUID().toString());
                                flat.set(cNode + "prisoner.name", p.getLastKnownName());
                                flat.set(cNode + "prisoner.muted", p.isMuted());
                                flat.set(cNode + "prisoner.time", p.getRemainingTime());
                                flat.set(cNode + "prisoner.offlinePending", p.isOfflinePending());
                                flat.set(cNode + "prisoner.toBeTransferred", p.isToBeTransferred());
                                flat.set(cNode + "prisoner.jailer", p.getJailer());
                                flat.set(cNode + "prisoner.reason", p.getReason());
                                flat.set(cNode + "prisoner.inventory", p.getInventory());
                                flat.set(cNode + "prisoner.armor", p.getArmor());
                                if(p.getPreviousLocationString() != null)
                                    flat.set(cNode + "prisoner.previousLocation", p.getPreviousLocationString());
                                if(p.getPreviousGameMode() != null)
                                    flat.set(cNode + "prisoner.previousGameMode", p.getPreviousGameMode().toString());
                            }

                            c.setChanged(false);
                        }

                        //Null all the prisoners out before we save them again, this way no prisoners are left behind
                        flat.set(node + "prisoners", null);
                        for(Prisoner p : j.getPrisonersNotInCells().values()) {
                            String pNode = node + "prisoners." + p.getUUID().toString() + ".";
                            flat.set(pNode + "name", p.getLastKnownName());
                            flat.set(pNode + "muted", p.isMuted());
                            flat.set(pNode + "time", p.getRemainingTime());
                            flat.set(pNode + "offlinePending", p.isOfflinePending());
                            flat.set(pNode + "toBeTransferred", p.isToBeTransferred());
                            flat.set(pNode + "jailer", p.getJailer());
                            flat.set(pNode + "reason", p.getReason());
                            flat.set(pNode + "inventory", p.getInventory());
                            flat.set(pNode + "armor", p.getArmor());
                            if(p.getPreviousLocationString() != null)
                                flat.set(pNode + "previousLocation", p.getPreviousLocationString());
                            if(p.getPreviousGameMode() != null)
                                flat.set(pNode + "previousGameMode", p.getPreviousGameMode().toString());
                        }

                        try {
                            saveYaml(flat, new File(pl.getDataFolder(), "data.yml"));
                        } catch (IOException e) {
                            storageFailure(e);
                            pl.getLogger().severe("Unable to save the Jail data: " + e.getMessage());
                        }
                    }else {
                        pl.getLogger().severe("Storage not enabled, could not save the jail " + j.getName());
                    }
                    break;
            }
        }
    }

    public void saveCell(Jail j, Cell c, boolean force) {
        // A creation/API placeholder has no serializable arrival point yet.
        if(c.getTeleport() == null) return;
        long beforeSave = failures;
        //if the cell hasn't changed, no need to save it again
        //unless they're forcing the save
        if(!c.hasChanged() && !force) return;

        switch(storage) {
            case 1:
            case 2:
                try {
                    Connection con = getConnection();
                    if (con == null) {
                        break;
                    }

                    pl.debug("Saving the cell " + c.getName());
                    boolean hasId = c.getDatabaseID() != -1;

                    PreparedStatement cPS = con.prepareStatement((hasId ? "REPLACE" : "INSERT")
                            + " INTO `" + prefix + "cells` (" + (hasId ? "`cellid`, " : "")
                            + "`name`, `jail`, `tp.x`, `tp.y`, `tp.z`, `tp.yaw`,"
                            + "`tp.pitch`, `chest.x`, `chest.y`, `chest.z`, `signs`) VALUES ("
                            + (hasId ? c.getDatabaseID() + "," : "") + "?,?,?,?,?,?,?,?,?,?,?)");

                    cPS.setString(1, c.getName());
                    cPS.setString(2, j.getName());
                    cPS.setDouble(3, c.getTeleport().getX());
                    cPS.setDouble(4, c.getTeleport().getY());
                    cPS.setDouble(5, c.getTeleport().getZ());
                    cPS.setDouble(6, c.getTeleport().getYaw());
                    cPS.setDouble(7, c.getTeleport().getPitch());

                    if(c.useChest()) {
                        cPS.setInt(8, c.getChestLocation().getBlockX());
                        cPS.setInt(9, c.getChestLocation().getBlockY());
                        cPS.setInt(10, c.getChestLocation().getBlockZ());
                    }else {
                        cPS.setNull(8, java.sql.Types.INTEGER);
                        cPS.setNull(9, java.sql.Types.INTEGER);
                        cPS.setNull(10, java.sql.Types.INTEGER);
                    }

                    cPS.setString(11, c.getSignString());

                    cPS.executeUpdate();
                    cPS.close();
                    if(!hasId) {
                        try(PreparedStatement generated = con.prepareStatement("SELECT cellid FROM `" + prefix + "cells` WHERE name=? AND jail=? ORDER BY cellid DESC LIMIT 1")) {
                            generated.setString(1, c.getName()); generated.setString(2, j.getName());
                            try(ResultSet result = generated.executeQuery()) { if(result.next()) c.setDatabaseID(result.getInt(1)); }
                        }
                    }

                    if(c.hasPrisoner()) {
                        Prisoner p = c.getPrisoner();
                        PreparedStatement pPS = con.prepareStatement("REPLACE INTO `" + prefix + "prisoners` (`uuid`, `name`, `jail`, `cell`, `muted`, `time`,"
                                + "`offlinePending`, `toBeTransferred`, `jailer`, `reason`, `inventory`, `armor`, `previousLocation`, `previousGameMode`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                        pPS.setString(1, p.getUUID().toString());
                        pPS.setString(2, p.getLastKnownName());
                        pPS.setString(3, j.getName());
                        pPS.setString(4, c.getName());
                        pPS.setBoolean(5, p.isMuted());
                        pPS.setLong(6, p.getRemainingTime());
                        pPS.setBoolean(7, p.isOfflinePending());
                        pPS.setBoolean(8, p.isToBeTransferred());
                        pPS.setString(9, p.getJailer());
                        pPS.setString(10, p.getReason());
                        pPS.setBytes(11, p.getInventory().getBytes());
                        pPS.setBytes(12, p.getArmor().getBytes());
                        pPS.setString(13, p.getPreviousLocationString());
                        pPS.setString(14, p.getPreviousGameMode().toString());

                        pPS.executeUpdate();
                        pPS.close();
                    }
                } catch (SQLException e) {
                    storageFailure(e);

                    pl.getLogger().severe("Error while saving the cell '" + c.getName() + "' of the Jail '" + j.getName() + "', please check the error and fix what is wrong.");
                }

                break;
            default:
                this.saveJail(j);
                break;
        }

        c.setChanged(failures != beforeSave);
    }

    /**
     * Removes the prisoner from the storage system.
     *
     * @param j the jail which the prisoner is in.
     * @param p the prisoner data
     */
    protected void removePrisoner(Jail j, Prisoner p) {
        this.removePrisoner(j, null, p);
    }

    /**
     * Removes the prisoner from the storage system.
     *
     * @param j the jail which the prisoner is in.
     * @param c the cell which the prisoner is in, null if none
     * @param p the prisoner data
     */
    protected void removePrisoner(Jail j, Cell c, Prisoner p) {
        removePrisoner(j, c, p, () -> { });
    }

    protected void removePrisoner(Jail j, Cell c, Prisoner p, Runnable restore) {
        if(storage > 0) {
            Connection connection = getConnection();
            try {
                connection.setAutoCommit(false);
                try(PreparedStatement delete = connection.prepareStatement("DELETE FROM `" + prefix + "prisoners` WHERE uuid=?")) {
                    delete.setString(1, p.getUUID().toString()); delete.executeUpdate();
                }
                restore.run();
                connection.commit();
            } catch(Exception failure) {
                try { connection.rollback(); } catch(SQLException rollback) { failure.addSuppressed(rollback); }
                storageFailure(failure);
                throw new IllegalStateException("Prisoner release could not be saved; property and prisoner retained", failure);
            } finally {
                try { connection.setAutoCommit(true); } catch(SQLException reset) { storageFailure(reset); }
            }
        } else {
            String previous = flat.saveToString();
            try {
                flat.set("jails." + j.getName() + (c == null ? ".prisoners." + p.getUUID() : ".cells." + c.getName() + ".prisoner"), null);
                saveYaml(flat, new File(pl.getDataFolder(), "data.yml"));
                restore.run();
            } catch(Exception failure) {
                try { flat.loadFromString(previous); saveYaml(flat, new File(pl.getDataFolder(), "data.yml")); }
                catch(Exception rollback) { failure.addSuppressed(rollback); }
                storageFailure(failure);
                throw new IllegalStateException("Prisoner release could not be saved; property and prisoner retained", failure);
            }
        }
    }

    /** Persist the remaining property before handing reclaimed items to the player. */
    public void saveCellOrPrisoner(Jail jail, Cell cell, Prisoner prisoner) {
        if(storage == 0) {
            long before = failures;
            saveJail(jail);
            if(failures != before) throw new IllegalStateException("Could not save prisoner property");
            return;
        }
        String columns = "`uuid`,`name`,`jail`,`cell`,`muted`,`time`,`offlinePending`,`toBeTransferred`,`jailer`,`reason`,`inventory`,`armor`,`previousLocation`,`previousGameMode`";
        try(PreparedStatement row = getConnection().prepareStatement("REPLACE INTO `" + prefix + "prisoners` (" + columns + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            row.setString(1, prisoner.getUUID().toString()); row.setString(2, prisoner.getLastKnownName());
            row.setString(3, jail.getName()); row.setString(4, cell == null ? "" : cell.getName());
            row.setBoolean(5, prisoner.isMuted()); row.setLong(6, prisoner.getRemainingTime());
            row.setBoolean(7, prisoner.isOfflinePending()); row.setBoolean(8, prisoner.isToBeTransferred());
            row.setString(9, prisoner.getJailer()); row.setString(10, prisoner.getReason());
            row.setBytes(11, prisoner.getInventory().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            row.setBytes(12, prisoner.getArmor().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            row.setString(13, prisoner.getPreviousLocationString()); row.setString(14, prisoner.getPreviousGameMode().name());
            row.executeUpdate(); prisoner.setChanged(false);
        } catch(SQLException failure) { storageFailure(failure); throw new IllegalStateException("Could not save prisoner property; items retained", failure); }
    }

    /**
     * Removes the provided cell from the jail.
     *
     * @param j instance of the jail the cell is in
     * @param c instance of the cell we are removing
     */
    public void removeCell(Jail j, Cell c) {
        //Clear the inventory before we delete it
        if(c.hasChest()) c.getChest().getInventory().clear();

        //transfer the prisoner if it has one
        if(c.hasPrisoner()) {
            pl.getLogger().warning("Removing of cell '" + c.getName() + "' from the jail '" + j.getName() + "' failed as it has a prisoner.");
            return;
        }

        switch(storage) {
            case 1:
                try {
                    Connection con = getConnection();
                    if (con == null) {
                        break;
                    }

                    PreparedStatement p = con.prepareStatement("delete from `" + prefix + "cells` where name = ? and jail = ?;");
                    p.setString(1, c.getName());
                    p.setString(2, j.getName());

                    p.executeUpdate();
                    p.close();
                } catch (SQLException e) {
                    storageFailure(e);

                    pl.getLogger().severe("Error while removing the cell '" + c.getName() + "' from the Jail '" + j.getName() + "', please check the error and fix what is wrong.");
                }
                break;
            case 2:
                try {
                    Connection con = getConnection();
                    if (con == null) {
                        break;
                    }

                    PreparedStatement p = con.prepareStatement("delete from `" + prefix + "cells` where name = ? and jail = ? limit 1;");
                    p.setString(1, c.getName());
                    p.setString(2, j.getName());

                    p.executeUpdate();
                    p.close();
                } catch (SQLException e) {
                    storageFailure(e);

                    pl.getLogger().severe("Error while removing the cell '" + c.getName() + "' from the Jail '" + j.getName() + "', please check the error and fix what is wrong.");
                }
                break;
            default:
                if(flat != null) {
                    flat.set("jails." + j.getName() + ".cells." + c.getName(), null);

                    try {
                        saveYaml(flat, new File(pl.getDataFolder(), "data.yml"));
                    } catch (IOException e) {
                        storageFailure(e);

                        pl.getLogger().severe("Removing of the cell '" + c.getName() + "' from the jail '" + j.getName() + "' errored out while on saving.");
                    }
                }
                break;
        }
    }

    /**
     * Removes a jail from the storage system.
     *
     * @param j the jail instance to remove.
     */
    protected void removeJail(Jail j) {
        String name = j.getName();

        switch(storage) {
            case 1:
            case 2:
                for(Cell c : j.getCells()) {
                    removeCell(j, c);
                }

                try {
                    Connection c = getConnection();
                    if (c == null) {
                        break;
                    }

                    PreparedStatement p = c.prepareStatement("delete from `" + prefix + "jails` where name = ?");
                    p.setString(1, name);

                    p.executeUpdate();
                    p.close();
                } catch (SQLException e) {
                    storageFailure(e);

                    pl.getLogger().severe("Error while removing the Jail '" + j.getName() + "', please check the error and fix what is wrong.");
                }
                break;
            default:
                flat.set("jails." + name, null);

                try {
                    saveYaml(flat, new File(pl.getDataFolder(), "data.yml"));
                } catch (IOException e) {
                    storageFailure(e);

                    pl.getLogger().severe("Removing of the jail '" + j.getName() + "' errored out while on saving.");
                }
                break;
        }
    }

    /**
     * Adds an entry to the database/file for the user, logging when they was jailed.
     *
     * @param uuid of the player
     * @param username of the player
     * @param jailer who jailed them
     * @param date string of when they are jailed
     * @param time of the player's sentence
     * @param reason the player is jailed
     */
    public void addRecordEntry(String uuid, String username, String jailer, String date, long time, String reason) {
        switch(storage) {
            case 1:
                break;
            case 2:
                try {
                    Connection c = getConnection();
                    if (c == null) {
                        break;
                    }

                    PreparedStatement p = c.prepareStatement("insert into `" + prefix + "records` (`uuid`, `username`, `jailer`, `date`, `time`,  `reason`) VALUES (?,?,?,?,?,?);");
                    p.setString(1, uuid);
                    p.setString(2, username);
                    p.setString(3, jailer);
                    p.setString(4, date);
                    p.setLong(5, time);
                    p.setString(6, reason);

                    p.executeUpdate();
                    p.close();
                } catch (SQLException e) {
                    storageFailure(e);

                    pl.getLogger().severe("Error while adding a record entry for '" + username + "', please check the error and fix what is wrong.");
                }
                break;
            default:
                if(records == null) records = loadYaml(new File(pl.getDataFolder(), "records.yml"));

                List<String> previous = records.getStringList(uuid);
                previous.add(Lang.RECORDENTRY.get(new String[] { date, username, jailer, String.valueOf(time), reason, uuid }));

                records.set(uuid, previous);

                try {
                    saveYaml(records, new File(pl.getDataFolder(), "records.yml"));
                } catch (IOException e) {
                    storageFailure(e);

                    pl.getLogger().severe("Saving the records.yml file failed while putting an entry in for '" + username + "'.");
                }
                break;
        }
    }

    /**
     * Gets all the record entries for the given player.
     *
     * @param username the of the prisoner's records to get.
     * @return A List of strings containing the record entries.
     * @deprecated This calls getOfflinePlayer which is a blocking call from Bukkit
     */
    public List<String> getRecordEntries(String username) {
        UUID uuid = pl.getServer().getOfflinePlayer(username).getUniqueId();
        List<String> entries = new ArrayList<String>();

        switch(storage) {
            case 1:
                break;
            case 2:
                try {
                    Connection c = getConnection();
                    if (c == null) {
                        break;
                    }

                    PreparedStatement ps = c.prepareStatement("SELECT * FROM " + prefix + "records where uuid = ?");
                    ps.setString(1, uuid.toString());
                    ResultSet set = ps.executeQuery();

                    while(set.next()) {
                        entries.add(Lang.RECORDENTRY.get(new String[] { set.getString("date"), set.getString("username"), set.getString("jailer"), String.valueOf(set.getLong("time")), set.getString("reason"), set.getString("uuid") }));
                    }

                    set.close();
                    ps.close();
                } catch (SQLException e) {
                    storageFailure(e);

                    pl.getLogger().severe("Error while getting all the record entries for '" + uuid + "', please check the error and fix what is wrong.");
                }
                break;
            default:
                if(records == null) records = loadYaml(new File(pl.getDataFolder(), "records.yml"));

                entries = records.getStringList(uuid.toString());
                break;
        }

        return entries;
    }
}
