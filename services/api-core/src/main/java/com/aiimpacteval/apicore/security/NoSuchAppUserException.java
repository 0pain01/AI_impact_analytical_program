package com.aiimpacteval.apicore.security;

/** No active {@code core.app_user} row for the requested email (and the system isn't empty). */
public class NoSuchAppUserException extends RuntimeException {

    public NoSuchAppUserException(String email) {
        super("No active account for " + email + " — ask an admin to add you in the Admin console.");
    }
}