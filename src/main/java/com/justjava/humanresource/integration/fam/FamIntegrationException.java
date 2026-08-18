package com.justjava.humanresource.integration.fam;

public class FamIntegrationException extends RuntimeException {

    public FamIntegrationException(String message) {
        super(message);
    }

    public FamIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
