package com.giftmarket.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** Session locks coordinate schedulers without holding a business transaction over PG I/O. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationBatchLock {
    private final DataSource dataSource;
    private final ConcurrentHashMap<ReconciliationBatchExecutor.Kind, ReentrantLock> localLocks = new ConcurrentHashMap<>();

    public void run(ReconciliationBatchExecutor.Kind kind, Runnable work) {
        var local = localLocks.computeIfAbsent(kind, ignored -> new ReentrantLock());
        if (!local.tryLock()) return;
        String name = "giftmarket:reconciliation:" + kind.name();
        try (Connection connection = dataSource.getConnection()) {
            if (!"MySQL".equals(connection.getMetaData().getDatabaseProductName())) {
                log.debug("Reconciliation batch requires MySQL session locks. kind={}", kind);
                return;
            }
            boolean acquired = false;
            try {
                try (var statement = connection.prepareStatement("select GET_LOCK(?, 0)")) {
                    statement.setString(1, name);
                    try (var result = statement.executeQuery()) {
                        acquired = result.next() && result.getInt(1) == 1 && !result.wasNull();
                    }
                }
                if (acquired) work.run();
            } catch (SQLException exception) {
                // The server may have acquired the lock before the response/close failed.
                abort(connection, kind);
                acquired = false;
                throw exception;
            } finally {
                if (acquired) release(connection, name, kind);
            }
        } catch (SQLException exception) {
            log.warn("Reconciliation batch lock unavailable. kind={}, exceptionType={}", kind, exception.getClass().getSimpleName());
        } finally {
            local.unlock();
        }
    }

    private void release(Connection connection, String name, ReconciliationBatchExecutor.Kind kind) {
        try (var statement = connection.prepareStatement("select RELEASE_LOCK(?)")) {
            statement.setString(1, name);
            try (var result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1 || result.wasNull()) {
                    throw new SQLException("Reconciliation session lock was not released");
                }
            }
        } catch (SQLException exception) {
            // Never return a session that may still own a named lock to the connection pool.
            abort(connection, kind);
            log.error("Reconciliation batch lock release failed. kind={}, exceptionType={}", kind, exception.getClass().getSimpleName());
        }
    }

    private void abort(Connection connection, ReconciliationBatchExecutor.Kind kind) {
        try { connection.abort(Runnable::run); }
        catch (SQLException exception) {
            log.error("Reconciliation connection abort failed. kind={}, exceptionType={}", kind, exception.getClass().getSimpleName());
        }
    }
}
