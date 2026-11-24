package eu.cloudhopper.mc.generator.api;

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

import java.util.Locale;

/**
 * A table a function reads or writes through a {@code @Table} parameter.
 */
public class TableInfo {

    private final String name;
    private final String access;
    private final boolean existing;

    /**
     * @param name     the table name
     * @param access   the declared access: {@code READ}, {@code WRITE} or {@code READ_WRITE}
     * @param existing {@code true} for a table outside the project, used without name prefix
     */
    public TableInfo(String name, String access, boolean existing) {
        this.name = name;
        this.access = access;
        this.existing = existing;
    }

    /**
     * @return the table name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the declared access: {@code READ}, {@code WRITE} or {@code READ_WRITE}
     */
    public String getAccess() {
        return access;
    }

    /**
     * @return whether the function may read the table
     */
    public boolean isRead() {
        return !"WRITE".equals(access);
    }

    /**
     * @return whether the function may write the table
     */
    public boolean isWrite() {
        return !"READ".equals(access);
    }

    /**
     * @return {@code true} for a table outside the project, used without name prefix
     */
    public boolean isExisting() {
        return existing;
    }

    /**
     * @return a name usable in Terraform resource names: lowercase letters, digits and underscores
     */
    public String getId() {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_");
    }

    /**
     * @return the environment variable the runtime reads the table's deployed name from, e.g.
     *         {@code CLOUDHOPPER_TABLE_ACCOUNTS_NAME} (configuration property {@code cloudhopper.table.accounts.name})
     */
    public String getNameEnvironmentName() {
        return ("cloudhopper.table." + name + ".name").replaceAll("[^A-Za-z0-9]", "_").toUpperCase(Locale.ROOT);
    }
}
