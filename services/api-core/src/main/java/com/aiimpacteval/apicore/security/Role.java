package com.aiimpacteval.apicore.security;

/**
 * The five BRD roles (FR-8.8), matching the {@code core.app_user} check constraint. Mapped to
 * Spring authorities {@code ROLE_<name>} via the JWT {@code role} claim.
 */
public enum Role {
    ADMIN,
    ENG_LEADER,
    MANAGER,
    IC,
    FINANCE_READONLY;

    public static Role fromString(String value) {
        for (Role role : values()) {
            if (role.name().equalsIgnoreCase(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }

    public String authority() {
        return "ROLE_" + name();
    }
}
