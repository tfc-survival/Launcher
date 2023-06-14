package launchserver.auth.limiter;

import launcher.helper.LogHelper;
import launcher.serialize.config.entry.BlockConfigEntry;
import launchserver.auth.MySQL8SourceConfig;
import launchserver.auth.MySQLSourceConfig;
import launchserver.auth.SQLSourceConfig;
import launchserver.helpers.ImmutableByteArray;
import launchserver.helpers.Pair;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class AuthLimiterHWIDConfig {

    private final SQLSourceConfig sourceConfig;

    private static final String createTable1 = "create table userhardware(nickname varchar(255), hwidId int)";
    private static final String createTable2 = "create table hardware(id int, hwid blob(64), banned bit)";

    private static final String getHardwareOfUser = "select hwid,banned from userhardware join hardware on hwidId=id and nickname=?";
    private static final String findHardware = "select * from hardware where hwid=?";
    private static final String addNewHardware = "insert into hardware(id,hwid,banned) values(DEFAULT,?,?)";
    private static final String addHardwareToUser = "insert into userhardware(nickname,hwidId) values(?,?)";
    private static final String banUser = "update hardware set banned=1 where id in (select hwidId from userhardware where nickname=?)";
    private static final String pardonUser = "update hardware set banned=0 where id in (select hwidId from userhardware where nickname=?)";
    private static final String checkBanStatus = "select * from userhardware join hardware on hwidId=id and banned=1 and nickname=?";

    public AuthLimiterHWIDConfig(BlockConfigEntry hwidDB) {
        sourceConfig = new MySQL8SourceConfig("hwidPool", hwidDB);
        LogHelper.info("HWID Limiter enabled");
        /*try {
            //sourceConfig.getConnection().prepareStatement(createTable1).execute();
            //sourceConfig.getConnection().prepareStatement(createTable2).execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }*/
    }

    public Map<ImmutableByteArray, Boolean> getHardware(String nickname) throws SQLException {
        try (Connection c = sourceConfig.getConnection(); PreparedStatement s = c.prepareStatement(getHardwareOfUser)) {
            s.setQueryTimeout(MySQLSourceConfig.TIMEOUT);

            s.setString(1, nickname);

            try (ResultSet set = s.executeQuery()) {
                Map<ImmutableByteArray, Boolean> r = new HashMap<>();
                while (set.next())
                    r.put(new ImmutableByteArray(set.getBytes("hwid")), set.getBoolean("banned"));
                return r;
            }
        }
    }

    public void banUser(String nickname) throws SQLException {
        try (Connection c = sourceConfig.getConnection(); PreparedStatement s = c.prepareStatement(banUser)) {
            s.setQueryTimeout(MySQLSourceConfig.TIMEOUT);

            s.setString(1, nickname);
            s.execute();
        }
    }

    public void pardonUser(String nickname) throws SQLException {
        try (Connection c = sourceConfig.getConnection(); PreparedStatement s = c.prepareStatement(pardonUser)) {
            s.setQueryTimeout(MySQLSourceConfig.TIMEOUT);

            s.setString(1, nickname);
            s.execute();
        }
    }

    public boolean isBanned(String nickname) throws SQLException {
        try (Connection c = sourceConfig.getConnection(); PreparedStatement s = c.prepareStatement(checkBanStatus)) {
            s.setQueryTimeout(MySQLSourceConfig.TIMEOUT);

            s.setString(1, nickname);
            try (ResultSet set = s.executeQuery()) {
                return set.next();
            }
        }
    }

    public Pair<Integer, Boolean> getOrRegisterHWID(byte[] hwid, boolean banned) throws SQLException {
        try (Connection c = sourceConfig.getConnection();
             PreparedStatement find = c.prepareStatement(findHardware);
             PreparedStatement add = c.prepareStatement(addNewHardware)) {
            find.setQueryTimeout(MySQLSourceConfig.TIMEOUT);
            add.setQueryTimeout(MySQLSourceConfig.TIMEOUT);

            find.setBytes(1, hwid);
            try (ResultSet findResult = find.executeQuery()) {
                if (findResult.next())
                    return Pair.of(findResult.getInt("id"), findResult.getBoolean("banned"));
                else {
                    add.setBytes(1, hwid);
                    add.setBoolean(2, banned);
                    add.executeUpdate();
                    try (ResultSet addResult = add.getGeneratedKeys()) {
                        return Pair.of(addResult.getInt("id"), banned);
                    }
                }
            }
        }

    }

    public void addHardwareToUser(String nickname, int id) throws SQLException {
        try (Connection c = sourceConfig.getConnection(); PreparedStatement userhardware = c.prepareStatement(addHardwareToUser)) {
            userhardware.setString(1, nickname);
            userhardware.setInt(2, id);
            userhardware.execute();
        }
    }
}
