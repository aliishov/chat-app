package com.raul.chat.repositories.auth;

import com.raul.chat.models.user.Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TokenRepository extends JpaRepository<Token, Long> {

    @Query(value = "SELECT * FROM tokens t WHERE t.token = :token", nativeQuery = true)
    Optional<Token> findByToken(@Param("token") String token);
}
