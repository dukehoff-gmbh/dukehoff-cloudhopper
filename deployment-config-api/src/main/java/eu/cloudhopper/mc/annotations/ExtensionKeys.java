package eu.cloudhopper.mc.annotations;

/*-
 * #%L
 * deployment-config-api - a library from the "Cloudhopper" project.
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
 * Defines well-known extension keys and allowed values for use with {@link eu.cloudhopper.mc.annotations.Extension}.
 *
 * <p>This utility class provides a structured and type-safe way to reference cloud provider–specific
 * extension keys, such as AWS architecture identifiers, to avoid hardcoding strings in annotations.</p>
 *
 * <p>These constants can be used in {@link eu.cloudhopper.mc.annotations.Function#extensions()} to configure
 * deployment-specific parameters, for example:</p>
 *
 * <pre>{@code
 * @Function(
 *     name = "hello",
 *     extensions = {
 *         @Extension(
 *             name = ExtensionKeys.Aws.Architecture.KEY,
 *             value = ExtensionKeys.Aws.Architecture.Values.ARM64
 *         )
 *     }
 * )
 * }</pre>
 *
 * <p>This class is not intended to be instantiated or extended.</p>
 */
public final class ExtensionKeys {

    private ExtensionKeys() { /* no-instantiation */ }

    /**
     * AWS-specific extension keys.
     */
    public static final class Aws {
        private Aws() {}

        /**
         * Extension key for specifying the target CPU architecture on AWS platforms.
         *
         * <p>Key: {@code x-aws-architecture}</p>
         *
         * <p>Used to specify whether the function should run on {@code arm64} or {@code x86_64}
         * hardware.</p>
         */
        public static final class Architecture {
            private Architecture() {}

            /**
             * The key for specifying AWS function architecture:
             * {@code "x-aws-architecture"}
             */
            public static final String KEY = "x-aws-architecture";

            /**
             * Allowed values for {@link #KEY}.
             */
            public static final class Values {
                private Values() {}

                /** ARM 64-bit architecture */
                public static final String ARM64 = "arm64";

                /** x86 64-bit architecture */
                public static final String X86_64 = "x86_64";
            }
        }
    }
}
