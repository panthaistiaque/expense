package com.ihit.expense_tracker;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

final class DatabaseSearchAccess {

    private static final String ALLOWED_EMAIL = "panthaistiaque@gmail.com";

    private DatabaseSearchAccess() {
    }

    static boolean isAllowed(Authentication authentication) {
        return ALLOWED_EMAIL.equalsIgnoreCase(getEmail(authentication));
    }

    static String getEmail(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User user) {
            Object email = user.getAttribute("email");
            if (email != null) {
                return email.toString().trim();
            }
        }
        return "";
    }
}
