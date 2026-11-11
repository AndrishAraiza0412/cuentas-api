package com.cuentasapi.auth;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public record RegisterRequest(@NotBlank @Email String email, @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record TokenResponse(String token) {
    }

    public record UserResponse(long id, String email, String role) {
    }

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a customer (role CLIENTE)")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = auth.register(request.email(), request.password());
        return new UserResponse(user.getId(), user.getEmail(), user.getRole().name());
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and get a 15-minute JWT")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return new TokenResponse(auth.login(request.email(), request.password()));
    }
}
