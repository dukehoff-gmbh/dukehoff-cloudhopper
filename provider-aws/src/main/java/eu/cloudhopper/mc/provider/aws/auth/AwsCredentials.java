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

/**
 * AWS credentials. In a Lambda function they come from the environment, which Lambda fills with
 * the temporary credentials of the execution role.
 *
 * @param accessKeyId     the access key id
 * @param secretAccessKey the secret access key
 * @param sessionToken    the session token of temporary credentials, or {@code null}
 */
public record AwsCredentials(String accessKeyId, String secretAccessKey, String sessionToken) {

    /**
     * Reads {@code AWS_ACCESS_KEY_ID}, {@code AWS_SECRET_ACCESS_KEY} and {@code AWS_SESSION_TOKEN}.
     * Read them for every request: after a SnapStart restore the environment holds new ones.
     *
     * @return the credentials
     * @throws IllegalStateException if the environment has none
     */
    public static AwsCredentials fromEnvironment() {
        String id = System.getenv("AWS_ACCESS_KEY_ID");
        String secret = System.getenv("AWS_SECRET_ACCESS_KEY");
        if (id == null || id.isBlank() || secret == null || secret.isBlank()) {
            throw new IllegalStateException("No AWS credentials in the environment (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)");
        }
        String token = System.getenv("AWS_SESSION_TOKEN");
        return new AwsCredentials(id, secret, token == null || token.isBlank() ? null : token);
    }

    @Override
    public String toString() {
        return "AwsCredentials[" + accessKeyId + "]";
    }
}
