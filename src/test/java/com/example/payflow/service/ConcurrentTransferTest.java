package com.example.payflow.service;

import com.example.payflow.IntegrationTestSupport;
import com.example.payflow.dto.TransferRequest;
import com.example.payflow.entity.TransactionStatus;
import com.example.payflow.exception.TransferFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fires many transfers at the same moment and checks the invariants that must hold no matter
 * how the threads interleave: money is never created or destroyed and no balance goes negative.
 * Without optimistic locking these tests fail with lost updates.
 */
class ConcurrentTransferTest extends IntegrationTestSupport {

    private static final int THREADS = 16;

    private enum Outcome { SUCCESS, CONFLICT, INSUFFICIENT_BALANCE }

    @Autowired
    private TransactionService transactionService;

    @Test
    void hundredConcurrentTransfersFromOneAccountConserveMoney() throws Exception {
        createUser("sender@okaxis", "1000.00");
        String[] receivers = {"r1@oksbi", "r2@oksbi", "r3@oksbi", "r4@oksbi"};
        for (String receiver : receivers) {
            createUser(receiver, "0.00");
        }

        List<TransferRequest> requests = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            requests.add(new TransferRequest("sender@okaxis", receivers[i % receivers.length], new BigDecimal("10.00"), null));
        }

        Map<Outcome, Integer> outcomes = runConcurrently(requests);
        int successes = outcomes.get(Outcome.SUCCESS);

        BigDecimal received = BigDecimal.ZERO;
        for (String receiver : receivers) {
            received = received.add(balanceOf(receiver));
        }
        BigDecimal moved = new BigDecimal("10.00").multiply(BigDecimal.valueOf(successes));

        assertThat(successes).isPositive();
        assertThat(outcomes.get(Outcome.INSUFFICIENT_BALANCE)).isZero();
        assertThat(balanceOf("sender@okaxis")).isEqualByComparingTo(new BigDecimal("1000.00").subtract(moved));
        assertThat(received).isEqualByComparingTo(moved);
        assertThat(transactionRepository.countByStatus(TransactionStatus.SUCCESS)).isEqualTo(successes);
        assertThat(transactionRepository.countByStatus(TransactionStatus.FAILED)).isEqualTo(100 - successes);
        assertThat(transactionRepository.countByStatus(TransactionStatus.PENDING)).isZero();
    }

    @Test
    void concurrentTransfersCannotOverdrawAnAccount() throws Exception {
        createUser("sender@okaxis", "100.00");
        createUser("receiver@oksbi", "0.00");

        // 50 requests of 10.00 against a balance that only covers 10 of them
        List<TransferRequest> requests = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            requests.add(new TransferRequest("sender@okaxis", "receiver@oksbi", new BigDecimal("10.00"), null));
        }

        Map<Outcome, Integer> outcomes = runConcurrently(requests);
        int successes = outcomes.get(Outcome.SUCCESS);

        assertThat(successes).isBetween(1, 10);
        assertThat(balanceOf("sender@okaxis")).isNotNegative()
                .isEqualByComparingTo(new BigDecimal("100.00").subtract(new BigDecimal("10.00").multiply(BigDecimal.valueOf(successes))));
        assertThat(balanceOf("receiver@oksbi")).isEqualByComparingTo(new BigDecimal("10.00").multiply(BigDecimal.valueOf(successes)));
    }

    @Test
    void opposingTransfersDoNotDeadlockOrLoseMoney() throws Exception {
        createUser("priya@okaxis", "1000.00");
        createUser("ravi@oksbi", "1000.00");

        List<TransferRequest> requests = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            requests.add(i % 2 == 0
                    ? new TransferRequest("priya@okaxis", "ravi@oksbi", new BigDecimal("7.00"), null)
                    : new TransferRequest("ravi@oksbi", "priya@okaxis", new BigDecimal("3.00"), null));
        }

        runConcurrently(requests);

        assertThat(balanceOf("priya@okaxis").add(balanceOf("ravi@oksbi"))).isEqualByComparingTo("2000.00");
        assertThat(balanceOf("priya@okaxis")).isNotNegative();
        assertThat(balanceOf("ravi@oksbi")).isNotNegative();
    }

    // Starts every request at the same instant; any exception other than the expected outcomes fails the test
    private Map<Outcome, Integer> runConcurrently(List<TransferRequest> requests) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (TransferRequest request : requests) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    try {
                        transactionService.sendMoney(request);
                        return Outcome.SUCCESS;
                    } catch (TransferFailedException e) {
                        return switch (e.getReason()) {
                            case CONCURRENT_UPDATE -> Outcome.CONFLICT;
                            case INSUFFICIENT_BALANCE -> Outcome.INSUFFICIENT_BALANCE;
                            default -> throw e;
                        };
                    }
                }));
            }
            startGate.countDown();

            Map<Outcome, Integer> outcomes = new EnumMap<>(Outcome.class);
            for (Outcome outcome : Outcome.values()) {
                outcomes.put(outcome, 0);
            }
            for (Future<Outcome> future : futures) {
                outcomes.merge(future.get(60, TimeUnit.SECONDS), 1, Integer::sum);
            }
            System.out.println("Concurrent transfer outcomes: " + outcomes);
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }
}
