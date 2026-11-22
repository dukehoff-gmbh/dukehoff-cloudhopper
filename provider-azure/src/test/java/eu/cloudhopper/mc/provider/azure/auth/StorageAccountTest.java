package eu.cloudhopper.mc.provider.azure.auth;

/*-
 * #%L
 * provider-azure - a library from the "Cloudhopper" project.
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

import com.azure.storage.common.StorageSharedKeyCredential;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StorageAccountTest {

    private static final String KEY = "c2VjcmV0LWtleS1mb3ItdGVzdHMtb25seS0xMjM0NTY3ODkw";

    @Test
    public void parsesTheConnectionString() {
        StorageAccount account = StorageAccount.parse("DefaultEndpointsProtocol=https;AccountName=acct;AccountKey=" + KEY
                + ";EndpointSuffix=core.windows.net");
        assertEquals("acct", account.name());
        assertEquals("https://acct.queue.core.windows.net", account.endpoint("queue"));
    }

    /** The signature of a Put Message request, as the Azure SDK computes it. */
    @Test
    public void sharedKeyMatchesTheAzureSdk() throws Exception {
        StorageAccount account = new StorageAccount("acct", KEY, "https", "core.windows.net");
        String date = "Sat, 21 Nov 2026 10:00:00 GMT";
        String ours = account.sharedKey("POST", "/orders/messages", Map.of(), 87, "application/xml",
                Map.of("x-ms-date", date, "x-ms-version", "2021-08-06"));

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Length", "87");
        headers.put("Content-Type", "application/xml");
        headers.put("x-ms-date", date);
        headers.put("x-ms-version", "2021-08-06");
        String sdk = new StorageSharedKeyCredential("acct", KEY)
                .generateAuthorizationHeader(new URL("https://acct.queue.core.windows.net/orders/messages"), "POST", headers);
        assertEquals(sdk, ours);
    }

    @Test
    public void queryParametersAreSignedLikeTheAzureSdk() throws Exception {
        StorageAccount account = new StorageAccount("acct", KEY, "https", "core.windows.net");
        String date = "Sat, 21 Nov 2026 10:00:00 GMT";
        String ours = account.sharedKey("GET", "/orders/messages", Map.of("numofmessages", "5", "peekonly", "true"), 0, "",
                Map.of("x-ms-date", date, "x-ms-version", "2021-08-06"));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Length", "0"); // the SDK always sends it; without it, it signs "null"
        headers.put("x-ms-date", date);
        headers.put("x-ms-version", "2021-08-06");
        String sdk = new StorageSharedKeyCredential("acct", KEY).generateAuthorizationHeader(
                new URL("https://acct.queue.core.windows.net/orders/messages?peekonly=true&numofmessages=5"), "GET", headers);
        assertEquals(sdk, ours);
    }
}
