package com.justjava.humanresource.communication.config;

import com.justjava.humanresource.hr.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EmployeePrincipalHandshakeHandler extends DefaultHandshakeHandler {

    private final EmployeeRepository employeeRepository;

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Principal principal = request.getPrincipal();
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof DefaultOidcUser oidcUser) {
            Object email = oidcUser.getClaims().get("email");
            if (email != null) {
                return employeeRepository.findByEmail(String.valueOf(email))
                        .<Principal>map(employee -> employee::getEmployeeNumber)
                        .orElse(() -> String.valueOf(email));
            }
        }
        return principal;
    }
}
