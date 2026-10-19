package com.familyguard;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
class Security {
  @Bean
  BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain chain(
      HttpSecurity http,
      Tokens tokens,
      AdminRepository admins,
      DeviceRepository devices,
      ParentDelivery delivery,
      @Value("${app.origins}") String origins)
      throws Exception {
    var cors = new CorsConfiguration();
    cors.setAllowedOrigins(Arrays.asList(origins.split(",")));
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    cors.setAllowCredentials(true);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    return http.cors(c -> c.configurationSource(source))
        .csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/actuator/health",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/logout",
                        "/api/pairing/complete",
                        "/api/parent-device/register",
                        "/ws")
                    .permitAll()
                    .requestMatchers("/api/device/**")
                    .hasRole("DEVICE")
                    .requestMatchers("/api/parent-device/**")
                    .hasRole("PARENT_DEVICE")
                    .requestMatchers("/api/**")
                    .hasRole("PARENT")
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((q, r, x) -> r.sendError(401))
                    .accessDeniedHandler((q, r, x) -> r.sendError(403)))
        .addFilterBefore(
            new Guard(tokens, admins, devices, delivery, origins),
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  static class Guard extends OncePerRequestFilter {
    final Tokens tokens;
    final AdminRepository admins;
    final DeviceRepository devices;
    final ParentDelivery delivery;
    final Set<String> origins;
    final Map<String, long[]> limits = new ConcurrentHashMap<>();

    Guard(Tokens t, AdminRepository a, DeviceRepository d, ParentDelivery pd, String o) {
      tokens = t;
      admins = a;
      devices = d;
      delivery = pd;
      origins = Set.of(o.split(","));
    }

    protected void doFilterInternal(HttpServletRequest q, HttpServletResponse r, FilterChain chain)
        throws ServletException, IOException {
      String path = q.getRequestURI();
      if (path.startsWith("/api/auth/") && !path.endsWith("/login")) {
        String origin = q.getHeader("Origin");
        if (origin == null || !origins.contains(origin)) {
          r.sendError(403);
          return;
        }
      }
      boolean publicEntry =
          path.equals("/api/auth/login")
              || path.equals("/api/pairing/complete")
              || path.equals("/api/parent-device/register");
      String auth = q.getHeader("Authorization");
      String identity = auth != null ? Tokens.hash(auth) : q.getRemoteAddr();
      String key = (publicEntry ? path + q.getRemoteAddr() : identity);
      long minute = System.currentTimeMillis() / 60000;
      if (limits.size() > 10000) limits.entrySet().removeIf(e -> e.getValue()[0] < minute);
      long[] window = limits.computeIfAbsent(key, k -> new long[] {minute, 0});
      synchronized (window) {
        if (window[0] != minute) {
          window[0] = minute;
          window[1] = 0;
        }
        if (++window[1] > (publicEntry ? 12 : 240)) {
          r.setHeader("Retry-After", "60");
          r.sendError(429);
          return;
        }
      }
      if (Set.of("POST", "PUT").contains(q.getMethod())) {
        byte[] body = q.getInputStream().readNBytes(262145);
        if (body.length > 262144) {
          r.sendError(413);
          return;
        }
        q = new CachedRequest(q, body);
      }
      if (auth != null && auth.startsWith("Bearer "))
        try {
          String token = auth.substring(7);
          if (path.startsWith("/api/device/"))
            devices
                .findByTokenHash(Tokens.hash(token))
                .ifPresent(
                    d ->
                        SecurityContextHolder.getContext()
                            .setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                    d.id.toString(),
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_DEVICE")))));
          else if (path.startsWith("/api/parent-device/")) {
            String hash = Tokens.hash(token);
            delivery
                .authenticate(hash)
                .ifPresent(
                    rp ->
                        SecurityContextHolder.getContext()
                            .setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                    hash,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_PARENT_DEVICE")))));
          } else {
            long v = tokens.verify(token);
            admins
                .findById(1)
                .filter(a -> a.enabled && a.sessionVersion == v)
                .ifPresent(
                    a ->
                        SecurityContextHolder.getContext()
                            .setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                    "parent",
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_PARENT")))));
          }
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ignored) {
        }
      chain.doFilter(q, r);
    }
  }

  static class CachedRequest extends HttpServletRequestWrapper {
    final byte[] body;

    CachedRequest(HttpServletRequest q, byte[] bytes) {
      super(q);
      body = bytes;
    }

    public ServletInputStream getInputStream() {
      var source = new java.io.ByteArrayInputStream(body);
      return new ServletInputStream() {
        public int read() {
          return source.read();
        }

        public int read(byte[] b, int offset, int length) {
          return source.read(b, offset, length);
        }

        public boolean isFinished() {
          return source.available() == 0;
        }

        public boolean isReady() {
          return true;
        }

        public void setReadListener(ReadListener listener) {
          throw new UnsupportedOperationException("Only blocking JSON requests are supported");
        }
      };
    }

    public java.io.BufferedReader getReader() {
      return new java.io.BufferedReader(
          new java.io.InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
    }
  }
}
