package com.example.payflow.security;

import com.example.payflow.entity.Transaction;
import com.example.payflow.exception.ForbiddenOperationException;
import com.example.payflow.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Account-ownership rules that URL patterns cannot express: a user may act only on their own account,
 * while an admin may view any account.
 */
@Component
public class AccessGuard {

    public AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            // Unreachable for endpoints that require authentication; guards against misconfiguration
            throw new IllegalStateException("No authenticated user in the security context");
        }
        return user;
    }

    public void requireSelfOrAdmin(String upiId) {
        AuthenticatedUser user = currentUser();
        if (!user.isAdmin() && !user.upiId().equals(UserService.normalizeUpiId(upiId))) {
            throw new ForbiddenOperationException("You can only access your own account");
        }
    }

    public void requireSelfOrAdmin(Long userId) {
        AuthenticatedUser user = currentUser();
        if (!user.isAdmin() && !Objects.equals(user.userId(), userId)) {
            throw new ForbiddenOperationException("You can only access your own account");
        }
    }

    // Money can only leave the caller's own account; the sender in the request body is never trusted on its own
    public void requireSender(String senderUpiId) {
        if (!currentUser().upiId().equals(UserService.normalizeUpiId(senderUpiId))) {
            throw new ForbiddenOperationException("You can only send money from your own account");
        }
    }

    public boolean canView(Transaction transaction) {
        AuthenticatedUser user = currentUser();
        return user.isAdmin()
                || user.upiId().equals(transaction.getSenderUpiId())
                || user.upiId().equals(transaction.getReceiverUpiId());
    }
}
