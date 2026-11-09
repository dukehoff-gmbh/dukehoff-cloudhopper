package eu.cloudhopper.mc.deployment.config.generator;

/*-
 * #%L
 * Cloudhopper: Deployment Configuration Processor - a library from the "Cloudhopper" project.
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

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import eu.cloudhopper.mc.generator.api.GeneratorFeatureInfo;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

/**
 * Runs {@link AnnotationFeatureValidator} for a target that, like springboot-http, supports
 * {@code @Function(name)} and {@code @HttpTrigger} but no schedules (#14).
 */
public class AnnotationFeatureValidatorTest {

    @SupportedAnnotationTypes("eu.cloudhopper.mc.annotations.Function")
    @SupportedOptions(AnnotationFeatureValidator.STRICT_OPTION)
    static class ValidatingProcessor extends AbstractProcessor {

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
            AnnotationFeatureValidator validator = new AnnotationFeatureValidator(processingEnv, httpOnlyTarget());
            for (TypeElement annotation : annotations) {
                for (Element element : round.getElementsAnnotatedWith(annotation)) {
                    validator.validate(element);
                }
            }
            return false;
        }
    }

    private static GeneratorFeatureInfo httpOnlyTarget() {
        GeneratorFeatureInfo.FeatureEntry function = new GeneratorFeatureInfo.FeatureEntry();
        function.setSupportedAnnotation("eu.cloudhopper.mc.annotations.Function");
        function.setSupportedAttributes(List.of("name"));
        GeneratorFeatureInfo.FeatureEntry http = new GeneratorFeatureInfo.FeatureEntry();
        http.setSupportedAnnotation("eu.cloudhopper.mc.annotations.HttpTrigger");
        http.setSupportedAttributes(List.of("summary", "description", "operationId", "path", "method"));
        GeneratorFeatureInfo info = new GeneratorFeatureInfo();
        info.setGeneratorId("http-only");
        info.setFeatures(List.of(function, http));
        return info;
    }

    private static Compilation compile(String body, String... options) {
        JavaFileObject source = JavaFileObjects.forSourceString("com.example.Service", ""
                + "package com.example;\n"
                + "import eu.cloudhopper.mc.annotations.*;\n"
                + "public class Service {\n"
                + body
                + "}\n");
        return javac().withOptions((Object[]) options).withProcessors(new ValidatingProcessor()).compile(source);
    }

    @Test
    public void supportedAnnotationsAndExtensionsPassWithoutWarnings() {
        Compilation compilation = compile(""
                + "  @Function(name = \"hello\", extensions = @Extension(name = \"x-aws-architecture\", value = \"ARM64\"))\n"
                + "  @HttpTrigger(method = \"GET\", path = \"/hello\")\n"
                + "  public String hello() { return \"hi\"; }\n");

        assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    public void unsupportedTriggerIsReported() {
        Compilation compilation = compile(""
                + "  @Function(name = \"tick\")\n"
                + "  @ScheduledTrigger(cron = \"* * * * *\")\n"
                + "  public void tick() { }\n");

        assertThat(compilation).succeeded();
        assertThat(compilation).hadWarningContaining(
                "@ScheduledTrigger is not supported by generator 'http-only'; it is ignored for this target.");
    }

    @Test
    public void unsupportedAttributeIsReported() {
        Compilation compilation = compile(""
                + "  @Function(name = \"big\", memory = 1024)\n"
                + "  @HttpTrigger(method = \"GET\", path = \"/big\")\n"
                + "  public String big() { return \"\"; }\n");

        assertThat(compilation).succeeded();
        assertThat(compilation).hadWarningContaining(
                "Attribute 'memory' of @Function is not supported by generator 'http-only'");
    }

    @Test
    public void strictModeTurnsFindingsIntoErrors() {
        Compilation compilation = compile(""
                + "  @Function(name = \"tick\")\n"
                + "  @ScheduledTrigger(cron = \"* * * * *\")\n"
                + "  public void tick() { }\n",
                "-A" + AnnotationFeatureValidator.STRICT_OPTION + "=true");

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("@ScheduledTrigger is not supported by generator 'http-only'");
    }
}
