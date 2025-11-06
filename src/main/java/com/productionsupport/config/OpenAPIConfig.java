package com.productionsupport.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration
 */
@Configuration
public class OpenAPIConfig {

    @Value("${server.port:8093}")
    private String serverPort;

    @Bean
    public OpenAPI productionSupportOpenAPI() {
        Server devServer = new Server();
        devServer.setUrl("http://localhost:" + serverPort);
        devServer.setDescription("Development server");

        Server prodServer = new Server();
        prodServer.setUrl("https://xxx.apigtw.com/production-support");
        prodServer.setDescription("Production server");

        Contact contact = new Contact();
        contact.setName("Production Support Team");
        contact.setEmail("production-support@example.com");

        License license = new License()
            .name("MIT License")
            .url("https://opensource.org/licenses/MIT");

        Info info = new Info()
            .title("Production Support API")
            .version("1.0.0")
            .description("Operational automation assistant that converts natural language queries into structured, " +
                        "validated API operations with built-in runbooks and safety checks. " +
                        "Supports operations like case cancellation, status updates, and more.")
            .contact(contact)
            .license(license);

        return new OpenAPI()
            .info(info)
            .servers(List.of(devServer, prodServer));
    }
}

