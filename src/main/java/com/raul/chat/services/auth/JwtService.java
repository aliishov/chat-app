package com.raul.chat.services.auth;

import com.raul.chat.models.user.TokenType;
import com.raul.chat.models.user.User;
import com.raul.chat.services.redis.JwtTokenTrackService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.Date;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JwtService {

    @Value("${jwt.expiration}")
    Long jwtExpiration;

    @Value("${jwt.refresh.expiration}")
    Long refreshJwtExpiration;

    @Value("${jwt.secret}")
    String secretKey;

    final JwtTokenTrackService jwtTokenTrackService;

    public String generateToken(User user) {
        return generateToken(new HashMap<>(), user);
    }

    private String generateToken(Map<String, Object> claims, User user) {

        var authorities = user.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        claims.put("userId", user.getId());
        claims.put("firstName", user.getFirstName());
        claims.put("lastName", user.getLastName());
        claims.put("role", authorities);
        claims.put("authenticated", user.getIsAuthenticated());
        claims.put("enabled", user.isEnabled());

        return buildToken(claims, user);
    }

    private String buildToken(Map<String, Object> extraClaims,
                              UserDetails userDetails) {
        UUID userId = (UUID) extraClaims.get("userId");

        revokeAllUserTokens(userId, TokenType.ACCESS_TOKEN);

        Date expirationDateTime = new Date(System.currentTimeMillis() + jwtExpiration);

        String token = Jwts
                .builder()
                .claims()
                .add(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(expirationDateTime)
                .and()
                .signWith(getSignInKey(), Jwts.SIG.HS256)
                .compact();

        saveToken(userId, token, TokenType.ACCESS_TOKEN);

        return token;
    }

    public String generateRefreshToken(User user) {

        revokeAllUserTokens(user.getId(), TokenType.REFRESH_TOKEN);

        Date expirationDateTime = new Date(System.currentTimeMillis() + refreshJwtExpiration);

        String refreshToken = Jwts.builder()
                .subject(user.getEmail())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(expirationDateTime)
                .signWith(getSignInKey())
                .compact();

        saveToken(user.getId(), refreshToken, TokenType.REFRESH_TOKEN);

        return refreshToken;
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private void saveToken(UUID userId, String token, TokenType tokenType) {
        jwtTokenTrackService.saveToken(userId, token, tokenType);
    }

    public void revokeAllUserTokens(UUID userId, TokenType tokenType) {
        jwtTokenTrackService.revokeAllTokens(userId, tokenType);
    }

    public Boolean isTokenValid(String token, UserDetails userDetails, TokenType tokenType) {
        String username = extractUsername(token);

        if (username == null || !username.equals(userDetails.getUsername())) {
            log.debug("Invalid token: username mismatch");
            return false;
        }

        UUID userId = extractUserId(token);
        return jwtTokenTrackService.isTokenValid(token, userId, tokenType);
    }

    public Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimResolver) {
        final Claims claims = extractAllClaims(token);
        return claimResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public UUID extractUserId(String token) {
        return extractClaim(token, claims -> UUID.fromString(claims.get("userId", String.class)));
    }
}
