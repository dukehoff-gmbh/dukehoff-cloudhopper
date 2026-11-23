package eu.cloudhopper.mc.provider.aws.blobstore;

/*-
 * #%L
 * provider-aws-blobstore - a library from the "Cloudhopper" project.
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

import eu.cloudhopper.mc.provider.aws.auth.AwsCredentials;
import eu.cloudhopper.mc.runtime.BlobStore;
import eu.cloudhopper.mc.runtime.BlobStoreException;
import eu.cloudhopper.mc.runtime.spi.BlobStoreFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Stores for S3 buckets, or for buckets of an S3-compatible object storage such as Scaleway
 * Object Storage or MinIO. Configuration properties:
 *
 * <ul>
 *   <li>{@code cloudhopper.bucket.<name>.name}: the bucket's deployed name, set by the deployment;
 *       without it the name from {@code @Bucket}</li>
 *   <li>{@code cloudhopper.blobstore.s3.endpoint}: the endpoint of an S3-compatible storage, e.g.
 *       {@code https://s3.fr-par.scw.cloud}; buckets are then addressed by path. Without it, S3 of
 *       the region, with the bucket in the host name</li>
 *   <li>{@code cloudhopper.blobstore.s3.region}: the signing region; without it
 *       {@code AWS_REGION}</li>
 * </ul>
 *
 * <p>The credentials come from {@code AWS_ACCESS_KEY_ID}, {@code AWS_SECRET_ACCESS_KEY} and
 * {@code AWS_SESSION_TOKEN}, which Lambda sets for the function's execution role.</p>
 */
public class S3BlobStoreFactory implements BlobStoreFactory {

    /** The configuration property of an S3-compatible endpoint. */
    public static final String ENDPOINT = "cloudhopper.blobstore.s3.endpoint";
    /** The configuration property of the signing region. */
    public static final String REGION = "cloudhopper.blobstore.s3.region";

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Override
    public BlobStore bucket(String name, boolean existing) {
        Config config = ConfigProvider.getConfig();
        String bucket = config.getOptionalValue("cloudhopper.bucket." + name + ".name", String.class).orElse(name);
        String region = config.getOptionalValue(REGION, String.class).orElseGet(() -> System.getenv("AWS_REGION"));
        if (region == null || region.isBlank()) {
            throw new BlobStoreException("Cannot tell the region of bucket " + bucket + ": set AWS_REGION or " + REGION);
        }
        String endpoint = config.getOptionalValue(ENDPOINT, String.class).orElse(null);
        return new S3BlobStore(HTTP, root(bucket, region, endpoint), bucket, region, AwsCredentials::fromEnvironment,
                Clock.systemUTC(), Duration.ofMillis(100));
    }

    /** The bucket's URL: by path at an S3-compatible endpoint or for names with dots, else in the host name. */
    static URI root(String bucket, String region, String endpoint) {
        if (endpoint != null && !endpoint.isBlank()) {
            String base = endpoint.endsWith("/") ? endpoint : endpoint + "/";
            return URI.create(base + bucket + "/");
        }
        String host = "s3." + region + ".amazonaws.com" + (region.startsWith("cn-") ? ".cn" : "");
        return bucket.contains(".")
                ? URI.create("https://" + host + "/" + bucket + "/")
                : URI.create("https://" + bucket + "." + host + "/");
    }
}
