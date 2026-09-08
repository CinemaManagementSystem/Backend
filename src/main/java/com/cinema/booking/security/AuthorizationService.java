package com.cinema.booking.security;

import com.cinema.booking.entity.User;
import com.cinema.booking.enums.Role;
import com.cinema.booking.exception.ResourceNotFoundException;
import com.cinema.booking.repository.UserRepository;
import com.cinema.booking.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final UserRepository userRepository;

    public User getCurrentUser() {
        String principal = SecurityUtil.getCurrentUsername()
                .orElseThrow(() -> new AccessDeniedException("Authentication required"));

        return userRepository.findByUsernameOrEmail(principal, principal)
                .orElseThrow(() -> new AccessDeniedException("Authentication required"));
    }

    public boolean isStaffOrAdmin(User user) {
        return user != null && (user.getRole() == Role.STAFF || user.getRole() == Role.ADMIN);
    }

    public void requireStaffOrAdmin() {
        if (!isStaffOrAdmin(getCurrentUser())) {
            throw new AccessDeniedException("Staff or admin role is required");
        }
    }

    public void requireOwnerOrStaff(User owner) {
        User currentUser = getCurrentUser();
        if (isStaffOrAdmin(currentUser) || isSameUser(currentUser, owner)) {
            return;
        }
        throw new AccessDeniedException("Access denied");
    }

    public User resolveCustomerForAuthenticatedRequest(Long requestedCustomerId) {
        User currentUser = getCurrentUser();
        if (isStaffOrAdmin(currentUser)) {
            if (requestedCustomerId == null) {
                throw new IllegalArgumentException("Customer ID is required");
            }
            return userRepository.findById(requestedCustomerId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", requestedCustomerId));
        }

        if (requestedCustomerId != null && !requestedCustomerId.equals(currentUser.getId())) {
            throw new AccessDeniedException("Cannot create or update another customer's data");
        }
        return currentUser;
    }

    private boolean isSameUser(User currentUser, User owner) {
        return currentUser != null
                && owner != null
                && currentUser.getId() != null
                && currentUser.getId().equals(owner.getId());
    }
}
