package com.railway.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI railwayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Railway Ticket Booking API")
                        .version("1.0.0")
                        .description("""
                                Backend API for a railway ticket booking system (similar to IRCTC).

                                **Getting Started:**
                                1. Register a new account via `/auth/register`
                                2. Login via `/auth/login` to get a JWT token
                                3. Click the **Authorize** button above and paste the token
                                4. You can now access authenticated endpoints

                                **Booking Flow:** Search trains → Check availability → Book → Pay → Get PNR status

                                **Admin endpoints** require a user with ADMIN role.""")
                        .contact(new Contact()
                                .name("Railway Booking Team")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .bearerFormat("JWT")
                                        .scheme("bearer")
                                        .description("Paste the `accessToken` from the login response")))
                .tags(List.of(
                        new Tag().name("1. Auth").description("Register, login, and refresh JWT tokens (public)"),
                        new Tag().name("2. User").description("User profile management"),
                        new Tag().name("3. Stations").description("Railway station lookup (public)"),
                        new Tag().name("4. Trains").description("Train information and search (public)"),
                        new Tag().name("5. Train Search").description("Search trains by route and date via Elasticsearch (public)"),
                        new Tag().name("6. Availability").description("Check seat availability for a train segment"),
                        new Tag().name("7. Bookings").description("Book tickets, view bookings, cancel bookings"),
                        new Tag().name("8. PNR Status").description("Check PNR status with passenger details"),
                        new Tag().name("9. Payments").description("Initiate and manage payments for bookings"),
                        new Tag().name("10. Admin - Trains").description("Admin: manage stations, trains, routes, schedules"),
                        new Tag().name("11. Admin - Bookings").description("Admin: generate train runs, manage bookings"),
                        new Tag().name("12. Admin - Search").description("Admin: Elasticsearch index management"),
                        new Tag().name("13. Admin - Scheduler").description("Admin: manually trigger scheduled jobs")
                ));
    }
}
