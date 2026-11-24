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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a {@link eu.cloudhopper.mc.runtime.KeyValueStore} parameter to a table of items that the
 * function reads or writes.
 *
 * <p>The generator creates the table once per name, grants the function's identity exactly the
 * declared {@link #access()} where the target has identities per function, and passes the
 * table's name to the runtime. A table owned by another system is declared with
 * {@code existing = true}.</p>
 *
 * <pre>{@code
 * @Function(name = "getAccount")
 * @HttpTrigger(path = "/accounts/{id}", method = "GET")
 * public Map<String, Object> account(@PathParam("id") String id,
 *                                    @Table(name = "accounts") KeyValueStore accounts) {
 *     return accounts.get(new Key("account:" + id, "profile")).map(Item::attributes).orElse(Map.of());
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.PARAMETER)
public @interface Table {

    /**
     * What a function may do with a table.
     */
    enum Access {
        /** {@code get} and {@code query} */
        READ,
        /** {@code put}, {@code putIfAbsent}, {@code update}, {@code delete} and the batch operations */
        WRITE,
        /** everything */
        READ_WRITE;

        /**
         * @return whether {@code get} and {@code query} are allowed
         */
        public boolean canRead() {
            return this != WRITE;
        }

        /**
         * @return whether the writing operations are allowed
         */
        public boolean canWrite() {
            return this != READ;
        }
    }

    /**
     * Constants for the attribute names of {@link Table}, for generator feature declarations.
     */
    public static class TableAttribute {

        public static final String NAME = "name";
        public static final String ACCESS = "access";
        public static final String EXISTING = "existing";
    }

    /**
     * The name of the table.
     *
     * <p>Use 3 to 63 lowercase letters and digits, starting with a letter, so that the name is
     * valid on every target. The deployment adds its name prefix where the target has one.</p>
     *
     * @return the table name
     */
    String name();

    /**
     * What the function may do with the table; the default is reading only.
     *
     * @return the access
     */
    Access access() default Access.READ;

    /**
     * {@code true} for a table another system owns: it is used under exactly this name, without
     * the deployment's name prefix, and the generator does not create it. It must have the key
     * schema the target's store expects.
     *
     * @return whether the table exists outside the project
     */
    boolean existing() default false;
}
