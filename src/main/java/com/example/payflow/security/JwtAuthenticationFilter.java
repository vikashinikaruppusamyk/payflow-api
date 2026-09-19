package com.example.payflow.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads "Authorization: Bearer <token>" and, if the token verifies, marks the request as authenticated.
 * A missing or invalid token leaves the request anonymous; Spring Security then rejects it with 401
 * if the endpoint requires authentication. Not a @Component, so it runs only inside the security chain.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    // Read by SecurityErrorHandler to tell the client why its token was not accepted
    static final String TOKEN_ERROR_ATTRIBUTE = "payflow.tokenError";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                AuthenticatedUser user = jwtService.parse(header.substring(BEARER_PREFIX.length()).trim());
                var authentication = new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ExpiredJwtException e) {
                request.setAttribute(TOKEN_ERROR_ATTRIBUTE, "Token has expired; log in again");
            } catch (JwtException | IllegalArgumentException e) {
                request.setAttribute(TOKEN_ERROR_ATTRIBUTE, "Token is invalid");
            }
        }
        chain.doFilter(request, response);
    }
}
