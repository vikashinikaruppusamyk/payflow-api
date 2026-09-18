package com.example.payflow.service;

import com.example.payflow.dto.TransferRequest;
import com.example.payflow.entity.Transaction;
import com.example.payflow.exception.ConcurrentTransferException;
import com.example.payflow.exception.InsufficientBalanceException;
import com.example.payflow.exception.InvalidTransferException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private TransferProcessor transferProcessor;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(transferProcessor, MAX_ATTEMPTS);
    }

    @Test
    void normalisesUpiIdsAndAmountBeforeTransferring() {
        Transaction saved = new Transaction("priya@okaxis", "ravi@oksbi", new BigDecimal("10.00"), "tea");
        when(transferProcessor.transfer("priya@okaxis", "ravi@oksbi", new BigDecimal("10.00"), "tea")).thenReturn(saved);

        Transaction result = transactionService.sendMoney(
                new TransferRequest(" Priya@OkAxis ", "RAVI@oksbi", new BigDecimal("10"), "tea"));

        assertThat(result).isSameAs(saved);
    }

    @Test
    void rejectsTransferToOwnAccount() {
        assertThatThrownBy(() -> transactionService.sendMoney(
                new TransferRequest("priya@okaxis", "PRIYA@okaxis", BigDecimal.TEN, null)))
                .isInstanceOf(InvalidTransferException.class);

        verify(transferProcessor, never()).transfer(anyString(), anyString(), any(), any());
    }

    @Test
    void retriesAfterOptimisticLockConflictAndSucceeds() {
        Transaction saved = new Transaction("priya@okaxis", "ravi@oksbi", BigDecimal.TEN, null);
        when(transferProcessor.transfer(anyString(), anyString(), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException("User", 1L))
                .thenThrow(new CannotAcquireLockException("lock timeout"))
                .thenReturn(saved);

        Transaction result = transactionService.sendMoney(
                new TransferRequest("priya@okaxis", "ravi@oksbi", BigDecimal.TEN, null));

        assertThat(result).isSameAs(saved);
        verify(transferProcessor, times(3)).transfer(anyString(), anyString(), any(), any());
    }

    @Test
    void givesUpAfterMaxAttempts() {
        when(transferProcessor.transfer(anyString(), anyString(), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException("User", 1L));

        assertThatThrownBy(() -> transactionService.sendMoney(
                new TransferRequest("priya@okaxis", "ravi@oksbi", BigDecimal.TEN, null)))
                .isInstanceOf(ConcurrentTransferException.class);

        verify(transferProcessor, times(MAX_ATTEMPTS)).transfer(anyString(), anyString(), any(), any());
    }

    @Test
    void doesNotRetryBusinessFailures() {
        when(transferProcessor.transfer(anyString(), anyString(), any(), any()))
                .thenThrow(new InsufficientBalanceException());

        assertThatThrownBy(() -> transactionService.sendMoney(
                new TransferRequest("priya@okaxis", "ravi@oksbi", BigDecimal.TEN, null)))
                .isInstanceOf(InsufficientBalanceException.class);

        verify(transferProcessor, times(1)).transfer(anyString(), anyString(), any(), any());
    }
}
