package com.tranquility.collection.infrastructure.omkar.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "omkar")
@Getter
@Setter
public class OmkarProperties {

    private String apiKey = "";
    private String bookingBaseUrl = "https://booking-scraper.omkar.cloud";
    private String locale = "en-gb";

    @Override
    public String toString() {
        return "OmkarProperties[apiKey=REDACTED, bookingBaseUrl=" + bookingBaseUrl + ", locale=" + locale + "]";
    }
}