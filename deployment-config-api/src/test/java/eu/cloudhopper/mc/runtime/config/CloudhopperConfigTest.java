package eu.cloudhopper.mc.runtime.config;

/*-
 * #%L
 * deployment-config-api - a library from the "Cloudhopper" project.
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

import eu.cloudhopper.mc.runtime.ConfigParams;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CloudhopperConfigTest {

    private static Config config(Map<String, String> environment, ConfigSource... sources) {
        return new CloudhopperConfigBuilder().withSources(sources)
                .withSources(new EnvironmentConfigSource(environment)).build();
    }

    private static ConfigSource source(int ordinal, Map<String, String> values) {
        return new ConfigSource() {
            @Override
            public Set<String> getPropertyNames() {
                return values.keySet();
            }

            @Override
            public String getValue(String name) {
                return values.get(name);
            }

            @Override
            public int getOrdinal() {
                return ordinal;
            }

            @Override
            public String getName() {
                return "test" + ordinal;
            }
        };
    }

    @Test
    public void environmentVariablesFollowTheMicroProfileMapping() {
        Config config = config(Map.of("REPORT_BUCKET", "reports", "retry_count", "4"));
        assertEquals("reports", config.getValue("report.bucket", String.class));
        assertEquals(4, config.getValue("retry.count", Integer.class));
        assertEquals("REPORT_BUCKET", EnvironmentConfigSource.environmentName("report.bucket"));
    }

    @Test
    public void higherOrdinalWins() {
        Config config = config(Map.of("NAME", "env"), source(400, Map.of("name", "high")), source(100, Map.of("name", "low")));
        assertEquals("high", config.getValue("name", String.class));
        assertEquals("test400", config.getConfigValue("name").getSourceName());
        assertEquals("env", config(Map.of("NAME", "env"), source(100, Map.of("name", "low"))).getValue("name", String.class));
    }

    @Test
    public void convertsBuiltInAndImplicitTypes() {
        Config config = config(Map.of(), source(100, Map.of(
                "flag", "yes", "count", "42", "ratio", "0.5", "timeout", "PT30S", "mode", "B", "list", "a,b\\,c,d")));
        assertTrue(config.getValue("flag", Boolean.class));
        assertEquals(42L, config.getValue("count", Long.class));
        assertEquals(0.5, config.getValue("ratio", Double.class));
        assertEquals(Duration.ofSeconds(30), config.getValue("timeout", Duration.class)); // parse(CharSequence)
        assertEquals(Mode.B, config.getValue("mode", Mode.class));
        assertEquals(List.of("a", "b,c", "d"), config.getValues("list", String.class));
    }

    @Test
    public void emptyAndMissingValuesAreAbsent() {
        Config config = config(Map.of(), source(100, Map.of("empty", "")));
        assertEquals(Optional.empty(), config.getOptionalValue("empty", String.class));
        assertEquals(Optional.empty(), config.getOptionalValue("missing", String.class));
        assertThrows(NoSuchElementException.class, () -> config.getValue("missing", String.class));
    }

    @Test
    public void configParamsUseTheDefaultAndNameTheEnvironmentVariable() {
        assertTrue(ConfigProvider.getConfig() instanceof CloudhopperConfig);
        System.setProperty("tck.params.set", "7");
        try {
            assertEquals(7, ConfigParams.value("tck.params.set", Integer.class, "3"));
            assertEquals(3, ConfigParams.value("tck.params.missing", Integer.class, "3"));
            assertEquals(Optional.empty(), ConfigParams.optional("tck.params.missing", String.class, null));
            assertEquals(List.of(1, 2), ConfigParams.values("tck.params.missing", Integer.class, "1,2"));
            NoSuchElementException e = assertThrows(NoSuchElementException.class,
                    () -> ConfigParams.value("tck.params.missing", String.class, null));
            assertTrue(e.getMessage().contains("TCK_PARAMS_MISSING"), e.getMessage());
        } finally {
            System.clearProperty("tck.params.set");
        }
    }

    enum Mode { A, B }
}
