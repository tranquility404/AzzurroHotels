package com.tranquility.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CollectionRequest(
        @NotEmpty(message = "Add at least one Booking.com hotel URL.")
        @Size(max = 4, message = "Collect up to four properties at a time.")
        List<@NotBlank @Size(max = 2048) String> bookingUrls
) {
}