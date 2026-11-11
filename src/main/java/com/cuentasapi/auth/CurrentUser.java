package com.cuentasapi.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** The caller, read from the JWT that Spring Security already validated. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return Long.parseLong(((Jwt) authentication.getPrincipal()).getSubject());
    }

    public static boolean isOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OPERADOR"));
    }
}
