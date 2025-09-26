package com.raul.chat.repositories.auth;

import com.raul.chat.models.user.JwtToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JwtTokenRepository extends JpaRepository<JwtToken, Long> {

    @Query(value = """
        SELECT * 
        FROM jwt_tokens t
        WHERE t.user_id = :userId
          AND t.expires_at > :now
          AND t.is_revoked = false
          AND t.token_type = :tokenType
    """, nativeQuery = true)
    List<JwtToken> findAllValidTokensByUser(@Param("userId") UUID userId,
                                            @Param("now") LocalDateTime now,
                                            @Param("tokenType") String tokenType);

    @Query(value = "SELECT * FROM jwt_tokens t WHERE t.token = :token", nativeQuery = true)
    Optional<JwtToken> findByToken(@Param("token") String token);

    @Query(value = "SELECT * FROM jwt_tokens t " +
            "WHERE t.token_type = :tokenType " +
            "AND t.user_id = :userId " +
            "AND t.is_revoked = :isRevoked", nativeQuery = true)
    Optional<JwtToken> findByTokenTypeAndUserIdAndIsRevoked(@Param("tokenType") String tokenType,
                                                            @Param("userId") UUID userId,
                                                            @Param("isRevoked") boolean isRevoked);
}
