package com.tranquility.collection;

import java.net.URI;
import java.util.Locale;

public final class BookingHotelUrlValidator {

    private BookingHotelUrlValidator() {
    }

    public static String validate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Enter a Booking.com hotel URL.");
        }

        try {
            URI uri = URI.create(value.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || !(host.equalsIgnoreCase("booking.com") || host.toLowerCase(Locale.ROOT).endsWith(".booking.com"))
                    || uri.getUserInfo() != null
                    || uri.getPath() == null
                    || !uri.getPath().startsWith("/hotel/")) {
                throw new IllegalArgumentException("Use a secure Booking.com hotel page URL, such as https://www.booking.com/hotel/au/name.html.");
            }
            return value.trim();
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("Use a secure")) {
                throw exception;
            }
            throw new IllegalArgumentException("Enter a valid Booking.com hotel page URL.", exception);
        }
    }

    public static String canonicalKey(String value) {
        URI uri = URI.create(validate(value));
        String path = uri.getPath().replaceAll("/+$", "").toLowerCase(Locale.ROOT);
        return "https://booking.com" + path;
    }
}
