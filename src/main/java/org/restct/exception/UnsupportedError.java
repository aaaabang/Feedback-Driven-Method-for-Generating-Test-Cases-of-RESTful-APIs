package org.restct.exception;

public class UnsupportedError extends Exception {
    private final String message;

    public UnsupportedError(String message) {
        this.message = message;
    }

    public String getMessage() {
        return this.message;
    }

    public String toString() {
        return this.message;
    }
}

