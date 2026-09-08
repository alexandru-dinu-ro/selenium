<dependencies>
    <!-- Oracle JDBC thin driver (19c-compatible, works forward with Java 25) -->
    <dependency>
        <groupId>com.oracle.database.jdbc</groupId>
        <artifactId>ojdbc11</artifactId>
        <version>23.5.0.24.07</version>
    </dependency>

    <!-- Required for wallet (TCPS/SSL) support with the thin driver -->
    <dependency>
        <groupId>com.oracle.database.security</groupId>
        <artifactId>oraclepki</artifactId>
        <version>23.5.0.24.07</version>
    </dependency>
    <dependency>
        <groupId>com.oracle.database.security</groupId>
        <artifactId>osdt_core</artifactId>
        <version>23.5.0.24.07</version>
    </dependency>
    <dependency>
        <groupId>com.oracle.database.security</groupId>
        <artifactId>osdt_cert</artifactId>
        <version>23.5.0.24.07</version>
    </dependency>

    <!-- TestNG -->
    <dependency>
        <groupId>org.testng</groupId>
        <artifactId>testng</artifactId>
        <version>7.10.2</version>
        <scope>test</scope>
    </dependency>
</dependencies>

---

# TNS alias to connect to
db.tns.alias=MYDB_1

# Folder containing tnsnames.ora (NOT the wallet subfolder)
db.tns.admin=C:/Users/myuser/AppData/Roaming/oracle

# Folder containing the wallet files (cwallet.sso etc.)
db.wallet.location=src/test/resources/downloads

---

package com.yourorg.framework.db;

import oracle.jdbc.pool.OracleDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public final class ConnectionManager {

    private static volatile ConnectionManager instance;

    private final String tnsAlias;
    private final String tnsAdmin;
    private final String walletLocation;

    private ConnectionManager() {
        Properties props = loadProperties();

        this.tnsAlias = System.getProperty("db.tns.alias",
                props.getProperty("db.tns.alias"));

        this.tnsAdmin = resolvePath(System.getenv("DB_TNS_ADMIN") != null
                ? System.getenv("DB_TNS_ADMIN")
                : props.getProperty("db.tns.admin"));

        this.walletLocation = resolvePath(System.getenv("DB_WALLET_LOCATION") != null
                ? System.getenv("DB_WALLET_LOCATION")
                : props.getProperty("db.wallet.location"));
    }

    public static ConnectionManager getInstance() {
        if (instance == null) {
            synchronized (ConnectionManager.class) {
                if (instance == null) {
                    instance = new ConnectionManager();
                }
            }
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        // Thin driver + wallet: no sqlnet.ora needed.
        System.setProperty("oracle.net.tns_admin", tnsAdmin);
        System.setProperty("oracle.net.wallet_location", walletLocation);
        System.setProperty("oracle.net.ssl_server_dn_match", "true");

        OracleDataSource ds = new OracleDataSource();
        ds.setURL("jdbc:oracle:thin:@" + tnsAlias + "?TNS_ADMIN=" + tnsAdmin);

        // Auto-login wallet (cwallet.sso) -> no username/password required
        return ds.getConnection();
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = ConnectionManager.class.getClassLoader()
                .getResourceAsStream("db-config.properties")) {
            if (in == null) {
                throw new IllegalStateException("db-config.properties not found on classpath");
            }
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load db-config.properties", e);
        }
        return props;
    }

    private static String resolvePath(String rawPath) {
        Path path = Paths.get(rawPath);
        return (path.isAbsolute() ? path : path.toAbsolutePath()).normalize().toString();
    }
}

---

package com.yourorg.framework.tests;

import com.yourorg.framework.db.ConnectionManager;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class DbSmokeTest {

    @Test
    public void canConnectAndQuery() throws Exception {
        try (Connection conn = ConnectionManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1 FROM DUAL")) {

            Assert.assertTrue(rs.next());
            Assert.assertEquals(rs.getInt(1), 1);
        }
    }
}
