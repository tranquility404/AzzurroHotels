package com.tranquility.collection.infrastructure.omkar.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OmkarProperties.class)
public class OmkarBookingConfiguration {

    @Bean
    RestClient omkarBookingRestClient(RestClient.Builder builder, OmkarProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(45));
        return builder
                .baseUrl(properties.getBookingBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
