package com.ausaf.sudoku.config;

import com.ausaf.sudoku.security.GuestCookieService;
import com.ausaf.sudoku.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Resolves the caller's identity during the STOMP handshake (a plain HTTP request under SockJS),
 * the same way {@link com.ausaf.sudoku.security.JwtAuthenticationFilter}/
 * {@link com.ausaf.sudoku.security.GuestSessionFilter} do for REST calls, and stashes it into the
 * WebSocket session attributes so {@code MultiplayerMoveController} can read it per message. Only
 * reads an existing guest cookie - never mints one - since the REST create/join calls that always
 * precede opening the socket already establish it.
 */
@Component
public class GuestHandshakeInterceptor implements HandshakeInterceptor {

    public static final String SESSION_ATTR_USERNAME = "callerUsername";
    public static final String SESSION_ATTR_ANONYMOUS_ID = "callerAnonymousId";

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private GuestCookieService guestCookieService;

    /** Stashes a resolved username or anonymous id into the session attributes; never rejects the handshake. */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return true;
        }
        HttpServletRequest httpRequest = servletRequest.getServletRequest();

        String token = jwtUtil.extractBearerToken(httpRequest);
        if (token != null && jwtUtil.isUserToken(token)) {
            attributes.put(SESSION_ATTR_USERNAME, jwtUtil.getUsernameFromToken(token));
            return true;
        }

        String anonymousId = guestCookieService.extractGuestId(httpRequest);
        if (anonymousId != null) {
            attributes.put(SESSION_ATTR_ANONYMOUS_ID, anonymousId);
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // No cleanup needed - session attributes are discarded with the WebSocket session itself.
    }
}