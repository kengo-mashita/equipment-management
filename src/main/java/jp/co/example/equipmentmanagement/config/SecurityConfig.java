package jp.co.example.equipmentmanagement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/h2-console/**").permitAll()
                // 備品の登録・編集・削除はADMINのみ。貸出・返却はADMIN/USER共通のため対象外（4.1節）。
                .requestMatchers(HttpMethod.GET, "/equipment/new", "/equipment/*/edit").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/equipment", "/equipment/*/edit", "/equipment/*/delete").hasRole("ADMIN")
                // 社員マスタの登録・編集・削除はADMINのみ。閲覧（貸出時の選択用）はADMIN/USER共通。
                .requestMatchers(HttpMethod.GET, "/employees/new", "/employees/*/edit").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/employees", "/employees/*/edit", "/employees/*/delete").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/equipment", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            // H2コンソールは開発用ツールのため、フレーム表示とCSRF検証の対象外にする。
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
            .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));

        return http.build();
    }
}
