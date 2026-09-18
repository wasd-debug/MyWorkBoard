package com.salarytracker.platform;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@OpenAPIDefinition(info = @Info(title = "Salary Tracker API", version = "v1", description = "多用户工时与真实时薪 API"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, bearerFormat = "JWT", scheme = "bearer", in = SecuritySchemeIn.HEADER)
public class OpenApiConfig {
    private static final Map<String, String> STANDARD_ERRORS = new LinkedHashMap<>();

    static {
        STANDARD_ERRORS.put("400", "Invalid request");
        STANDARD_ERRORS.put("401", "Authentication required");
        STANDARD_ERRORS.put("403", "Operation forbidden");
        STANDARD_ERRORS.put("404", "Resource not found");
        STANDARD_ERRORS.put("405", "Method not allowed");
        STANDARD_ERRORS.put("409", "Revision or state conflict");
        STANDARD_ERRORS.put("410", "Sync cursor expired and requires reset");
        STANDARD_ERRORS.put("500", "Unexpected server error");
    }

    @Bean
    OpenApiCustomizer apiProblemResponses() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new io.swagger.v3.oas.models.Components());
            }
            var resolved = ModelConverters.getInstance()
                    .resolveAsResolvedSchema(new AnnotatedType(ApiProblem.class));
            resolved.referencedSchemas.forEach(openApi.getComponents()::addSchemas);
            openApi.getComponents().addSchemas("ApiProblem", resolved.schema);

            if (openApi.getPaths() == null) return;
            openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation ->
                    STANDARD_ERRORS.forEach((status, description) -> {
                        if (operation.getResponses().containsKey(status)) return;
                        Content content = new Content().addMediaType(
                                org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiProblem")));
                        operation.getResponses().addApiResponse(status,
                                new ApiResponse().description(description).content(content));
                    })));
        };
    }
}
