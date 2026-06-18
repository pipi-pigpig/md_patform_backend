package com.mdplatform.management.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class PasswordResetService {

    private static final long TOKEN_EXPIRY_MINUTES = 30;
    private final Map<String, ResetEntry> tokenStore = new ConcurrentHashMap<>();

    public String createResetToken(String email) {
        String token = generateRandomToken();
        tokenStore.put(token, new ResetEntry(email, LocalDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES)));
        log.info("Password reset token created for email: {}", email);
        return token;
    }

    public String validateToken(String token) {
        ResetEntry entry = tokenStore.get(token);
        if (entry == null) {
            return null;
        }
        if (LocalDateTime.now().isAfter(entry.expiryTime)) {
            tokenStore.remove(token);
            return null;
        }
        return entry.email;
    }

    public void consumeToken(String token) {
        tokenStore.remove(token);
    }

    private String generateRandomToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static class ResetEntry {
        final String email;
        final LocalDateTime expiryTime;

        ResetEntry(String email, LocalDateTime expiryTime) {
            this.email = email;
            this.expiryTime = expiryTime;
        }
    }
}
