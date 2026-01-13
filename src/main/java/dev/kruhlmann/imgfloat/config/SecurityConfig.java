package dev.kruhlmann.imgfloat.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        OAuth2AuthorizedClientRepository authorizedClientRepository
    ) throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName("_csrf");

        http
            .authorizeHttpRequests((auth) ->
                auth
                    .requestMatchers(
                        "/",
                        "/favicon.ico",
                        "/img/**",
                        "/css/**",
                        "/js/**",
                        "/webjars/**",
                        "/actuator/health",
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/channels",
                        "/terms",
                        "/privacy",
                        "/cookies"
                    )
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/view/*/broadcast")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/channels")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/channels/*/assets/visible")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/channels/*/assets")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/channels/*/canvas")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/channels/gasolinebased/script-assets/*/attachments/*/content")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/channels/*/assets/*/content")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/channels/*/assets/*/preview")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/twitch/emotes/**")
                    .permitAll()
                    .requestMatchers("/ws/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            )
            .oauth2Login((oauth) ->
                oauth
                    .authorizedClientRepository(authorizedClientRepository)
                    .tokenEndpoint((token) -> token.accessTokenResponseClient(twitchAccessTokenResponseClient()))
                    .userInfoEndpoint((user) -> user.userService(twitchOAuth2UserService()))
            )
            .logout((logout) -> logout.logoutSuccessUrl("/").permitAll())
            .exceptionHandling((exceptions) ->
                exceptions
                    .defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        new AntPathRequestMatcher("/api/**")
                    )
                    .accessDeniedHandler(csrfAccessDeniedHandler())
            )
            .csrf((csrf) ->
                csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(csrfRequestHandler)
                    .ignoringRequestMatchers("/ws/**")
            )
            .addFilterAfter(csrfTokenCookieFilter(), CsrfFilter.class);
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

    private AccessDeniedHandler csrfAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            if (accessDeniedException instanceof CsrfException) {
                LOG.warn(
                    "CSRF failure for {} {} - referer: {}, origin: {}, message: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getHeader("Referer"),
                    request.getHeader("Origin"),
                    accessDeniedException.getMessage()
                );
            }
            response.sendError(HttpServletResponse.SC_FORBIDDEN, accessDeniedException.getMessage());
        };
    }

    /**
     * Ensure the XSRF-TOKEN cookie is always present for browser clients and mirror the current
     * token value on every request. This helps client-side fetch calls include the token header and
     * aids debugging when tokens are missing.
     */
    @Bean
    OncePerRequestFilter csrfTokenCookieFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
            ) throws java.io.IOException, jakarta.servlet.ServletException {
                CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
                if (csrfToken == null) {
                    csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                }
                if (csrfToken != null) {
                    String token = csrfToken.getToken();
                    Cookie existingCookie = WebUtils.getCookie(request, "XSRF-TOKEN");
                    if (existingCookie == null || !token.equals(existingCookie.getValue())) {
                        Cookie cookie = new Cookie("XSRF-TOKEN", token);
                        cookie.setPath("/");
                        cookie.setSecure(request.isSecure());
                        cookie.setHttpOnly(false);
                        response.addCookie(cookie);
                    }
                }
                filterChain.doFilter(request, response);
            }
        };
    }
}
