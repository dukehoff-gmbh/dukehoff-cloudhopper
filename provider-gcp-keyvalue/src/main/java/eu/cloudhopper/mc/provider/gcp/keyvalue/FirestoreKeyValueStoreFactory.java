package eu.cloudhopper.mc.provider.gcp.keyvalue;


/*-
 * #%L
 * provider-gcp-keyvalue - a library from the "Cloudhopper" project.
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

import eu.cloudhopper.mc.provider.gcp.auth.GcpMetadata;
import eu.cloudhopper.mc.runtime.KeyValueStore;
import eu.cloudhopper.mc.runtime.KeyValueStoreException;
import eu.cloudhopper.mc.runtime.spi.KeyValueStoreFactory;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Stores for Firestore databases (Native mode), signed in as the function's service account
 * through the metadata server. Configuration properties:
 *
 * <ul>
 *   <li>{@code cloudhopper.table.<name>.name}: the database's ID, set by the deployment; without
 *       it the name from {@code @Table}</li>
 *   <li>{@code cloudhopper.keyvalue.firestore.endpoint}: another endpoint, e.g. the Firestore
 *       emulator ({@code http://localhost:8080/v1/}); requests to it are not signed in</li>
 * </ul>
 *
 * <p>The project comes from {@code GOOGLE_CLOUD_PROJECT} or the metadata server.</p>
 */
public class FirestoreKeyValueStoreFactory implements KeyValueStoreFactory {

    /** The configuration property of another endpoint. */
    public static final String ENDPOINT = "cloudhopper.keyvalue.firestore.endpoint";

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final GcpMetadata METADATA = new GcpMetadata();
    private static final URI API = URI.create("https://firestore.googleapis.com/v1/");

    @Override
    public KeyValueStore table(String name, boolean existing) {
        Config config = ConfigProvider.getConfig();
        String database = config.getOptionalValue("cloudhopper.table." + name + ".name", String.class).orElse(name);
        String endpoint = config.getOptionalValue(ENDPOINT, String.class).filter(v -> !v.isBlank()).orElse(null);
        String project;
        try {
            project = METADATA.projectId();
        } catch (IOException e) {
            throw new KeyValueStoreException("Cannot tell the project of Firestore database " + database
                    + ": set GOOGLE_CLOUD_PROJECT", e);
        }
        if (endpoint != null) {
            return new FirestoreKeyValueStore(HTTP, URI.create(endpoint.endsWith("/") ? endpoint : endpoint + "/"),
                    project, database, () -> "owner", Duration.ofMillis(100));
        }
        return new FirestoreKeyValueStore(HTTP, API, project, database, METADATA::accessToken, Duration.ofMillis(100));
    }
}
