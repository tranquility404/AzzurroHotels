package com.tranquility.dashboard.service;

import com.tranquility.dashboard.model.ReviewCursor;
import com.tranquility.dashboard.exception.InvalidReviewCursorException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class ReviewCursorCodec {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final int MAX_CURSOR_LENGTH = 64;

    public String encode(ReviewCursor cursor) {
        String value = cursor.sourcePageNumber() + ":" + cursor.position();
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public ReviewCursor decode(String value) {
        if (value == null || value.length() > MAX_CURSOR_LENGTH || !value.matches("[A-Za-z0-9_-]+")) {
            throw new InvalidReviewCursorException();
        }
        try {
            String decoded = new String(DECODER.decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", -1);
            if (parts.length != 2) {
                throw new InvalidReviewCursorException();
            }
            ReviewCursor cursor = new ReviewCursor(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            if (!encode(cursor).equals(value)) {
                throw new InvalidReviewCursorException();
            }
            return cursor;
        } catch (InvalidReviewCursorException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new InvalidReviewCursorException(exception);
        }
    }
}
