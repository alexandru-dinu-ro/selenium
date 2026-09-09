package com.yourorg.framework.tests;

import com.yourorg.framework.db.ConnectionManager;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectorScaleTest {

    @Test
    public void connectorScalesTo1000PlusConnections() throws InterruptedException {
        int threadCount = 1000;

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);

        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(ConnectionManager.getInstance()::printPoolStats, 0, 1, TimeUnit.SECONDS);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < threadCount; i++) {
                final String label = "worker-" + i;
                executor.submit(() -> {
                    try {
                        doHarmlessQuery(label);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        System.err.println(label + " failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(3, TimeUnit.MINUTES);
        }

        monitor.shutdown();

        System.out.printf("Done. success=%d failure=%d%n", successCount.get(), failureCount.get());
    }

    private void doHarmlessQuery(String label) throws Exception {
        try (Connection conn = ConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM DUAL");
             ResultSet rs = ps.executeQuery()) {

            rs.next(); // just proves the round trip worked
        }
    }
}
