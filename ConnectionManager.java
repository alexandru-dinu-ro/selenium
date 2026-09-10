package com.db.neopam.infra.utils.jdbc;

import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
        System.setProperty("oracle.net.wallet_location",
                "(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=" + walletLocation + ")))");
        System.setProperty("oracle.net.ssl_server_dn_match", "true");

        // One-time single-threaded warm-up of the wallet/PKI subsystem,
        // using the plain driver (not UCP), before UCP touches anything.
        warmUpWallet(tnsAlias);

        try {
            pds = PoolDataSourceFactory.getPoolDataSource();
            pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
            pds.setURL("jdbc:oracle:thin:@" + tnsAlias);
            pds.setConnectionPoolName("PERF_TEST_POOL");

            // Keep startup pool size at 1 — UCP creates initialPoolSize
            // connections in PARALLEL internally, which races against the
            // wallet's Secret Store reader and intermittently fails.
            // We grow the pool ourselves afterward via preWarmPool().
            pds.setInitialPoolSize(1);
            pds.setMinPoolSize(1);
            pds.setMaxPoolSize(1100);
            pds.setConnectionWaitTimeout(60);

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

    /**
     * Grows the pool to targetSize by opening connections ONE AT A TIME,
     * sequentially, on the calling thread. Call this once, before any
     * concurrent load, to avoid racing UCP's/the wallet's Secret Store reader.
     */
    public void preWarmPool(int targetSize) {
        List<Connection> held = new ArrayList<>();
        try {
            for (int i = 0; i < targetSize; i++) {
                held.add(pds.getConnection());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Pre-warm failed at connection " + held.size(), e);
        } finally {
            for (Connection c : held) {
                try { c.close(); } catch (SQLException ignored) {}
            }
        }
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

    private void warmUpWallet(String tnsAlias) {
        try {
            oracle.jdbc.pool.OracleDataSource ds = new oracle.jdbc.pool.OracleDataSource();
            ds.setURL("jdbc:oracle:thin:@" + tnsAlias);
            try (Connection warmup = ds.getConnection()) {
                // no-op; forces PKI/wallet init once, single-threaded
            }
        } catch (SQLException e) {
            throw new RuntimeException("Wallet warm-up failed", e);
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
