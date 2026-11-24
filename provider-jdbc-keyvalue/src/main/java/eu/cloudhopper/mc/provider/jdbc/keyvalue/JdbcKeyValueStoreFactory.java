package eu.cloudhopper.mc.provider.jdbc.keyvalue;


/*-
 * #%L
 * provider-jdbc-keyvalue - a library from the "Cloudhopper" project.
 *
 * Dukehoff GmbH designates this particular file as subject to the "Classpath"
 * exception as provided in the LICENSE-EXCEPTIONS file that accompanies this code.
 * %%
 * Copyright (C) 2024 - 2026 Dukehoff GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import eu.cloudhopper.mc.runtime.KeyValueStore;
import eu.cloudhopper.mc.runtime.KeyValueStoreException;
import eu.cloudhopper.mc.runtime.spi.KeyValueStoreFactory;
import java.time.Duration;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Stores for tables of a PostgreSQL database, through JDBC and a small connection pool shared by
 * all tables. Configuration properties:
 *
 * <ul>
 *   <li>{@code cloudhopper.keyvalue.jdbc.url} (required): the JDBC URL of the database, e.g.
 *       {@code jdbc:postgresql://postgres.db.svc:5432/app}</li>
 *   <li>{@code cloudhopper.keyvalue.jdbc.username} and {@code cloudhopper.keyvalue.jdbc.password}:
 *       the database user</li>
 *   <li>{@code cloudhopper.keyvalue.jdbc.pool-size} (4): the maximum number of connections of an
 *       instance; idle connections are closed after a minute, so that a serverless database can
 *       scale to zero</li>
 *   <li>{@code cloudhopper.table.<name>.name}: the table's name in the database, set by the
 *       deployment; without it the name from {@code @Table}</li>
 * </ul>
 */
public class JdbcKeyValueStoreFactory implements KeyValueStoreFactory {

    /** The configuration property of the JDBC URL. */
    public static final String URL = "cloudhopper.keyvalue.jdbc.url";
    /** The configuration property of the database user. */
    public static final String USERNAME = "cloudhopper.keyvalue.jdbc.username";
    /** The configuration property of the database user's password. */
    public static final String PASSWORD = "cloudhopper.keyvalue.jdbc.password";
    /** The configuration property of the pool size. */
    public static final String POOL_SIZE = "cloudhopper.keyvalue.jdbc.pool-size";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static HikariDataSource dataSource;

    @Override
    public KeyValueStore table(String name, boolean existing) {
        Config config = ConfigProvider.getConfig();
        String table = config.getOptionalValue("cloudhopper.table." + name + ".name", String.class).orElse(name);
        return new JdbcKeyValueStore(MAPPER, dataSource(config), table, existing, Duration.ofMillis(200));
    }

    private static synchronized HikariDataSource dataSource(Config config) {
        if (dataSource == null) {
            String url = config.getOptionalValue(URL, String.class).filter(v -> !v.isBlank())
                    .orElseThrow(() -> new KeyValueStoreException("No database for the @Table parameters: set " + URL
                            + " (environment variable CLOUDHOPPER_KEYVALUE_JDBC_URL)"));
            HikariConfig pool = new HikariConfig();
            pool.setPoolName("cloudhopper-keyvalue");
            pool.setJdbcUrl(url);
            config.getOptionalValue(USERNAME, String.class).ifPresent(pool::setUsername);
            config.getOptionalValue(PASSWORD, String.class).ifPresent(pool::setPassword);
            pool.setMaximumPoolSize(config.getOptionalValue(POOL_SIZE, Integer.class).orElse(4));
            pool.setMinimumIdle(0);
            pool.setIdleTimeout(Duration.ofMinutes(1).toMillis());
            pool.setMaxLifetime(Duration.ofMinutes(10).toMillis());
            // a serverless database may need a while to start
            pool.setConnectionTimeout(Duration.ofSeconds(30).toMillis());
            // connect on first use, not here
            pool.setInitializationFailTimeout(-1);
            dataSource = new HikariDataSource(pool);
        }
        return dataSource;
    }
}
