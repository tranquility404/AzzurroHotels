package com.tranquility.insights.config;

import com.tranquility.insights.tools.ReviewInsightsTools;
import com.tranquility.insights.tools.CurrentTimeTools;
import com.tranquility.insights.tools.HotelProfileTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.ai.session.compaction.TurnCountTrigger;
import org.springframework.ai.session.compaction.WindowCompactionStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InsightsLlmLimitProperties.class)
public class InsightsAgentConfiguration {

    @Bean
    Clock insightsClock() {
        return Clock.systemUTC();
    }

    @Bean
    ChatClient insightsChatClient(
            ChatClient.Builder builder,
            SessionService sessionService,
            ReviewInsightsTools reviewInsightsTools,
            HotelProfileTools hotelProfileTools,
            CurrentTimeTools currentTimeTools
    ) {
        return builder
                .defaultSystem(new ClassPathResource("prompts/guest-insights-system.st"))
                .defaultTools(reviewInsightsTools, hotelProfileTools, currentTimeTools)
                .defaultAdvisors(SessionAdvisors.builder(sessionService)
                        .compaction(new TurnCountTrigger(24),
                                WindowCompactionStrategy.builder().lastTurns(12).build())
                        .build())
                .build();
    }
}
