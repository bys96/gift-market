package com.giftmarket.auth.jwt;

import com.giftmarket.user.entity.User;
import com.giftmarket.user.entity.UserRole;
import com.giftmarket.user.entity.UserStatus;
import com.giftmarket.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock UserRepository userRepository;
    @Mock FilterChain filterChain;
    @Mock User user;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeUserAccessTokenCreatesAuthenticationAfterDatabaseStatusCheck() throws Exception {
        prepareToken(UserStatus.ACTIVE);
        given(user.getId()).willReturn(1L);
        given(user.getRole()).willReturn(UserRole.USER);

        execute();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(1L);
    }

    @Test
    void suspendedUserAccessTokenDoesNotCreateAuthentication() throws Exception {
        prepareToken(UserStatus.SUSPENDED);

        execute();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void withdrawnUserAccessTokenDoesNotCreateAuthentication() throws Exception {
        prepareToken(UserStatus.WITHDRAWN);

        execute();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private void prepareToken(UserStatus status) {
        Claims claims = mock(Claims.class);
        given(jwtTokenProvider.isValidAccessToken("access-token")).willReturn(true);
        given(jwtTokenProvider.parseAccessToken("access-token")).willReturn(claims);
        given(claims.getSubject()).willReturn("1");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(user.getStatus()).willReturn(status);
    }

    private void execute() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}
