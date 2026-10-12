package com.shopwise.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI shopwiseOpenApi() {
        final String scheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Shopwise Commerce & Assistant API")
                        .version("1.0.0")
                        .description("""
                                REST contract for the Shopwise storefront, cart, checkout and the
                                Spring AI powered shopping assistant.

                                Authentication is a bearer JWT issued by `/api/v1/auth/login`.
                                Guest carts and guest checkout are supported without a token; the
                                assistant additionally accepts an `X-Guest-Token` header so that a
                                signed-out shopper keeps conversation and cart context.
                                """)
                        .contact(new Contact().name("Shopwise Platform").email("platform@shopwise.dev"))
                        .license(new License().name("MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development")))
                .components(new Components().addSecuritySchemes(scheme,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the `token` returned by /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(scheme));
    }
}
