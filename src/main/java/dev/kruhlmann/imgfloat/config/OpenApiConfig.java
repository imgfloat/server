package dev.kruhlmann.imgfloat.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String TWITCH_OAUTH_SCHEME = "twitchOAuth";

    @Bean
    public OpenAPI imgfloatOpenAPI() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(TWITCH_OAUTH_SCHEME, twitchOAuthScheme()))
                .addSecurityItem(new SecurityRequirement().addList(TWITCH_OAUTH_SCHEME))
                .info(new Info()
                        .title("Imgfloat API")
                        .description("OpenAPI documentation for Imgfloat admin and broadcaster APIs.")
                        .version("v1"));
    }

    private SecurityScheme twitchOAuthScheme() {
        return new SecurityScheme()
                .name(TWITCH_OAUTH_SCHEME)
                .type(SecurityScheme.Type.OAUTH2)
                .flows(new OAuthFlows()
                        .authorizationCode(new OAuthFlow()
                                .authorizationUrl("https://id.twitch.tv/oauth2/authorize")
                                .tokenUrl("https://id.twitch.tv/oauth2/token")));
    }
}
