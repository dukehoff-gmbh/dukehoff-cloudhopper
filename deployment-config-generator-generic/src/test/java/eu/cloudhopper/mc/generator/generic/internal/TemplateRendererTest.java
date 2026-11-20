package eu.cloudhopper.mc.generator.generic.internal;

/*-
 * #%L
 * deployment-config-generator - a library from the "Cloudhopper" project.
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

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Numbers in generated configuration do not depend on the build machine's locale (#84).
 */
public class TemplateRendererTest {

    @Test
    public void numbersHaveNoGroupingSeparatorInAnyLocale() throws Exception {
        Locale saved = Locale.getDefault();
        try {
            for (Locale locale : new Locale[]{Locale.US, Locale.GERMANY, Locale.FRANCE, Locale.forLanguageTag("de-CH")}) {
                Locale.setDefault(locale);
                Configuration configuration = new TemplateRenderer().configuration();
                StringTemplateLoader loader = new StringTemplateLoader();
                loader.putTemplate("t", "memory_size = ${memory}\ntimeout = ${timeout}\nratio = ${ratio}");
                configuration.setTemplateLoader(loader);
                StringWriter out = new StringWriter();
                configuration.getTemplate("t").process(Map.of("memory", 1024, "timeout", 54000, "ratio", 1.5), out);
                assertEquals("memory_size = 1024\ntimeout = 54000\nratio = 1.5", out.toString(), locale.toString());
            }
        } finally {
            Locale.setDefault(saved);
        }
    }
}
