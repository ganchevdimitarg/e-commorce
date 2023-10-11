package com.concordeu.profile.excaption;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
public class ErrorMessage {
    private int statusCode;
    private OffsetDateTime timestamp;
    private String message;
}
