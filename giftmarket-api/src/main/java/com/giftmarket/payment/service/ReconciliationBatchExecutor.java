package com.giftmarket.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationBatchExecutor {
    public enum Kind { APPROVAL, FULL_CANCELLATION, PARTIAL_CANCELLATION, RETURN_REFUND, EXCHANGE_SHIPPING }

    private final ReconciliationBatchLock lock;
    private final EnumMap<Kind, Cursor> cursors = new EnumMap<>(Kind.class);

    @FunctionalInterface
    public interface Candidates<T> {
        List<T> find(long afterId, long throughId, Pageable limit);
    }

    public <T> void run(Kind kind, int batchSize, LongSupplier maximumId,
            Candidates<T> query, ToLongFunction<T> candidateId, Consumer<T> reconcile) {
        if (batchSize <= 0) throw new IllegalArgumentException("복구 배치 크기는 양수여야 합니다.");
        lock.run(kind, () -> {
            Cursor cursor;
            synchronized (cursors) { cursor = cursors.computeIfAbsent(kind, ignored -> new Cursor()); }
            if (cursor.throughId == 0L) cursor.throughId = maximumId.getAsLong();
            if (cursor.throughId == 0L) return;
            List<T> values = query.find(cursor.afterId, cursor.throughId, PageRequest.of(0, batchSize));
            if (values.size() > batchSize) throw new IllegalStateException("복구 조회가 배치 크기를 초과했습니다.");
            for (T value : values) {
                long id = candidateId.applyAsLong(value);
                if (id <= cursor.afterId || id > cursor.throughId) {
                    throw new IllegalStateException("복구 대상 ID 순서 또는 조회 범위가 올바르지 않습니다.");
                }
                // Failed and unresolved attempts also advance the scan, not the business state.
                cursor.afterId = id;
                try { reconcile.accept(value); }
                catch (RuntimeException exception) {
                    log.error("Reconciliation batch candidate failed. kind={}, candidateId={}, exceptionType={}",
                            kind, id, exception.getClass().getSimpleName());
                }
            }
            log.info("Reconciliation batch scanned. kind={}, afterId={}, throughId={}, count={}",
                    kind, cursor.afterId, cursor.throughId, values.size());
            if (values.size() < batchSize || cursor.afterId == cursor.throughId) {
                cursor.afterId = 0L;
                cursor.throughId = 0L;
            }
        });
    }

    private static class Cursor {
        private long afterId;
        private long throughId;
    }
}
