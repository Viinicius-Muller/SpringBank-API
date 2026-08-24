package vinicius.muller.SpringBank.infra;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

// Swagger UI at /swagger-ui.html, raw spec at /v3/api-docs
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "SpringBank API", version = "0.0.1-SNAPSHOT",
                description = "REST banking API: JWT auth, multi-account, deposits, withdrawals and transfers."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
