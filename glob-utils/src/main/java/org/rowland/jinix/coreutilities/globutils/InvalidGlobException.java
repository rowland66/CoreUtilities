package org.rowland.jinix.coreutilities.globutils;

/**
 * Exception indicating that a glob is invalid. The message can be displayed in errors
 */
public class InvalidGlobException extends Exception {
    InvalidGlobException(String message) {
        super(message);
    }
}