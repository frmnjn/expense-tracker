package com.expensetracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfig {

    @Bean
    public Clock reportClock(@Value("${report.zone:Asia/Jakarta}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
