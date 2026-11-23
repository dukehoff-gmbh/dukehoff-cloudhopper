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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SigV4SignerTest {

    /** The credentials and time of the AWS SigV4 test suite. */
    private static final AwsCredentials SUITE = new AwsCredentials("AKIDEXAMPLE",
            "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", null);
    private static final Instant SUITE_TIME = Instant.parse("2015-08-30T12:36:00Z");

    @Test
    public void getVanillaOfTheTestSuite() {
        Map<String, String> headers = SigV4Signer.sign("GET", URI.create("https://example.amazonaws.com/"), Map.of(),
                new byte[0], "service", "us-east-1", SUITE, SUITE_TIME);
        assertEquals("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/service/aws4_request, "
                + "SignedHeaders=host;x-amz-date, "
                + "Signature=5fa00fa31553b73ebf1942676e86291e8372ff2a2260956d9b8aae1d763fbf31", headers.get("Authorization"));
        assertEquals("20150830T123600Z", headers.get("X-Amz-Date"));
    }

    @Test
    public void postVanillaOfTheTestSuite() {
        Map<String, String> headers = SigV4Signer.sign("POST", URI.create("https://example.amazonaws.com/"), Map.of(),
                new byte[0], "service", "us-east-1", SUITE, SUITE_TIME);
        assertEquals("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/service/aws4_request, "
                + "SignedHeaders=host;x-amz-date, "
                + "Signature=5da7c1a2acd57cee7505fc6676e4e544621c30862966e37dddb68e92efbe5d6b", headers.get("Authorization"));
    }

    /** An SQS request as the publisher sends it, with temporary credentials, signed like the AWS SDK does. */
    @Test
    public void sqsRequestMatchesTheAwsSdk() {
        String body = "{\"QueueUrl\":\"https://sqs.eu-central-1.amazonaws.com/123456789012/orders\",\"MessageBody\":\"{\\\"id\\\":1}\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        URI uri = URI.create("https://sqs.eu-central-1.amazonaws.com/");
        Instant time = Instant.parse("2026-11-21T09:15:30Z");
        AwsCredentials credentials = new AwsCredentials("ASIAEXAMPLE", "secretEXAMPLE/key+42", "token/with+chars==");
        Map<String, String> headers = Map.of("Content-Type", "application/x-amz-json-1.0", "X-Amz-Target", "AmazonSQS.SendMessage");

        Map<String, String> ours = SigV4Signer.sign("POST", uri, headers, bytes, "sqs", "eu-central-1", credentials, time);

        SdkHttpFullRequest.Builder request = SdkHttpFullRequest.builder().method(SdkHttpMethod.POST).uri(uri)
                .contentStreamProvider(RequestBody.fromBytes(bytes).contentStreamProvider());
        headers.forEach(request::putHeader);
        SdkHttpFullRequest signed = Aws4Signer.create().sign(request.build(), Aws4SignerParams.builder()
                .awsCredentials(AwsSessionCredentials.create(credentials.accessKeyId(), credentials.secretAccessKey(),
                        credentials.sessionToken()))
                .signingName("sqs").signingRegion(Region.EU_CENTRAL_1)
                .signingClockOverride(java.time.Clock.fixed(time, java.time.ZoneOffset.UTC))
                .build());
        assertEquals(signed.firstMatchingHeader("Authorization").orElseThrow(), ours.get("Authorization"));
        assertEquals(signed.firstMatchingHeader("X-Amz-Security-Token").orElseThrow(), ours.get("X-Amz-Security-Token"));
    }

    @Test
    public void pathAndQueryAreEncodedCanonically() {
        Map<String, String> headers = SigV4Signer.sign("GET", URI.create("https://example.amazonaws.com/a%20b/c?z=1&a=x%2By&a=b"),
                Map.of(), new byte[0], "service", "us-east-1", SUITE, SUITE_TIME);
        SdkHttpFullRequest signed = Aws4Signer.create().sign(SdkHttpFullRequest.builder().method(SdkHttpMethod.GET)
                .uri(URI.create("https://example.amazonaws.com/a%20b/c")).putRawQueryParameter("z", "1")
                .putRawQueryParameter("a", java.util.List.of("x+y", "b")).build(), Aws4SignerParams.builder()
                .awsCredentials(software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(SUITE.accessKeyId(), SUITE.secretAccessKey()))
                .signingName("service").signingRegion(Region.US_EAST_1)
                .signingClockOverride(java.time.Clock.fixed(SUITE_TIME, java.time.ZoneOffset.UTC)).build());
        assertEquals(signed.firstMatchingHeader("Authorization").orElseThrow(), headers.get("Authorization"));
    }

    /** An S3 PUT with a key that needs encoding, signed like the AWS SDK's S3 client does. */
    @Test
    public void s3RequestMatchesTheAwsSdk() {
        byte[] bytes = "{\"prices\":[1,2]}".getBytes(StandardCharsets.UTF_8);
        URI uri = URI.create("https://my-bucket.s3.eu-central-1.amazonaws.com/reports/2026%2011/a%2Bb.json");
        Instant time = Instant.parse("2026-11-23T10:15:30Z");
        AwsCredentials credentials = new AwsCredentials("ASIAEXAMPLE", "secretEXAMPLE/key+42", "token/with+chars==");
        Map<String, String> headers = Map.of("Content-Type", "application/json");

        Map<String, String> ours = SigV4Signer.signS3("PUT", uri, headers, bytes, "eu-central-1", credentials, time);

        SdkHttpFullRequest.Builder request = SdkHttpFullRequest.builder().method(SdkHttpMethod.PUT).uri(uri)
                .contentStreamProvider(RequestBody.fromBytes(bytes).contentStreamProvider());
        headers.forEach(request::putHeader);
        SdkHttpFullRequest signed = software.amazon.awssdk.auth.signer.AwsS3V4Signer.create().sign(request.build(),
                software.amazon.awssdk.auth.signer.params.AwsS3V4SignerParams.builder()
                .awsCredentials(AwsSessionCredentials.create(credentials.accessKeyId(), credentials.secretAccessKey(),
                        credentials.sessionToken()))
                .signingName("s3").signingRegion(Region.EU_CENTRAL_1).enablePayloadSigning(true)
                // as the S3 client configures it: S3 encodes the path only once
                .doubleUrlEncode(false)
                .signingClockOverride(java.time.Clock.fixed(time, java.time.ZoneOffset.UTC))
                .build());
        assertEquals(signed.firstMatchingHeader("x-amz-content-sha256").orElseThrow(), ours.get("X-Amz-Content-Sha256"));
        assertEquals(signed.firstMatchingHeader("Authorization").orElseThrow(), ours.get("Authorization"));
    }
}
