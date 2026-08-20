package com.atlas.knowledgebase.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DatasourcePlaneEnvironmentPostProcessorTest {

    @Test
    void localPlaneAllowsH2() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        environment.setProperty("spring.datasource.url", "jdbc:h2:mem:atlas;MODE=Oracle");

        assertThatCode(() -> DatasourcePlaneEnvironmentPostProcessor.validate(environment))
                .doesNotThrowAnyException();
    }

    @Test
    void nonProdRejectsH2() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("non-prod");
        environment.setProperty("spring.datasource.url", "jdbc:h2:mem:blocked");

        assertThatThrownBy(() -> DatasourcePlaneEnvironmentPostProcessor.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("H2 is not an allowed datastore");
    }

    @Test
    void prodRejectsH2() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.datasource.url", "jdbc:h2:file:./data/atlas");

        assertThatThrownBy(() -> DatasourcePlaneEnvironmentPostProcessor.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("H2 is not an allowed datastore");
    }

    @Test
    void nonProdRequiresOracleUrl() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("non-prod");
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://db/atlas");

        assertThatThrownBy(() -> DatasourcePlaneEnvironmentPostProcessor.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Oracle 19c");
    }

    @Test
    void prodAcceptsOraclePlaceholderUrl() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty(
                "spring.datasource.url", "jdbc:oracle:thin:@//oracle.example.internal:1521/ORCLPDB1");

        assertThatCode(() -> DatasourcePlaneEnvironmentPostProcessor.validate(environment))
                .doesNotThrowAnyException();
    }
}
