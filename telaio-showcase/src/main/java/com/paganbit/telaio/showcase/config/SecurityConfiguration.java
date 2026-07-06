package com.paganbit.telaio.showcase.config;

import com.paganbit.telaio.showcase.role.UserRole;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Optional;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll();
                auth.requestMatchers("/actuator/**").permitAll();
                auth.anyRequest().authenticated();
            });
        return http.build();
    }

    @Bean
    @SuppressWarnings("squid:S6437")
    UserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails developer = User.withUsername("developer")
            .password(encoder.encode("developer"))
            .authorities(UserRole.DEVELOPER)
            .build();
        UserDetails admin = User.withUsername("admin")
            .password(encoder.encode("admin"))
            .authorities(UserRole.ADMIN)
            .build();
        UserDetails user = User.withUsername("user")
            .password(encoder.encode("user"))
            .authorities(UserRole.USER)
            .build();
        return new InMemoryUserDetailsManager(developer, admin, user);
    }

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                return Optional.of("system");
            }
            return Optional.of(auth.getName());
        };
    }
}
