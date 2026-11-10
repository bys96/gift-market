package com.giftmarket.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.giftmarket.payment.service.ReconciliationBatchExecutor.Kind.APPROVAL;

class ReconciliationBatchLockTest {
    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final PreparedStatement acquire = mock(PreparedStatement.class);
    private final PreparedStatement release = mock(PreparedStatement.class);
    private final ResultSet acquired = mock(ResultSet.class);
    private final ResultSet released = mock(ResultSet.class);
    private final ReconciliationBatchLock lock = new ReconciliationBatchLock(dataSource);

    @BeforeEach
    void setUp() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        var metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(connection.prepareStatement("select GET_LOCK(?, 0)")).thenReturn(acquire);
        when(connection.prepareStatement("select RELEASE_LOCK(?)")).thenReturn(release);
        when(acquire.executeQuery()).thenReturn(acquired);
        when(release.executeQuery()).thenReturn(released);
        when(acquired.next()).thenReturn(true);
        when(acquired.getInt(1)).thenReturn(1);
        when(released.next()).thenReturn(true);
        when(released.getInt(1)).thenReturn(1);
    }

    @Test
    void busyLockSkipsWorkWithoutWaitingOrReleasingAnotherOwnersLock() throws Exception {
        when(acquired.getInt(1)).thenReturn(0);
        Runnable work = mock(Runnable.class);
        lock.run(APPROVAL, work);
        verifyNoInteractions(work);
        verify(release, never()).executeQuery();
        verify(connection).close();
    }

    @Test
    void workFailureStillReleasesSessionLockAndConnection() throws Exception {
        assertThatThrownBy(() -> lock.run(APPROVAL, () -> { throw new IllegalStateException("isolated error"); }))
                .isInstanceOf(IllegalStateException.class);
        verify(release).executeQuery();
        verify(connection).close();
        verify(connection, never()).abort(any());
    }

    @Test
    void uncertainAcquisitionDiscardsSessionInsteadOfReturningPossibleLockToPool() throws Exception {
        when(acquire.executeQuery()).thenThrow(new SQLException("isolated connection loss"));
        Runnable work = mock(Runnable.class);
        lock.run(APPROVAL, work);
        verifyNoInteractions(work);
        verify(connection).abort(any());
    }

    @Test
    void failureWhileClosingAcquisitionResultAlsoDiscardsSession() throws Exception {
        doThrow(new SQLException("isolated result close failure")).when(acquired).close();
        Runnable work = mock(Runnable.class);
        lock.run(APPROVAL, work);
        verifyNoInteractions(work);
        verify(connection).abort(any());
    }

    @Test
    void releaseFailureDiscardsSessionAndDoesNotRunWorkAgain() throws Exception {
        when(release.executeQuery()).thenThrow(new SQLException("isolated release failure"));
        Runnable work = mock(Runnable.class);
        lock.run(APPROVAL, work);
        verify(work, times(1)).run();
        verify(connection).abort(any());
    }
}
