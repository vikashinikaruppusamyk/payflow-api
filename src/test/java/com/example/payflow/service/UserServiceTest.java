package com.example.payflow.service;

import com.example.payflow.dto.CreateUserRequest;
import com.example.payflow.entity.User;
import com.example.payflow.exception.DuplicateUpiIdException;
import com.example.payflow.exception.UserNotFoundException;
import com.example.payflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private UserService userService;

    @Test
    void registersUserWithNormalisedUpiIdAndScaledBalance() {
        when(userRepository.existsByUpiId("priya@okaxis")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.registerUser(new CreateUserRequest(" Priya ", "Priya@OkAxis", new BigDecimal("100"), "9876543210"));

        assertThat(user.getName()).isEqualTo("Priya");
        assertThat(user.getUpiId()).isEqualTo("priya@okaxis");
        assertThat(user.getBalance()).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void initialBalanceDefaultsToZero() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.registerUser(new CreateUserRequest("Ravi", "ravi@oksbi", null, null));

        assertThat(user.getBalance()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void rejectsDuplicateUpiId() {
        when(userRepository.existsByUpiId("priya@okaxis")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerUser(new CreateUserRequest("Priya", "PRIYA@okaxis", null, null)))
                .isInstanceOf(DuplicateUpiIdException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void unknownUserIdThrowsNotFound() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(42L)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void unknownUpiIdThrowsNotFound() {
        when(userRepository.findByUpiId("ghost@okaxis")).thenReturn(null);

        assertThatThrownBy(() -> userService.findByUpiId("Ghost@OkAxis")).isInstanceOf(UserNotFoundException.class);
    }
}
