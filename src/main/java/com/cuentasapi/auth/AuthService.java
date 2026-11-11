package com.cuentasapi.auth;

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

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public User register(String email, String password) {
        return create(email, password, Role.CLIENTE);
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

    @Transactional(readOnly = true)
    public String login(String email, String password) {
        User user = users.findByEmail(normalize(email))
                .filter(u -> encoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        return jwt.issue(user);
    }
}
