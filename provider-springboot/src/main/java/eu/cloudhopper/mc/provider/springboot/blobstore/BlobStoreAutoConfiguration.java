package eu.cloudhopper.mc.provider.springboot.blobstore;

/*-
 * #%L
 * provider-springboot - a library from the "Cloudhopper" project.
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

import eu.cloudhopper.mc.runtime.BlobStores;
import eu.cloudhopper.mc.runtime.spi.BlobStoreFactory;
import java.nio.file.Path;
import java.util.ServiceLoader;
import org.eclipse.microprofile.config.ConfigProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Makes {@code @Bucket} stores work in the Spring Boot runtime. A {@link BlobStoreFactory} of
 * another runtime module found through {@link ServiceLoader} is used, e.g. the S3 client of
 * {@code provider-aws-blobstore} for S3 or an S3-compatible storage. Without one, every bucket
 * is a directory below {@code cloudhopper.blobstore.dir} (default {@code buckets}), named like
 * the bucket's deployed name ({@code cloudhopper.bucket.<name>.name}).
 */
@AutoConfiguration
public class BlobStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(BlobStoreFactory.class)
    BlobStoreFactory cloudhopperDefaultBlobStoreFactory(Environment environment) {
        BlobStoreFactory discovered = discovered();
        if (discovered != null) {
            return discovered;
        }
        Path directory = Path.of(environment.getProperty("cloudhopper.blobstore.dir", "buckets"));
        return (name, existing) -> new FileSystemBlobStore(directory.resolve(ConfigProvider.getConfig()
                .getOptionalValue("cloudhopper.bucket." + name + ".name", String.class).orElse(name)));
    }

    /** The factory of another runtime module on the classpath, or {@code null}. */
    static BlobStoreFactory discovered() {
        for (BlobStoreFactory factory : ServiceLoader.load(BlobStoreFactory.class,
                BlobStoreAutoConfiguration.class.getClassLoader())) {
            if (!(factory instanceof SpringBlobStoreFactory)) {
                return factory;
            }
        }
        return null;
    }

    @Bean
    SmartInitializingSingleton cloudhopperBlobStoreRegistration(BlobStoreFactory factory) {
        return () -> BlobStores.setFactory(factory);
    }
}
