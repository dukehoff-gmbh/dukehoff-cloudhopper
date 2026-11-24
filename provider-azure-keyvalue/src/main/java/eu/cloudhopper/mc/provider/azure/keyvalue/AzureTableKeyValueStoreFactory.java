package eu.cloudhopper.mc.provider.azure.keyvalue;


/*-
 * #%L
 * provider-azure-keyvalue - a library from the "Cloudhopper" project.
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
import eu.cloudhopper.mc.provider.azure.auth.StorageAccount;
import eu.cloudhopper.mc.runtime.KeyValueStore;
import eu.cloudhopper.mc.runtime.KeyValueStoreException;
import eu.cloudhopper.mc.runtime.spi.KeyValueStoreFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Stores for the tables of the function app's storage account, whose connection string the app
 * has as {@code AzureWebJobsStorage}. The table's name is {@code cloudhopper.table.<name>.name},
 * or the name of the annotation. A {@code TableEndpoint} in the connection string, such as
 * Azurite's, takes the place of the account's Table service endpoint.
 */
public class AzureTableKeyValueStoreFactory implements KeyValueStoreFactory {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public KeyValueStore table(String name, boolean existing) {
        Config config = ConfigProvider.getConfig();
        String connection = config.getOptionalValue("AzureWebJobsStorage", String.class)
                .orElseThrow(() -> new KeyValueStoreException("No storage account for table " + name
                        + ": the function app has no AzureWebJobsStorage setting"));
        StorageAccount account = StorageAccount.parse(connection);
        String table = config.getOptionalValue("cloudhopper.table." + name + ".name", String.class).orElse(name);
        return new AzureTableKeyValueStore(MAPPER, HTTP, account, URI.create(endpoint(connection, account)), table,
                Clock.systemUTC(), Duration.ofMillis(100));
    }

    /** The connection string's TableEndpoint, or the account's Table service endpoint. */
    static String endpoint(String connection, StorageAccount account) {
        for (String part : connection.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0 && "TableEndpoint".equals(part.substring(0, eq).trim())) {
                return part.substring(eq + 1).trim();
            }
        }
        return account.endpoint("table");
    }
}
