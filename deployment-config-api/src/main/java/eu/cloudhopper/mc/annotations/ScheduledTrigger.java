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
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;

/**
 * Declares that the annotated function should be executed on a schedule.
 *
 * <p>This annotation can be used in combination with {@link Function} to define
 * time-based triggers using cron expressions. It enables the generation of scheduled
 * tasks for serverless platforms that support them (e.g. AWS EventBridge, GCP Cloud Scheduler).</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @Function(name = "dailyTask")
 * @ScheduledTrigger(cron = "0 0 * * *", timezone = "Europe/Berlin")
 * public Void runDailyTask() {
 *     // perform daily task
 *     return null;
 * }
 * }</pre>
 *
 */
@Retention(SOURCE)
@Target(ElementType.METHOD)
public @interface ScheduledTrigger {

    /**
     * A cron expression defining when the function should run.
     * <p>Uses standard Unix cron syntax (e.g., {@code "0 0 * * *"} for daily at midnight).</p>
     *
     * @return cron expression
     */
    String cron();

    /**
     * The IANA time zone in which the cron expression should be evaluated.
     * <p>Defaults to {@code "UTC"} if not specified.</p>
     *
     * @return time zone ID (e.g., "UTC", "Europe/Berlin")
     */
    String timezone() default "UTC";

    /**
     * Constants representing attribute names of {@link ScheduledTrigger}.
     *
     * <p>These are intended for use by code generators when declaring which
     * attributes they support via {@code @GeneratorFeature}.</p>
     *
     * <h3>Example usage:</h3>
     * <pre>{@code
     * @GeneratorFeature(
     *     supportedAnnotation = ScheduledTrigger.class,
     *     supportedAttributes = {
     *         ScheduleAttribute.CRON,
     *         ScheduleAttribute.TIMEZONE
     *     }
     * )
     * }</pre>
     *
     * <p>This class is not intended for application developers and can be ignored
     * in function code.</p>
     */
    class ScheduleAttribute {
        public static final String CRON = "cron";
        public static final String TIMEZONE = "timezone";
    }
}