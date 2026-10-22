package com.ganchevdimitarg.catalog.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the profile split: the base file stays environment-neutral, {@code prod} carries no
 * development defaults, and {@code dev} keeps the local composition root behind placeholders.
 */
@Tag("unit")
class ConfigurationProfilesTest {

    private static final List<String> PROD_EXTERNAL_ENDPOINTS = List.of(
            "catalog.datasource.writer.url",
            "catalog.datasource.reader.url",
            "spring.security.oauth2.resourceserver.jwt.issuer-uri",
            "spring.data.redis.host",
            "spring.kafka.bootstrap-servers",
            "management.server.port");

    @Test
    void should_not_contain_localhost_in_base_or_prod_config_when_config_files_are_read() {
        assertThat(new ClassPathResource("application-prod.yml").exists())
                .as("application-prod.yml must exist")
                .isTrue();

        for (String name : List.of("application.yml", "application-prod.yml")) {
            assertThat(load(name))
                    .as("%s must not carry local defaults", name)
                    .allSatisfy((key, value) -> assertThat(String.valueOf(value))
                            .as("value of %s in %s", key, name)
                            .doesNotContain("localhost"));
        }
    }

    @Test
    void should_disable_swagger_vault_and_docker_compose_in_prod_when_prod_profile_is_active() {
        assertThat(load("application-prod.yml"))
                .containsEntry("springdoc.api-docs.enabled", false)
                .containsEntry("springdoc.swagger-ui.enabled", false)
                .containsEntry("spring.cloud.vault.enabled", false)
                .containsEntry("spring.docker.compose.enabled", false)
                .doesNotContainKey("spring.devtools.livereload.port");
    }

    @Test
    void should_use_catalog_owned_vault_backend_in_dev_when_dev_config_is_read() {
        Map<String, Object> dev = load("application-dev.yml");

        assertThat(dev)
                .containsEntry("spring.cloud.vault.kv.backend", "${VAULT_BACKEND:secret}")
                .containsEntry("spring.cloud.vault.kv.default-context", "${VAULT_DEFAULT_CONTEXT:catalog/credentials}");

        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("dev", dev));

        assertThat(environment.getProperty("spring.cloud.vault.kv.backend")).isEqualTo("secret");
        assertThat(environment.getProperty("spring.cloud.vault.kv.default-context")).isEqualTo("catalog/credentials");
    }

    @Test
    void should_require_environment_values_for_external_endpoints_in_prod_when_prod_config_is_read() {
        Map<String, Object> prod = load("application-prod.yml");

        assertThat(prod).containsKeys(PROD_EXTERNAL_ENDPOINTS.toArray(String[]::new));
        assertThat(PROD_EXTERNAL_ENDPOINTS).allSatisfy(key -> assertThat(String.valueOf(prod.get(key)))
                .as("%s must be environment-backed", key)
                .contains("${")
                .contains("}"));
    }

    @Test
    void should_serve_actuator_on_the_management_port_when_dev_config_is_read() {
        assertThat(load("application-dev.yml"))
                .containsEntry("management.server.port", "${MANAGEMENT_PORT:8088}");
    }

    @Test
    void should_keep_the_spring_cloud_compatibility_verifier_disabled_when_base_config_is_read() {
        assertThat(load("application.yml"))
                .containsEntry("spring.cloud.compatibility-verifier.enabled", false);
    }

    private Map<String, Object> load(String name) {
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(name, new ClassPathResource(name));
            Map<String, Object> flattened = new LinkedHashMap<>();
            for (PropertySource<?> source : sources) {
                if (source instanceof EnumerablePropertySource<?> enumerable) {
                    for (String key : enumerable.getPropertyNames()) {
                        flattened.put(key, enumerable.getProperty(key));
                    }
                }
            }
            return flattened;
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not load " + name, ex);
        }
    }
}
