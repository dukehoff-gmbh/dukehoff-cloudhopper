package eu.cloudhopper.mc.deployment.config.generator;

/*-
 * #%L
 * deployment-config-processor - a library from the "Cloudhopper" project.
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
import com.palantir.javapoet.JavaFile;
import eu.cloudhopper.mc.deployment.config.generator.HandlerGenerator.MethodHandlerInfo;
import eu.cloudhopper.mc.deployment.config.generator.HandlerGenerator.ParamInfo;
import static eu.cloudhopper.mc.deployment.config.generator.HandlerGenerator.ParamInfo.ParamType.PATH;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HandlerGeneratorTest {

    @Test
    public void testGenerateHandlerFile_WithBodyOnly() throws Exception {
        // Arrange: simple service method: String echo(String input)
        HandlerGenerator.ParamInfo bodyParam = new HandlerGenerator.ParamInfo(
                HandlerGenerator.ParamInfo.ParamType.BODY,
                null,
                "java.lang.String",
                "_body"
        );
        HandlerGenerator.MethodHandlerInfo info = new HandlerGenerator.MethodHandlerInfo(
                "com.example",
                "ExampleService_echo_Handler",
                "com.example.ExampleService",
                "echo",
                "java.lang.String", // body type
                "java.lang.String", // return type
                Collections.singletonList(bodyParam)
        );

        // Act
        JavaFile javaFile = HandlerGenerator.generateHandlerFile(info);
        String source = javaFile.toString();

        // Assert
        assertTrue(source.contains("public class ExampleService_echo_Handler"),
                "Generated class declaration missing, source:\n" + source);
        assertTrue(source.contains("implements CloudRequestHandler<String, String>"),
                "Generated implements missing, source:\n" + source);
        assertTrue(source.contains("private final ExampleService delegate = new ExampleService()"),
                "Generated delegate field missing, source:\n" + source);
        assertTrue(source.contains("public String handleRequest("),
                "Generated handleRequest signature missing, source:\n" + source);
        assertTrue(source.contains("String _body"),
                "Generated body param missing, source:\n" + source);
        assertTrue(source.contains("Map<String, String> _path"),
                "Generated path param placeholder missing, source:\n" + source);
        assertTrue(source.contains("Map<String, String> _query"),
                "Generated query param placeholder missing, source:\n" + source);
        assertTrue(source.contains("HandlerContext _ctx"),
                "Generated context param missing, source:\n" + source);
        assertTrue(source.contains("return delegate.echo(_body)"),
                "Generated invocation missing, source:\n" + source);
    }

    @Test
    public void testGenerateHandlerFile_WithPathAndBody() throws Exception {
        // Arrange: service method: String getItem(String id, Integer count)
        HandlerGenerator.ParamInfo pathParam = new HandlerGenerator.ParamInfo(
                HandlerGenerator.ParamInfo.ParamType.PATH,
                "id",
                "java.lang.String",
                "idParam"
        );
        HandlerGenerator.ParamInfo bodyParam = new HandlerGenerator.ParamInfo(
                HandlerGenerator.ParamInfo.ParamType.BODY,
                null,
                "java.lang.Integer",
                "_body"
        );
        List<HandlerGenerator.ParamInfo> params = List.of(pathParam, bodyParam);
        HandlerGenerator.MethodHandlerInfo info = new HandlerGenerator.MethodHandlerInfo(
                "com.example",
                "ExampleService_getItem_Handler",
                "com.example.ExampleService",
                "getItem",
                "java.lang.Integer",
                "java.lang.String",
                params
        );

        // Act
        JavaFile javaFile = HandlerGenerator.generateHandlerFile(info);
        String source = javaFile.toString();

        // Assert
        assertTrue(source.contains("String idParam = _path.get(\"id\")"),
                "Generated code doesn't contain expected path extraction, source is: " + source);
        assertTrue(source.contains("return delegate.getItem(idParam, _body)"),
                "Generated invocation missing, source:\n" + source);
    }

    @Test
    public void testGenerateHandlerFile_WithQueryAndBody() throws Exception {
        // Arrange: service method: List<String> search(String q, String body)
        HandlerGenerator.ParamInfo queryParam = new HandlerGenerator.ParamInfo(
                HandlerGenerator.ParamInfo.ParamType.QUERY,
                "q",
                "java.lang.String",
                "qParam"
        );
        HandlerGenerator.ParamInfo bodyParam = new HandlerGenerator.ParamInfo(
                HandlerGenerator.ParamInfo.ParamType.BODY,
                null,
                "java.lang.String",
                "_body"
        );
        List<HandlerGenerator.ParamInfo> paramsQuery = List.of(queryParam, bodyParam);
        HandlerGenerator.MethodHandlerInfo infoQuery = new HandlerGenerator.MethodHandlerInfo(
                "com.example",
                "ExampleService_search_Handler",
                "com.example.ExampleService",
                "search",
                "java.lang.String",
                "java.util.List<java.lang.String>",
                paramsQuery
        );

        // Act
        JavaFile javaFile = HandlerGenerator.generateHandlerFile(infoQuery);
        String source = javaFile.toString();

        // Assert
        assertTrue(source.contains("String qParam = _query.get(\"q\")"),
                "Generated code doesn't contain expected query extraction, source is: " + source);
        assertTrue(source.contains("return delegate.search(qParam, _body)"),
                "Generated invocation missing, source:\n" + source);
    }

    @Test
    public void testGenerateHandlerFile_WithContextParam() throws Exception {
        // Arrange: service method: void withContext(HandlerContext ctx)
        HandlerGenerator.ParamInfo contextParam = new HandlerGenerator.ParamInfo(
                HandlerGenerator.ParamInfo.ParamType.CONTEXT,
                null,
                "eu.cloudhopper.mc.runtime.HandlerContext",
                "ctxParam"
        );
        List<HandlerGenerator.ParamInfo> paramsCtx = List.of(contextParam);
        HandlerGenerator.MethodHandlerInfo infoCtx = new HandlerGenerator.MethodHandlerInfo(
                "com.example",
                "ExampleService_context_Handler",
                "com.example.ExampleService",
                "withContext",
                "void",
                "void",
                paramsCtx
        );

        // Act
        JavaFile javaFile = HandlerGenerator.generateHandlerFile(infoCtx);
        String source = javaFile.toString();

        // Assert
        assertTrue(source.contains("HandlerContext ctxParam = _ctx"),
                "Generated code doesn't contain expected context assignment, source is: " + source);
        assertTrue(source.contains("delegate.withContext(ctxParam)"),
                "Generated invocation missing, source:\n" + source);
        assertTrue(source.contains("return null"),
                "Generated return statement missing, source:\n" + source);
    }

    @Test
    public void testGenerateHandlerFile_PathParamPrimitive() throws Exception {
        ParamInfo p = new ParamInfo(PATH, "id", "int", "id");
        MethodHandlerInfo info = new MethodHandlerInfo(
                "com.example", "Foo_id_Handler",
                "com.example.Foo", "foo",
                "java.lang.Void", "java.lang.Void",
                List.of(p)
        );
        String source = HandlerGenerator.generateHandlerFile(info).toString();
        assertTrue(source.contains("int id = ParamConverters.toInteger(_path, \"id\");"),
                "ID conversion missing:\n" + source);
    }
}
