package com.atlas.knowledgebase;

import com.atlas.knowledgebase.providers.ProviderProperties;
import com.atlas.knowledgebase.session.SessionProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({SessionProperties.class, ProviderProperties.class})
public class AtlasConfiguration {

    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }
}
