package com.giftmarket.payment.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

class ReconciliationBatchExecutorTest {
    @ParameterizedTest
    @EnumSource(ReconciliationBatchExecutor.Kind.class)
    void unresolvedRowsLargerThanBatchCannotStarveLaterRowsAndAreRevisited(ReconciliationBatchExecutor.Kind kind) {
        var executor = ReconciliationBatchTestSupport.executor();
        var pending = new TreeSet<>(List.of(1L, 2L, 3L, 4L, 5L));
        var visited = new ArrayList<Long>();
        ReconciliationBatchExecutor.Candidates<Long> query = (after, through, limit) -> page(pending, after, through, limit);
        for (int tick = 0; tick < 4; tick++) executor.run(kind, 2, pending::last, query, Long::longValue, visited::add);
        assertThat(visited).containsExactly(1L, 2L, 3L, 4L, 5L, 1L, 2L);
        assertThat(pending).hasSize(5); // No success/failure is inferred from visiting a candidate.
    }

    @Test
    void continuousArrivalsDoNotExtendCurrentSweepOrPreventOldRowsFromBeingRetried() {
        var executor = ReconciliationBatchTestSupport.executor();
        var pending = new TreeSet<>(List.of(1L, 2L, 3L, 4L));
        var visited = new ArrayList<Long>();
        ReconciliationBatchExecutor.Candidates<Long> query = (after, through, limit) -> page(pending, after, through, limit);
        executor.run(ReconciliationBatchExecutor.Kind.APPROVAL, 2, pending::last, query, Long::longValue, visited::add);
        pending.add(5L); pending.add(6L);
        executor.run(ReconciliationBatchExecutor.Kind.APPROVAL, 2, pending::last, query, Long::longValue, visited::add);
        pending.add(7L);
        executor.run(ReconciliationBatchExecutor.Kind.APPROVAL, 2, pending::last, query, Long::longValue, visited::add);
        assertThat(visited).containsExactly(1L, 2L, 3L, 4L, 1L, 2L);
        executor.run(ReconciliationBatchExecutor.Kind.APPROVAL, 2, pending::last, query, Long::longValue, visited::add);
        executor.run(ReconciliationBatchExecutor.Kind.APPROVAL, 2, pending::last, query, Long::longValue, visited::add);
        assertThat(visited).contains(5L, 6L);
    }

    @Test
    void failuresAndTimeoutsAdvanceCursorAndDoNotStopRemainingCandidates() {
        var executor = ReconciliationBatchTestSupport.executor();
        var pending = new TreeSet<>(List.of(1L, 2L, 3L));
        var visited = new ArrayList<Long>();
        for (int tick = 0; tick < 2; tick++) executor.run(ReconciliationBatchExecutor.Kind.PARTIAL_CANCELLATION,
                2, pending::last, (after, through, limit) -> page(pending, after, through, limit), Long::longValue, id -> {
                    visited.add(id);
                    if (id == 1L) throw new com.giftmarket.payment.gateway.PaymentGatewayUncertainException("isolated timeout", null);
                });
        assertThat(visited).containsExactly(1L, 2L, 3L);
    }

    @Test
    void queryFailureDoesNotSkipCandidatesAndNeverUsesGrowingOffsets() {
        var executor = ReconciliationBatchTestSupport.executor();
        var first = new AtomicBoolean(true);
        var visited = new ArrayList<Long>();
        var pending = new TreeSet<>(List.of(3L, 90L));
        ReconciliationBatchExecutor.Candidates<Long> query = (after, through, limit) -> {
            assertThat(after).isZero();
            assertThat(limit.getOffset()).isZero();
            if (first.getAndSet(false)) throw new IllegalStateException("isolated DB outage");
            return page(pending, after, through, limit);
        };
        assertThatThrownBy(() -> executor.run(ReconciliationBatchExecutor.Kind.APPROVAL, 2, () -> 100L,
                query, Long::longValue, visited::add)).isInstanceOf(IllegalStateException.class);
        executor.run(ReconciliationBatchExecutor.Kind.APPROVAL, 2, () -> 100L, query, Long::longValue, visited::add);
        assertThat(visited).containsExactly(3L, 90L);
    }

    @Test
    void restartRescansSafelyAndStillReachesTailWithinOneSweep() {
        var pending = new TreeSet<>(List.of(1L, 2L, 3L, 4L, 5L));
        var visited = new ArrayList<Long>();
        ReconciliationBatchExecutor.Candidates<Long> query = (after, through, limit) -> page(pending, after, through, limit);
        ReconciliationBatchTestSupport.executor().run(ReconciliationBatchExecutor.Kind.APPROVAL, 2,
                pending::last, query, Long::longValue, visited::add);
        var restarted = ReconciliationBatchTestSupport.executor();
        for (int tick = 0; tick < 3; tick++) restarted.run(ReconciliationBatchExecutor.Kind.APPROVAL, 2,
                pending::last, query, Long::longValue, visited::add);
        assertThat(visited).containsExactly(1L, 2L, 1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void disappearingCandidatesAndEmptyEndOfRangeResetSweep() {
        var executor = ReconciliationBatchTestSupport.executor();
        var pending = new TreeSet<>(List.of(1L, 2L, 3L));
        var visited = new ArrayList<Long>();
        var maximum = new AtomicLong(10L);
        ReconciliationBatchExecutor.Candidates<Long> query = (after, through, limit) -> page(pending, after, through, limit);
        executor.run(ReconciliationBatchExecutor.Kind.FULL_CANCELLATION, 2, maximum::get, query, Long::longValue, visited::add);
        pending.remove(3L);
        executor.run(ReconciliationBatchExecutor.Kind.FULL_CANCELLATION, 2, maximum::get, query, Long::longValue, visited::add);
        pending.add(11L); maximum.set(11L);
        executor.run(ReconciliationBatchExecutor.Kind.FULL_CANCELLATION, 2, maximum::get, query, Long::longValue, visited::add);
        executor.run(ReconciliationBatchExecutor.Kind.FULL_CANCELLATION, 2, maximum::get, query, Long::longValue, visited::add);
        assertThat(visited).containsExactly(1L, 2L, 1L, 2L, 11L);
    }

    private List<Long> page(TreeSet<Long> pending, long after, long through, Pageable limit) {
        assertThat(limit.getOffset()).isZero();
        assertThat(limit.getPageSize()).isEqualTo(2);
        return pending.stream().filter(id -> id > after && id <= through).limit(limit.getPageSize()).toList();
    }
}
