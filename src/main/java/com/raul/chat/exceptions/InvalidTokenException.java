package com.raul.chat.exceptions;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class InvalidTokenException extends RuntimeException {
    private final String message;
}
