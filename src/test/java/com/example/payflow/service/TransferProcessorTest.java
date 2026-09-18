package com.example.payflow.service;

import com.example.payflow.entity.Transaction;
import com.example.payflow.entity.User;
import com.example.payflow.exception.InsufficientBalanceException;
import com.example.payflow.exception.UserNotFoundException;
import com.example.payflow.repository.TransactionRepository;
import com.example.payflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferProcessorTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private TransferProcessor transferProcessor;

    private User priya;
    private User ravi;

    @BeforeEach
    void setUp() {
        priya = new User("Priya", "priya@okaxis", new BigDecimal("1000.00"), null);
        ravi = new User("Ravi", "ravi@oksbi", new BigDecimal("500.00"), null);
    }

    @Test
    void transferMovesMoneyAndRecordsTransaction() {
        when(userRepository.findByUpiId("priya@okaxis")).thenReturn(priya);
        when(userRepository.findByUpiId("ravi@oksbi")).thenReturn(ravi);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction result = transferProcessor.transfer("priya@okaxis", "ravi@oksbi", new BigDecimal("250.50"), "rent");

        assertThat(priya.getBalance()).isEqualByComparingTo("749.50");
        assertThat(ravi.getBalance()).isEqualByComparingTo("750.50");
        assertThat(result.getSenderUpiId()).isEqualTo("priya@okaxis");
        assertThat(result.getReceiverUpiId()).isEqualTo("ravi@oksbi");
        assertThat(result.getAmount()).isEqualByComparingTo("250.50");
        assertThat(result.getNote()).isEqualTo("rent");
        assertThat(result.getTimestamp()).isNotNull();
    }

    @Test
    void transferOfEntireBalanceIsAllowed() {
        when(userRepository.findByUpiId("priya@okaxis")).thenReturn(priya);
        when(userRepository.findByUpiId("ravi@oksbi")).thenReturn(ravi);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transferProcessor.transfer("priya@okaxis", "ravi@oksbi", new BigDecimal("1000.00"), null);

        assertThat(priya.getBalance()).isEqualByComparingTo("0.00");
        assertThat(ravi.getBalance()).isEqualByComparingTo("1500.00");
    }

    @Test
    void transferFailsWhenSenderDoesNotExist() {
        when(userRepository.findByUpiId("ghost@okaxis")).thenReturn(null);
        when(userRepository.findByUpiId("ravi@oksbi")).thenReturn(ravi);

        assertThatThrownBy(() -> transferProcessor.transfer("ghost@okaxis", "ravi@oksbi", BigDecimal.TEN, null))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("Sender");

        assertThat(ravi.getBalance()).isEqualByComparingTo("500.00");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transferFailsWhenReceiverDoesNotExist() {
        when(userRepository.findByUpiId("priya@okaxis")).thenReturn(priya);
        when(userRepository.findByUpiId("ghost@oksbi")).thenReturn(null);

        assertThatThrownBy(() -> transferProcessor.transfer("priya@okaxis", "ghost@oksbi", BigDecimal.TEN, null))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("Receiver");

        assertThat(priya.getBalance()).isEqualByComparingTo("1000.00");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transferFailsWhenBalanceIsInsufficient() {
        when(userRepository.findByUpiId("priya@okaxis")).thenReturn(priya);
        when(userRepository.findByUpiId("ravi@oksbi")).thenReturn(ravi);

        assertThatThrownBy(() -> transferProcessor.transfer("priya@okaxis", "ravi@oksbi", new BigDecimal("1000.01"), null))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(priya.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(ravi.getBalance()).isEqualByComparingTo("500.00");
        verify(transactionRepository, never()).save(any());
    }
}
