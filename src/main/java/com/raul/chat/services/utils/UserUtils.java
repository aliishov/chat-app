package com.raul.chat.services.utils;

import com.raul.chat.dtos.auth.RegisterRequestDto;
import com.raul.chat.dtos.auth.UserDto;
import com.raul.chat.models.user.Role;
import com.raul.chat.models.user.Status;
import com.raul.chat.models.user.User;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserUtils {

    PasswordEncoder passwordEncoder;

    public User convertToNewUser(RegisterRequestDto request) {
        return User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.ROLE_USER)
                .status(Status.OFFLINE)
                .imageUrl(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deletedAt(null)
                .isAuthenticated(false)
                .isEnabled(true)
                .isAccountNonExpired(true)
                .isCredentialsNonExpired(true)
                .isAccountNonLocked(true)
                .build();
    }

    public UserDto convertToUserDto(User user) {
        return new UserDto(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                user.getImageUrl(),
                user.getStatus()
        );
    }

    public UsernameNotFoundException throwUserNotFoundException(String marker, String identifier) {
        log.warn("User with {}: {} not found", marker, identifier);
        return new UsernameNotFoundException("User not found");
    }
}
