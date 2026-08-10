package com.aurora.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class FlywayMigrationVersionTest {
  private static final Pattern MIGRATION_NAME = Pattern.compile("V(\\d+)__.*\\.sql");

  @Test
  void migrationVersionsAreContiguousStartingAtOneWithoutDuplicates() throws IOException {
    Resource[] resources =
        new PathMatchingResourcePatternResolver().getResources("classpath*:/db/migration/*.sql");
    List<Integer> versions =
        Arrays.stream(resources)
            .map(Resource::getFilename)
            .map(FlywayMigrationVersionTest::version)
            .sorted()
            .toList();

    assertThat(versions).withFailMessage("No Flyway migrations were found").isNotEmpty();

    List<Integer> duplicates =
        versions.stream()
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue() > 1)
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
    assertThat(duplicates)
        .withFailMessage("Duplicate Flyway migration version(s): %s", duplicates)
        .isEmpty();

    int highestVersion = versions.get(versions.size() - 1);
    List<Integer> missing =
        IntStream.rangeClosed(1, highestVersion)
            .filter(version -> !versions.contains(version))
            .boxed()
            .toList();
    assertThat(missing)
        .withFailMessage("Missing Flyway migration version(s): %s (found %s)", missing, versions)
        .isEmpty();
  }

  private static int version(String filename) {
    Matcher matcher = MIGRATION_NAME.matcher(filename);
    if (!matcher.matches()) {
      throw new AssertionError("Invalid Flyway migration filename: " + filename);
    }
    return Integer.parseInt(matcher.group(1));
  }
}
