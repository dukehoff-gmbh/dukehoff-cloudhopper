package eu.cloudhopper.mc.generator.generic.internal;

/*-
 * #%L
 * deployment-config-generator-generic - a library from the "Cloudhopper" project.
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

import freemarker.cache.TemplateLoader;
import javax.annotation.processing.Filer;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Reader;

public class FilerTemplateLoader implements TemplateLoader {
    private final Filer filer;
    private final String basePath;  // e.g. "cloudhopper-templates/aws-terraform"

    public FilerTemplateLoader(Filer filer, String basePath) {
        this.filer   = filer;
        this.basePath = basePath;
    }

    @Override
    public Object findTemplateSource(String name) throws IOException {
        try {
            FileObject fo = filer.getResource(StandardLocation.CLASS_PATH, "", basePath + "/" + name);
            return fo;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public long getLastModified(Object templateSource) {
        return 0;
    }

    @Override
    public Reader getReader(Object templateSource, String encoding) throws IOException {
        return ((FileObject) templateSource).openReader(false);
    }

    @Override
    public void closeTemplateSource(Object templateSource) throws IOException {
    }
}
