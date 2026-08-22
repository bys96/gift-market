package com.giftmarket.order.service;

import com.giftmarket.order.repository.ReturnRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnCompletionRecoveryService {
    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnCompletionService completionService;

    @Scheduled(fixedDelayString = "#{@paymentProperties.partialCancellationReconciliationCheckIntervalMillis}")
    public void recover() {
        for (Long id : returnRequestRepository.findCompletionCandidateIds(PageRequest.of(0, 100))) {
            try { completionService.complete(id); }
            catch (RuntimeException exception) {
                log.error("Return completion recovery failed. returnRequestId={}, exceptionType={}",
                        id, exception.getClass().getSimpleName());
            }
        }
    }
}
