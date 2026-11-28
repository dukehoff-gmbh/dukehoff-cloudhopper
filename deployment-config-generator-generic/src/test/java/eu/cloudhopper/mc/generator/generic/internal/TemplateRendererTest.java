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

    /**
     * A template of the project, cloudhopper-templates/<generator>/<template> on the class path
     * of the compilation, replaces the generator's own; the others stay the generator's.
     */
    @Test
    public void aProjectTemplateReplacesTheGeneratorsTemplate() throws Exception {
        javax.tools.FileObject override = new javax.tools.SimpleJavaFileObject(
                java.net.URI.create("string:///cloudhopper-templates/override-test/function.ftl"),
                javax.tools.JavaFileObject.Kind.OTHER) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return "project ${name}";
            }

            @Override
            public java.io.Reader openReader(boolean ignoreEncodingErrors) {
                return new java.io.StringReader("project ${name}");
            }
        };
        javax.annotation.processing.Filer filer = (javax.annotation.processing.Filer) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {javax.annotation.processing.Filer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getResource")
                            && "cloudhopper-templates/override-test/function.ftl".equals(args[2].toString())) {
                        return override;
                    }
                    throw new java.io.FileNotFoundException(String.valueOf(args == null ? null : args[2]));
                });
        javax.annotation.processing.ProcessingEnvironment env = (javax.annotation.processing.ProcessingEnvironment)
                java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[] {javax.annotation.processing.ProcessingEnvironment.class},
                        (proxy, method, args) -> method.getName().equals("getFiler") ? filer : null);
        TemplateRenderer renderer = new TemplateRenderer();
        renderer.initTemplateLoaders(TemplateRendererTest.class, java.util.List.of("override-test"), id -> "/templates/" + id, env);
        Configuration configuration = renderer.configuration();
        StringWriter function = new StringWriter();
        configuration.getTemplate("function.ftl").process(Map.of("name", "hello"), function);
        assertEquals("project hello", function.toString().trim());
        StringWriter other = new StringWriter();
        configuration.getTemplate("other.ftl").process(Map.of(), other);
        assertEquals("built-in other", other.toString().trim());
    }
}
