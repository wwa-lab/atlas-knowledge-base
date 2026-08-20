package com.atlas.knowledgebase.config;

import java.util.Locale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

/**
 * Enforces ADR-0005: H2 is local-only; {@code non-prod} and {@code prod} must
 * use Oracle. Runs before DataSource auto-configuration.
 */
public final class DatasourcePlaneEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String H2_FORBIDDEN =
            "H2 is not an allowed datastore for non-prod or prod (ADR-0005).";

    static final String ORACLE_REQUIRED =
            "non-prod and prod must use Oracle 19c (jdbc:oracle:...).";

    static final String URL_REQUIRED =
            "spring.datasource.url / ATLAS_DATASOURCE_URL is required on non-prod and prod.";

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {
        validate(environment);
    }

    static void validate(Environment environment) {
        if (!isDeployedPlane(environment)) {
            return;
        }
        String url = environment.getProperty("spring.datasource.url", "").trim();
        if (url.isEmpty()) {
            throw new IllegalStateException(URL_REQUIRED);
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("jdbc:h2:") || lower.contains(":h2:")) {
            throw new IllegalStateException(H2_FORBIDDEN);
        }
        if (!lower.startsWith("jdbc:oracle:")) {
            throw new IllegalStateException(ORACLE_REQUIRED);
        }
    }

    private static boolean isDeployedPlane(Environment environment) {
        return environment.matchesProfiles("non-prod") || environment.matchesProfiles("prod");
    }
}
