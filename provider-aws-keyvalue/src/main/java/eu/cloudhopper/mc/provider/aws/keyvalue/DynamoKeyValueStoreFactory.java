package eu.cloudhopper.mc.provider.aws.keyvalue;


/*-
 * #%L
 * provider-aws-keyvalue - a library from the "Cloudhopper" project.
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
import eu.cloudhopper.mc.provider.aws.auth.AwsCredentials;
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
 * Stores for DynamoDB tables. Configuration properties:
 *
 * <ul>
 *   <li>{@code cloudhopper.table.<name>.name}: the table's deployed name, set by the deployment;
 *       without it the name from {@code @Table}</li>
 *   <li>{@code cloudhopper.keyvalue.dynamodb.endpoint}: another endpoint, e.g. DynamoDB Local;
 *       without it DynamoDB of the region</li>
 * </ul>
 *
 * <p>The region comes from {@code AWS_REGION}, the credentials from {@code AWS_ACCESS_KEY_ID},
 * {@code AWS_SECRET_ACCESS_KEY} and {@code AWS_SESSION_TOKEN} or the container credentials
 * endpoint, which Lambda sets for the function's execution role.</p>
 */
public class DynamoKeyValueStoreFactory implements KeyValueStoreFactory {

    /** The configuration property of another endpoint. */
    public static final String ENDPOINT = "cloudhopper.keyvalue.dynamodb.endpoint";

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public KeyValueStore table(String name, boolean existing) {
        Config config = ConfigProvider.getConfig();
        String table = config.getOptionalValue("cloudhopper.table." + name + ".name", String.class).orElse(name);
        String region = System.getenv("AWS_REGION");
        if (region == null || region.isBlank()) {
            throw new KeyValueStoreException("Cannot tell the region of table " + table + ": set AWS_REGION");
        }
        String endpoint = config.getOptionalValue(ENDPOINT, String.class).filter(v -> !v.isBlank()).orElse(null);
        return new DynamoKeyValueStore(MAPPER, HTTP, endpoint(region, endpoint), region, table,
                AwsCredentials::fromEnvironment, Clock.systemUTC(), Duration.ofMillis(50));
    }

    /** The endpoint of the region, or the configured one. */
    static URI endpoint(String region, String endpoint) {
        if (endpoint != null) {
            return URI.create(endpoint.endsWith("/") ? endpoint : endpoint + "/");
        }
        return URI.create("https://dynamodb." + region + ".amazonaws.com" + (region.startsWith("cn-") ? ".cn" : "") + "/");
    }
}
