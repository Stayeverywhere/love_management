package com.lovemanagement.config;

import com.lovemanagement.domain.AppRole;
import com.lovemanagement.repo.AppRoleRepository;
import com.lovemanagement.repo.UserAccountRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UserAccountRepository repository,
                                                  AppRoleRepository roleRepository) {
        return username -> repository.findByUsername(username)
                .map(account -> {
                    String code = account.getAccountRole().name();
                    // 该账号所属角色被禁用时拒绝登录
                    AppRole role = roleRepository.findByCode(code).orElse(null);
                    if (role != null && !role.isEnabled()) {
                        throw new UsernameNotFoundException("该账号所属角色已被禁用");
                    }
                    return User.withUsername(account.getUsername())
                            .password(account.getPasswordHash())
                            .roles(code)
                            .build();
                })
                .orElseThrow(() -> new UsernameNotFoundException("账号不存在"));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/error").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/pm/**").hasAnyRole("ADMIN", "PROJECT_MANAGER")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/home", true)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID"))
                .exceptionHandling(ex -> ex.accessDeniedPage("/denied"));
        return http.build();
    }
}
