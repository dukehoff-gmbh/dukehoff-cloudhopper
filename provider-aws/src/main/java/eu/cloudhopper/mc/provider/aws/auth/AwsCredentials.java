package eu.cloudhopper.mc.provider.aws.auth;

/*-
 * #%L
 * provider-aws - a library from the "Cloudhopper" project.
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

import java.util.Map;

/**
 * AWS credentials of the function's execution role. Lambda passes them in the environment
 * ({@code AWS_ACCESS_KEY_ID}, {@code AWS_SECRET_ACCESS_KEY}, {@code AWS_SESSION_TOKEN}), or, for
 * functions with SnapStart, through the container credentials endpoint
 * ({@code AWS_CONTAINER_CREDENTIALS_FULL_URI} and {@code AWS_CONTAINER_AUTHORIZATION_TOKEN}).
 *
 * @param accessKeyId     the access key id
 * @param secretAccessKey the secret access key
 * @param sessionToken    the session token of temporary credentials, or {@code null}
 */
public record AwsCredentials(String accessKeyId, String secretAccessKey, String sessionToken) {

    private static final ContainerCredentials CONTAINER = new ContainerCredentials();

    /**
     * The credentials from the environment variables, or else from the container credentials
     * endpoint; those are cached until shortly before they expire. Resolve them for every request:
     * after a SnapStart restore the environment and the endpoint's token are new.
     *
     * @return the credentials
     * @throws IllegalStateException if there are none
     */
    public static AwsCredentials fromEnvironment() {
        return resolve(System.getenv(), CONTAINER);
    }

    static AwsCredentials resolve(Map<String, String> environment, ContainerCredentials container) {
        String id = environment.get("AWS_ACCESS_KEY_ID");
        String secret = environment.get("AWS_SECRET_ACCESS_KEY");
        if (id != null && !id.isBlank() && secret != null && !secret.isBlank()) {
            String token = environment.get("AWS_SESSION_TOKEN");
            return new AwsCredentials(id, secret, token == null || token.isBlank() ? null : token);
        }
        AwsCredentials fromContainer = container.get(environment);
        if (fromContainer != null) {
            return fromContainer;
        }
        throw new IllegalStateException("No AWS credentials in the environment (AWS_ACCESS_KEY_ID, "
                + "AWS_SECRET_ACCESS_KEY) and no container credentials endpoint (AWS_CONTAINER_CREDENTIALS_FULL_URI)");
    }

    @Override
    public String toString() {
        return "AwsCredentials[" + accessKeyId + "]";
    }
}
