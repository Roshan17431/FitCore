package com.fitcore.gym.security;

import com.fitcore.gym.entity.User;
import com.fitcore.gym.exception.ForbiddenException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Value("${jwt.allowed-issue-clock-skew-seconds:30}")
    private long allowedIssueClockSkewSeconds = 30;

    @jakarta.annotation.PostConstruct
    void validateClockPolicy() {
        if (allowedIssueClockSkewSeconds < 0 || allowedIssueClockSkewSeconds > 120)
            throw new IllegalStateException("JWT issue clock tolerance must be between 0 and 120 seconds");
    }

    private boolean validIssueTime(Claims claims) {
        Object issued = claims.get("iat");
        if (!(issued instanceof Integer || issued instanceof Long)) return false;
        Date issueTime = claims.getIssuedAt();
        Date expiration = claims.getExpiration();
        return issueTime != null && expiration != null
                && issueTime.getTime() >= 0
                && issueTime.before(expiration)
                && issueTime.getTime() <= System.currentTimeMillis() + allowedIssueClockSkewSeconds * 1000;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        if (!(userDetails instanceof User account) || account.getGymId() == null)
            throw new ForbiddenException("GYM_ASSIGNMENT_REQUIRED");
        return Jwts.builder()
                .setClaims(extraClaims)
                .claim("tokenVersion", 2)
                .claim("gymId", account.getGymId().toString())
                .claim("authzVersion", account.getAuthzVersion())
                .claim("credentialsVersion", credentialsVersion(userDetails))
                .setSubject(account.getId().toString())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        if (!(userDetails instanceof User account) || account.getGymId() == null) return false;
        final Claims claims = extractAllClaims(token);
        final String username = claims.getSubject();
        final String version = claims.get("credentialsVersion", String.class);
        return validIssueTime(claims)
                && userDetails.isEnabled()
                && userDetails.isAccountNonExpired()
                && userDetails.isAccountNonLocked()
                && userDetails.isCredentialsNonExpired()
                && account.getId().toString().equals(username)
                && Integer.valueOf(2).equals(claims.get("tokenVersion", Integer.class))
                && account.getGymId().toString().equals(claims.get("gymId", String.class))
                && (claims.get("authzVersion") instanceof Integer
                        || claims.get("authzVersion") instanceof Long)
                && ((Number) claims.get("authzVersion")).longValue() == account.getAuthzVersion()
                && claims.getExpiration() != null
                && claims.getExpiration().after(new Date())
                && version != null
                && MessageDigest.isEqual(
                        version.getBytes(StandardCharsets.UTF_8),
                        credentialsVersion(userDetails).getBytes(StandardCharsets.UTF_8));
    }

    private String credentialsVersion(UserDetails userDetails) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            // Do not expose the stored password hash in the JWT. A keyed version
            // invalidates old tokens when credentials change, including demo-account rotation.
            byte[] version =
                    mac.doFinal(
                            ("credentials-version:" + userDetails.getPassword())
                                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(version);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to derive credential version", exception);
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSignInKey() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
