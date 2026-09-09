package com.yourorg.framework.db;

import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public final class ConnectionManager {

    private static volatile ConnectionManager instance;

    private final PoolDataSource pds;

    private ConnectionManager() {
        Properties props = loadProperties();

        String tnsAlias = System.getProperty("db.tns.alias", props.getProperty("db.tns.alias"));
        String tnsAdmin = resolve(System.getenv("DB_TNS_ADMIN"), props.getProperty("db.tns.admin"));
        String walletLocation = resolve(System.getenv("DB_WALLET_LOCATION"), props.getProperty("db.wallet.location"));

        System.setProperty("oracle.net.tns_admin", tnsAdmin);
        System.setProperty("oracle.net.wallet_location", walletLocation);
        System.setProperty("oracle.net.ssl_server_dn_match", "true");

        try {
            pds = PoolDataSourceFactory.getPoolDataSource();
            pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
            pds.setURL("jdbc:oracle:thin:@" + tnsAlias);
            pds.setConnectionPoolName("PERF_TEST_POOL");

            pds.setInitialPoolSize(10);
            pds.setMinPoolSize(10);
            pds.setMaxPoolSize(1100);          // headroom above the 1000 target
            pds.setConnectionWaitTimeout(60);  // seconds a thread waits if pool is maxed

        } catch (SQLException e) {
            throw new RuntimeException("Failed to configure UCP pool", e);
        }
    }

    public static ConnectionManager getInstance() {
        if (instance == null) {
            synchronized (ConnectionManager.class) {
                if (instance == null) instance = new ConnectionManager();
            }
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return pds.getConnection();
    }

    public void printPoolStats() {
        try {
            System.out.printf("[UCP] borrowed=%d available=%d total=%d%n",
                    pds.getBorrowedConnectionsCount(),
                    pds.getAvailableConnectionsCount(),
                    pds.getBorrowedConnectionsCount() + pds.getAvailableConnectionsCount());
        } catch (SQLException e) {
            System.err.println("Could not read pool stats: " + e.getMessage());
        }
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = ConnectionManager.class.getClassLoader()
                .getResourceAsStream("db-config.properties")) {
            if (in == null) throw new IllegalStateException("db-config.properties not found");
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load db-config.properties", e);
        }
        return props;
    }

    private static String resolve(String envVal, String propVal) {
        return (envVal != null && !envVal.isBlank()) ? envVal : propVal;
    }
}
