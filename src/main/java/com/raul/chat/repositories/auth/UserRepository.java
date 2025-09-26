package com.raul.chat.repositories.auth;

import com.raul.chat.models.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @Query(value = "SELECT * FROM users u WHERE u.email = :email", nativeQuery = true)
    Optional<User> findByEmail(@Param("email") String email);

    @Query(value = "SELECT * FROM users u WHERE u.status = :status", nativeQuery = true)
    List<User> findAllByStatus(@Param("status") String status);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM users u WHERE u.id = :userId AND u.status = 'ONLINE')",
            nativeQuery = true)
    boolean isUserOnline(@Param("userId") UUID userId);
}
