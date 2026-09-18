package com.example.payflow.service;

import com.example.payflow.IntegrationTestSupport;
import com.example.payflow.dto.TransferRequest;
import com.example.payflow.entity.User;
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
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(balanceOf("priya@okaxis")).isEqualByComparingTo("1000.00");
        assertThat(balanceOf("ravi@oksbi")).isEqualByComparingTo("99999999999999999.99");
        assertThat(transactionRepository.count()).isZero();
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
