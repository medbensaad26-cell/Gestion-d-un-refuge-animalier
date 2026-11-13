package com.enicarthage.refugeanimalier.exception;

/** A valid resource identifier that no longer exists. */
public class RessourceIntrouvableException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public RessourceIntrouvableException(String message) {
        super(message);
    }
}
