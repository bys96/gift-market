package com.giftmarket.auth.jwt;

import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserStatus;
import com.giftmarket.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String accessToken = resolveAccessToken(request);

        if (accessToken != null
                && SecurityContextHolder.getContext().getAuthentication() == null
                && jwtTokenProvider.isValidAccessToken(accessToken)) {

            authenticate(accessToken);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String accessToken) {
        Claims claims = jwtTokenProvider.parseAccessToken(accessToken);

        Long userId = Long.valueOf(claims.getSubject());

        User user = userRepository.findById(userId)
                .orElse(null);

        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        user.getId(),
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_" + user.getRole().name()
                                )
                        )
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(
                HttpHeaders.AUTHORIZATION
        );

        if (authorizationHeader == null
                || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }

        return authorizationHeader.substring(
                BEARER_PREFIX.length()
        );
    }
}