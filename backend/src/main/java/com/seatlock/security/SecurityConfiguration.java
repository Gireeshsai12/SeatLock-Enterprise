package com.seatlock.security;

import com.seatlock.identity.IdentityService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwords() { return new BCryptPasswordEncoder(10); }

    @Bean
    @ConditionalOnWebApplication
    SecurityFilterChain web(HttpSecurity http, Tokens tokens, IdentityService identity,
            ObjectProvider<ClientRegistrationRepository> google) throws Exception {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        http.csrf(csrf -> csrf.disable())
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .securityContext(ctx -> ctx.securityContextRepository(new NullSecurityContextRepository()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/readiness", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                    "/api/v1/auth/**", "/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/events/**").permitAll()
                .requestMatchers("/api/v1/admin/**", "/actuator/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((req,res,err) -> problem(res,401,"Authentication required."))
                .accessDeniedHandler((req,res,err) -> problem(res,403,"Administrator permission required.")))
            .oauth2ResourceServer(resource -> resource
                .authenticationEntryPoint((req,res,err) -> problem(res,401,"Invalid or missing access token."))
                .jwt(jwt -> jwt.decoder(tokens.decoder()).jwtAuthenticationConverter(converter)));
        if (google.getIfAvailable() != null) {
            http.oauth2Login(login -> login.successHandler((request,response,authentication) -> {
                if (!(authentication.getPrincipal() instanceof OidcUser user)
                        || !Boolean.TRUE.equals(user.getEmailVerified())) {
                    response.sendRedirect("/#oauth-error");
                    return;
                }
                try {
                    String token = tokens.issue(identity.google(user.getSubject(), user.getEmail()));
                    response.sendRedirect("/#access_token=" + token);
                } catch (com.seatlock.shared.ApiError error) {
                    response.sendRedirect("/#oauth-error");
                }
            }).failureHandler((req,res,err) -> res.sendRedirect("/#oauth-error")));
        }
        return http.build();
    }

    private static void problem(HttpServletResponse response, int status, String detail)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
    }
}
