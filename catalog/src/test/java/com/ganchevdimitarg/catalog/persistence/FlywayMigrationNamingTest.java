package com.ganchevdimitarg.catalog.persistence;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the Flyway naming convention mechanically so the "next free version" recorded in
 * prose cannot drift from the files on disk. Flyway itself tolerates gaps and other
 * filename shapes; catalog's convention is stricter.
 */
@Tag("unit")
class FlywayMigrationNamingTest {

    private static final Pattern NAME =
            Pattern.compile("^V([1-9][0-9]*)__[a-z0-9]+(?:_[a-z0-9]+)*\\.sql$");

    @Test
    void should_useContiguousVersionedSnakeCaseNames_when_migrationsAreScanned() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/V*.sql");
        assertThat(resources).isNotEmpty();

        List<Integer> versions = new ArrayList<>();
        for (Resource resource : resources) {
            String name = resource.getFilename();
            Matcher matcher = name == null ? null : NAME.matcher(name);
            assertThat(matcher != null && matcher.matches())
                    .as("migration %s must match V<n>__snake_case.sql", name)
                    .isTrue();
            versions.add(Integer.parseInt(matcher.group(1)));
        }

        assertThat(versions).doesNotHaveDuplicates();
        List<Integer> sorted = versions.stream().sorted().toList();
        List<Integer> expected = new ArrayList<>();
        for (int version = 1; version <= sorted.size(); version++) {
            expected.add(version);
        }
        assertThat(sorted).containsExactlyElementsOf(expected);
    }
}
