package com.railway.booking.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class PnrGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DIGITS = "0123456789";

    public String generate() {
        StringBuilder sb = new StringBuilder("PNR");
        for (int i = 0; i < 7; i++) {
            sb.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        }
        return sb.toString();
    }
}
