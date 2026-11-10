package com.giftmarket.payment.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;

final class ReconciliationBatchTestSupport {
    static ReconciliationBatchExecutor executor() {
        var lock = mock(ReconciliationBatchLock.class);
        lenient().doAnswer(invocation -> { ((Runnable) invocation.getArgument(1)).run(); return null; })
                .when(lock).run(any(), any());
        return new ReconciliationBatchExecutor(lock);
    }
}
