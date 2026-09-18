package com.example.payflow.service;

import com.example.payflow.IntegrationTestSupport;
import com.example.payflow.dto.TransferRequest;
import com.example.payflow.entity.FailureReason;
import com.example.payflow.entity.TransactionStatus;
import com.example.payflow.entity.User;
import com.example.payflow.exception.TransferFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferAtomicityTest extends IntegrationTestSupport {

    @Autowired
    private TransactionService transactionService;

    @Test
    void failedCreditRollsBackTheDebit() {
        // The receiver is at the largest balance NUMERIC(19,2) can hold, so the database rejects the credit
        // after the sender's debit has already been issued. @Transactional must undo the debit.
        createUser("priya@okaxis", "1000.00");
        createUser("ravi@oksbi", "99999999999999999.99");

        assertThatThrownBy(() -> transactionService.sendMoney(
                new TransferRequest("priya@okaxis", "ravi@oksbi", new BigDecimal("100.00"), null)))
                .isInstanceOfSatisfying(TransferFailedException.class,
                        e -> assertThat(e.getReason()).isEqualTo(FailureReason.SYSTEM_ERROR));

        assertThat(balanceOf("priya@okaxis")).isEqualByComparingTo("1000.00");
        assertThat(balanceOf("ravi@oksbi")).isEqualByComparingTo("99999999999999999.99");
        assertThat(transactionRepository.countByStatus(TransactionStatus.SUCCESS)).isZero();
        assertThat(transactionRepository.countByStatus(TransactionStatus.FAILED)).isEqualTo(1);
    }

    @Test
    void databaseRejectsNegativeBalanceEvenIfApplicationCheckIsBypassed() {
        Long id = createUser("priya@okaxis", "10.00").getUserId();
        User user = userRepository.findById(id).orElseThrow();
        user.setBalance(new BigDecimal("-0.01"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(user))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(balanceOf("priya@okaxis")).isEqualByComparingTo("10.00");
    }

    @Test
    void staleUpdateIsRejectedByVersionCheck() {
        Long id = createUser("priya@okaxis", "1000.00").getUserId();

        // Two requests read the same row (version 0)
        User firstRead = userRepository.findById(id).orElseThrow();
        User secondRead = userRepository.findById(id).orElseThrow();

        secondRead.setBalance(new BigDecimal("900.00"));
        userRepository.save(secondRead); // version becomes 1

        firstRead.setBalance(new BigDecimal("500.00"));
        assertThatThrownBy(() -> userRepository.save(firstRead))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(balanceOf("priya@okaxis")).isEqualByComparingTo("900.00");
    }
}
