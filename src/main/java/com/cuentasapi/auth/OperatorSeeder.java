package com.cuentasapi.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Creates the one operator from OPERATOR_EMAIL and OPERATOR_PASSWORD; there is no API for operators. */
@Component
public class OperatorSeeder implements ApplicationRunner {

    private final AuthService auth;
    private final UserRepository users;
    private final String email;
    private final String password;

    public OperatorSeeder(AuthService auth, UserRepository users,
                          @Value("${OPERATOR_EMAIL:}") String email,
                          @Value("${OPERATOR_PASSWORD:}") String password) {
        this.auth = auth;
        this.users = users;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!email.isBlank() && !password.isBlank()) {
            seed(email, password);
        }
    }

    /** Does nothing when a user with that email already exists. */
    public void seed(String operatorEmail, String operatorPassword) {
        if (users.findByEmail(operatorEmail.trim().toLowerCase(java.util.Locale.ROOT)).isEmpty()) {
            auth.create(operatorEmail, operatorPassword, Role.OPERADOR);
        }
    }
}
