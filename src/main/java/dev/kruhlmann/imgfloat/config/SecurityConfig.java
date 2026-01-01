package dev.kruhlmann.imgfloat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpStatus;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            OAuth2AuthorizedClientRepository authorizedClientRepository) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/",
                        "/css/**",
                        "/js/**",
                        "/webjars/**",
                        "/actuator/health",
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/channels"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/view/*/broadcast").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/channels").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/channels/*/assets/visible").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/channels/*/canvas").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/channels/*/assets/*/content").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth -> oauth
                .authorizedClientRepository(authorizedClientRepository)
                .tokenEndpoint(token -> token.accessTokenResponseClient(twitchAccessTokenResponseClient()))
                .userInfoEndpoint(user -> user.userService(twitchOAuth2UserService())))
            .logout(logout -> logout.logoutSuccessUrl("/").permitAll())
            .exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    new AntPathRequestMatcher("/api/**")
                ))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/ws/**", "/api/**"));
        return http.build();
    }

    @Bean
    OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> twitchAccessTokenResponseClient() {
        DefaultAuthorizationCodeTokenResponseClient delegate = new DefaultAuthorizationCodeTokenResponseClient();
        delegate.setRequestEntityConverter(new TwitchAuthorizationCodeGrantRequestEntityConverter());
        RestTemplate restTemplate = OAuth2RestTemplateFactory.create();
        restTemplate.setErrorHandler(new TwitchOAuth2ErrorResponseErrorHandler());
        delegate.setRestOperations(restTemplate);
        return delegate;
    }

    @Bean
    TwitchOAuth2UserService twitchOAuth2UserService() {
        return new TwitchOAuth2UserService();
    }
}
