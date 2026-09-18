package com.example.payflow.service;

import com.example.payflow.dto.TransferRequest;
import com.example.payflow.entity.FailureReason;
import com.example.payflow.entity.Transaction;
import com.example.payflow.exception.InvalidTransferException;
import com.example.payflow.exception.TransferFailedException;
import com.example.payflow.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final int MAX_ATTEMPTS = 3;
    private static final Long TRANSACTION_ID = 11L;

    @Mock
    private TransferProcessor transferProcessor;
    @Mock
    private TransactionRecorder transactionRecorder;
    @Mock
    private TransactionRepository transactionRepository;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(transferProcessor, transactionRecorder, transactionRepository, MAX_ATTEMPTS);
    }

    private Transaction stubPending() {
        Transaction pending = new Transaction("priya@okaxis", "ravi@oksbi", new BigDecimal("10.00"), null);
        ReflectionTestUtils.setField(pending, "transactionId", TRANSACTION_ID);
        when(transactionRecorder.createPending(anyString(), anyString(), any(), any())).thenReturn(pending);
        return pending;
    }

    private static TransferRequest request() {
        return new TransferRequest("priya@okaxis", "ravi@oksbi", BigDecimal.TEN, null);
    }

    @Test
    void recordsPendingTransferWithNormalisedValuesThenExecutesIt() {
        Transaction pending = stubPending();
        when(transferProcessor.execute(TRANSACTION_ID)).thenReturn(pending);

        Transaction result = transactionService.sendMoney(
                new TransferRequest(" Priya@OkAxis ", "RAVI@oksbi", new BigDecimal("10"), "tea"));

        assertThat(result).isSameAs(pending);
        verify(transactionRecorder).createPending("priya@okaxis", "ravi@oksbi", new BigDecimal("10.00"), "tea");
        verify(transactionRecorder, never()).markFailed(anyLong(), any());
    }

    @Test
    void rejectsTransferToOwnAccountWithoutRecordingIt() {
        assertThatThrownBy(() -> transactionService.sendMoney(
                new TransferRequest("priya@okaxis", "PRIYA@okaxis", BigDecimal.TEN, null)))
                .isInstanceOf(InvalidTransferException.class);

        verify(transactionRecorder, never()).createPending(anyString(), anyString(), any(), any());
        verify(transferProcessor, never()).execute(anyLong());
    }

    @Test
    void retriesAfterConcurrencyConflictsAndSucceeds() {
        Transaction pending = stubPending();
        when(transferProcessor.execute(TRANSACTION_ID))
                .thenThrow(new ObjectOptimisticLockingFailureException("User", 1L))
                .thenThrow(new CannotAcquireLockException("lock timeout"))
                .thenReturn(pending);

        Transaction result = transactionService.sendMoney(request());

        assertThat(result).isSameAs(pending);
        verify(transferProcessor, times(3)).execute(TRANSACTION_ID);
        verify(transactionRecorder, never()).markFailed(anyLong(), any());
    }

    @Test
    void givesUpAfterMaxAttemptsAndRecordsFailure() {
        stubPending();
        when(transferProcessor.execute(TRANSACTION_ID)).thenThrow(new ObjectOptimisticLockingFailureException("User", 1L));

        assertThatThrownBy(() -> transactionService.sendMoney(request()))
                .isInstanceOfSatisfying(TransferFailedException.class,
                        e -> assertThat(e.getReason()).isEqualTo(FailureReason.CONCURRENT_UPDATE));

        verify(transferProcessor, times(MAX_ATTEMPTS)).execute(TRANSACTION_ID);
        verify(transactionRecorder).markFailed(TRANSACTION_ID, FailureReason.CONCURRENT_UPDATE);
    }

    @Test
    void businessFailureIsRecordedAndNotRetried() {
        stubPending();
        when(transferProcessor.execute(TRANSACTION_ID)).thenThrow(TransferFailedException.insufficientBalance(TRANSACTION_ID));

        assertThatThrownBy(() -> transactionService.sendMoney(request()))
                .isInstanceOfSatisfying(TransferFailedException.class,
                        e -> assertThat(e.getReason()).isEqualTo(FailureReason.INSUFFICIENT_BALANCE));

        verify(transferProcessor, times(1)).execute(TRANSACTION_ID);
        verify(transactionRecorder).markFailed(TRANSACTION_ID, FailureReason.INSUFFICIENT_BALANCE);
    }

    @Test
    void unexpectedErrorIsRecordedAsSystemError() {
        stubPending();
        when(transferProcessor.execute(TRANSACTION_ID)).thenThrow(new DataIntegrityViolationException("boom"));

        assertThatThrownBy(() -> transactionService.sendMoney(request()))
                .isInstanceOfSatisfying(TransferFailedException.class,
                        e -> assertThat(e.getReason()).isEqualTo(FailureReason.SYSTEM_ERROR));

        verify(transactionRecorder).markFailed(TRANSACTION_ID, FailureReason.SYSTEM_ERROR);
    }
}
