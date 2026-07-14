# URL Shortener

A Spring Boot-based URL shortener with user accounts, private/public links, expiry support, and a Thymeleaf-based web UI. The application stores link metadata in PostgreSQL, uses Redis for fast lookup/caching, and applies database changes through Flyway.

## Architecture at a glance

The project follows a simple layered structure:

- Presentation layer: controllers and Thymeleaf templates under the web package render the UI and handle user requests.
- Application/domain layer: services contain the business rules for creating links, resolving redirects, enforcing access rules, and managing users.
- Data layer: JPA repositories persist entities to PostgreSQL, while Redis caches short-link lookups for faster access.
- Infrastructure: Spring Security handles authentication and authorization, and Docker Compose provides local PostgreSQL and Redis services.

## Main flow

1. A user submits a long URL through the home page.
2. The service generates a short key, validates the URL, and stores the record.
3. The redirect endpoint resolves the short key and sends the user to the original URL.
4. Private links are restricted based on ownership, and public links remain accessible.

## Tech stack

- Java 21
- Spring Boot 3.5
- Spring MVC, Spring Security, Spring Data JPA
- PostgreSQL
- Redis
- Flyway
- Thymeleaf + Bootstrap
- Docker Compose

## Project structure

- src/main/java/com/rishavdev/UrlShortener/config — configuration classes
- src/main/java/com/rishavdev/UrlShortener/domain — entities, repositories, services, DTOs, and exceptions
- src/main/java/com/rishavdev/UrlShortener/web — controllers, form DTOs, and exception handling
- src/main/resources/templates — HTML templates for the UI
- src/main/resources/db/migration — Flyway SQL migration scripts

## Running locally

Prerequisites:
- Java 21
- Maven
- Docker Desktop

Steps:

1. Start the supporting services:
   ```bash
   docker compose up -d
   ```
2. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```
3. Open the app at:
   ```text
   http://localhost:8080
   ```

Useful local services:
- PostgreSQL: localhost:5432
- Redis: localhost:6379
- pgAdmin: http://localhost:5050

## Notes

- The default app configuration expects PostgreSQL and Redis to be available locally.
- Flyway automatically applies database migrations on startup.
- The application includes basic authentication flows for registration, login, and user-specific URL management.
