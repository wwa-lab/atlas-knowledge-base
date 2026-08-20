package com.atlas.knowledgebase.secrets;

/**
 * Thrown when a {@code secret_ref} cannot be resolved. The message must not
 * include secret material.
 */
public class SecretResolutionException extends RuntimeException {

    public SecretResolutionException(String message) {
        super(message);
    }

    public SecretResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
