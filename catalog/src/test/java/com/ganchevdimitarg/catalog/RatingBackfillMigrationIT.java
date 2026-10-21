package com.ganchevdimitarg.catalog;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that {@code V16__denormalise_product_ratings.sql} backfills
 * {@code rating_count} / {@code avg_stars} from the live comments at migration time.
 *
 * <p>Flyway's {@code target(...)} API drives a real migration run against a database
 * created for this test on the shared Postgres container: the schema is migrated to V15,
 * rows are seeded with the pre-rating shape, then V16 is applied and the derived columns
 * are asserted. No fallback is needed — the target API works, so the migration itself is
 * exercised rather than a copy of its {@code UPDATE}.
 *
 * <p>This class deliberately does not extend {@link AbstractIntegrationTest}: it needs no
 * Spring context, only the singleton container that the base class starts in its static
 * initialiser (accessible because this class sits in the same package).
 */
@Tag("integration")
class RatingBackfillMigrationIT {

    private static final UUID CATEGORY_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RATED_PRODUCT_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID UNRATED_PRODUCT_ID = UUID.fromString("20000000-0000-0000-0000-000000000003");
    private static final UUID ONLY_DELETED_PRODUCT_ID = UUID.fromString("20000000-0000-0000-0000-000000000004");

    private static final UUID LIVE_FIVE_STARS = UUID.fromString("30000000-0000-0000-0000-000000000005");
    private static final UUID LIVE_THREE_STARS = UUID.fromString("30000000-0000-0000-0000-000000000006");
    private static final UUID DELETED_ONE_STAR = UUID.fromString("30000000-0000-0000-0000-000000000007");
    private static final UUID ONLY_DELETED_ONE_STAR = UUID.fromString("30000000-0000-0000-0000-000000000008");

    private static String databaseName;
    private static String databaseUrl;

    @BeforeAll
    static void createIsolatedDatabase() throws SQLException {
        databaseName = "rating_backfill_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + databaseName);
        }
        databaseUrl = "jdbc:postgresql://%s:%d/%s".formatted(
                AbstractIntegrationTest.POSTGRES.getHost(),
                AbstractIntegrationTest.POSTGRES.getMappedPort(5432),
                databaseName);
    }

    @AfterAll
    static void dropIsolatedDatabase() throws SQLException {
        if (databaseName == null) {
            return;
        }
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + databaseName + " WITH (FORCE)");
        }
    }

    @Test
    void should_backfillRatingFromLiveComments_when_v16Runs() throws SQLException {
        migrateTo("15");
        assertThat(ratingColumns()).isEmpty();

        seedPreMigrationRows();

        migrateTo("16");

        assertThat(ratingOf(RATED_PRODUCT_ID)).isEqualTo(new Rating(2, 4.0));
        assertThat(ratingOf(UNRATED_PRODUCT_ID)).isEqualTo(new Rating(0, 0.0));
        assertThat(ratingOf(ONLY_DELETED_PRODUCT_ID)).isEqualTo(new Rating(0, 0.0));
    }

    private static void migrateTo(String version) {
        Flyway.configure()
                .dataSource(databaseUrl,
                        AbstractIntegrationTest.POSTGRES.getUsername(),
                        AbstractIntegrationTest.POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private static List<String> ratingColumns() throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     select column_name
                     from information_schema.columns
                     where table_name = 'products'
                       and column_name in ('rating_count', 'avg_stars')
                     """)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    columns.add(resultSet.getString("column_name"));
                }
            }
        }
        return columns;
    }

    private static void seedPreMigrationRows() throws SQLException {
        try (Connection connection = connection()) {
            insert(connection, "insert into categories (id, name) values (?, ?)",
                    CATEGORY_ID, "rating-backfill-category");
            insertProduct(connection, RATED_PRODUCT_ID, "rating-backfill-rated");
            insertProduct(connection, UNRATED_PRODUCT_ID, "rating-backfill-unrated");
            insertProduct(connection, ONLY_DELETED_PRODUCT_ID, "rating-backfill-only-deleted");

            insertComment(connection, LIVE_FIVE_STARS, 5.0, RATED_PRODUCT_ID, false);
            insertComment(connection, LIVE_THREE_STARS, 3.0, RATED_PRODUCT_ID, false);
            insertComment(connection, DELETED_ONE_STAR, 1.0, RATED_PRODUCT_ID, true);
            insertComment(connection, ONLY_DELETED_ONE_STAR, 1.0, ONLY_DELETED_PRODUCT_ID, true);
        }
    }

    private static void insertProduct(Connection connection, UUID id, String name) throws SQLException {
        insert(connection, """
                        insert into products (id, name, description, price, stock, characteristics, category_id)
                        values (?, ?, ?, ?, ?, ?, ?)
                        """,
                id, name, "Product seeded before the rating backfill", new BigDecimal("9.99"),
                true, "seeded characteristics", CATEGORY_ID);
    }

    private static void insertComment(Connection connection, UUID id, double star, UUID productId,
                                      boolean softDeleted) throws SQLException {
        insert(connection, """
                        insert into comments (id, title, text, star, author, product_id, created_at, updated_at, deleted_at)
                        values (?, ?, ?, ?, ?, ?, now(), now(), %s)
                        """.formatted(softDeleted ? "now()" : "null"),
                id, "great", "thorough review text", star, "joe", productId);
    }

    private static void insert(Connection connection, String sql, Object... arguments) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            statement.executeUpdate();
        }
    }

    private static Rating ratingOf(UUID productId) throws SQLException {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "select rating_count, avg_stars from products where id = ?")) {
            statement.setObject(1, productId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return new Rating(resultSet.getInt("rating_count"), resultSet.getDouble("avg_stars"));
            }
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(databaseUrl,
                AbstractIntegrationTest.POSTGRES.getUsername(),
                AbstractIntegrationTest.POSTGRES.getPassword());
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(AbstractIntegrationTest.POSTGRES.getJdbcUrl(),
                AbstractIntegrationTest.POSTGRES.getUsername(),
                AbstractIntegrationTest.POSTGRES.getPassword());
    }

    private record Rating(int count, double average) {
    }
}
