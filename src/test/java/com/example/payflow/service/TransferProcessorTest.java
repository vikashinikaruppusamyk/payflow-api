package com.example.payflow.service;

import com.example.payflow.entity.FailureReason;
import com.example.payflow.entity.Transaction;
import com.example.payflow.entity.TransactionStatus;
import com.example.payflow.entity.User;
import com.example.payflow.exception.TransferFailedException;
import com.example.payflow.repository.TransactionRepository;
import com.example.payflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferProcessorTest {

    private static final Long TRANSACTION_ID = 7L;

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

    private Transaction pendingTransfer(String sender, String receiver, String amount) {
        Transaction transaction = new Transaction(sender, receiver, new BigDecimal(amount), "rent");
        when(transactionRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(transaction));
        return transaction;
    }

    @Test
    void transferMovesMoneyAndMarksTransactionSuccessful() {
        Transaction transaction = pendingTransfer("priya@okaxis", "ravi@oksbi", "250.50");
        when(userRepository.findByUpiId("priya@okaxis")).thenReturn(priya);
        when(userRepository.findByUpiId("ravi@oksbi")).thenReturn(ravi);

        Transaction result = transferProcessor.execute(TRANSACTION_ID);

        assertThat(result).isSameAs(transaction);
        assertThat(priya.getBalance()).isEqualByComparingTo("749.50");
        assertThat(ravi.getBalance()).isEqualByComparingTo("750.50");
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(result.getFailureReason()).isNull();
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    void transferOfEntireBalanceIsAllowed() {
        pendingTransfer("priya@okaxis", "ravi@oksbi", "1000.00");
        when(userRepository.findByUpiId("priya@okaxis")).thenReturn(priya);
        when(userRepository.findByUpiId("ravi@oksbi")).thenReturn(ravi);

        transferProcessor.execute(TRANSACTION_ID);

        assertThat(priya.getBalance()).isEqualByComparingTo("0.00");
        assertThat(ravi.getBalance()).isEqualByComparingTo("1500.00");
    }

    @Test
    void transferFailsWhenSenderDoesNotExist() {
        Transaction transaction = pendingTransfer("ghost@okaxis", "ravi@oksbi", "10.00");
        when(userRepository.findByUpiId("ghost@okaxis")).thenReturn(null);
        when(userRepository.findByUpiId("ravi@oksbi")).thenReturn(ravi);

        assertThatThrownBy(() -> transferProcessor.execute(TRANSACTION_ID))
                .isInstanceOfSatisfying(TransferFailedException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(FailureReason.SENDER_NOT_FOUND);
                    assertThat(e.getTransactionId()).isEqualTo(TRANSACTION_ID);
                });

        assertThat(ravi.getBalance()).isEqualByComparingTo("500.00");
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    void transferFailsWhenReceiverDoesNotExist() {
        pendingTransfer("priya@okaxis", "ghost@oksbi", "10.00");
        when(userRepository.findByUpiId("priya@okaxis")).thenReturn(priya);
        when(userRepository.findByUpiId("ghost@oksbi")).thenReturn(null);

        assertThatThrownBy(() -> transferProcessor.execute(TRANSACTION_ID))
                .isInstanceOfSatisfying(TransferFailedException.class,
                        e -> assertThat(e.getReason()).isEqualTo(FailureReason.RECEIVER_NOT_FOUND));

        assertThat(priya.getBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void transferFailsWhenBalanceIsInsufficient() {
        pendingTransfer("priya@okaxis", "ravi@oksbi", "1000.01");
        when(userRepository.findByUpiId("priya@okaxis")).thenReturn(priya);
        when(userRepository.findByUpiId("ravi@oksbi")).thenReturn(ravi);

        assertThatThrownBy(() -> transferProcessor.execute(TRANSACTION_ID))
                .isInstanceOfSatisfying(TransferFailedException.class,
                        e -> assertThat(e.getReason()).isEqualTo(FailureReason.INSUFFICIENT_BALANCE));

        assertThat(priya.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(ravi.getBalance()).isEqualByComparingTo("500.00");
    }
}
