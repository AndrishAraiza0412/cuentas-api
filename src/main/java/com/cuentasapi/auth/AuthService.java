package com.cuentasapi.auth;

import com.cuentasapi.audit.AuditService;
import com.cuentasapi.common.ApiException;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public User register(String email, String password) {
        User user = create(email, password, Role.CLIENTE);
        audit.record(user.getId(), "REGISTER", "user:" + user.getId(), "OK");
        return user;
    }

    /** Creates a user with the given role; a duplicate email (any letter case) is a 409. */
    @Transactional
    public User create(String email, String password, Role role) {
        String normalized = normalize(email);
        if (users.findByEmail(normalized).isPresent()) {
            throw ApiException.conflict("Email is already registered");
        }
        try {
            return users.saveAndFlush(new User(normalized, encoder.encode(password), role));
        } catch (DataIntegrityViolationException race) {
            throw ApiException.conflict("Email is already registered");
        }
    }

    /**
     * No outer transaction on purpose: a failed login is audited in its own transaction, and holding one connection
     * while asking the pool for a second would deadlock the pool under concurrent failures.
     */
    public String login(String email, String password) {
        String normalized = normalize(email);
        User user = users.findByEmail(normalized)
                .filter(u -> encoder.matches(password, u.getPasswordHash()))
                .orElse(null);
        if (user == null) {
            audit.recordIndependent(null, "LOGIN", normalized, "FAILED");
            throw ApiException.unauthorized("Invalid email or password");
        }
        audit.record(user.getId(), "LOGIN", "user:" + user.getId(), "OK");
        return jwt.issue(user);
    }
}
