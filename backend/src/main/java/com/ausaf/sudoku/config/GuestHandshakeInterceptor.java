package com.ausaf.sudoku.config;

import com.ausaf.sudoku.security.GuestSessionFilter;
import com.ausaf.sudoku.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
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
 * the same way {@link com.ausaf.sudoku.security.JwtAuthenticationFilter}/{@link GuestSessionFilter}
 * do for REST calls, and stashes it into the WebSocket session attributes so
 * {@code MultiplayerMoveController} can read it per message. A guest id arrives as the
 * {@value #GUEST_ID_QUERY_PARAM} query parameter rather than the {@code X-Guest-Id} header
 * {@link GuestSessionFilter} reads for REST calls, since a browser's raw WebSocket handshake
 * can't carry custom headers - a query parameter is the one thing every transport can deliver.
 */
@Slf4j
@Component
public class GuestHandshakeInterceptor implements HandshakeInterceptor {

    public static final String SESSION_ATTR_USERNAME = "callerUsername";
    public static final String SESSION_ATTR_ANONYMOUS_ID = "callerAnonymousId";
    public static final String GUEST_ID_QUERY_PARAM = "guestId";

    @Autowired
    private JwtUtil jwtUtil;

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
            String username = jwtUtil.getUsernameFromToken(token);
            attributes.put(SESSION_ATTR_USERNAME, username);
            log.debug("WebSocket handshake resolved user '{}'", username);
            return true;
        }

        String anonymousId = httpRequest.getParameter(GUEST_ID_QUERY_PARAM);
        if (anonymousId != null && !anonymousId.isBlank()) {
            attributes.put(SESSION_ATTR_ANONYMOUS_ID, anonymousId);
            log.debug("WebSocket handshake resolved guest:{}", anonymousId);
        } else {
            log.warn("WebSocket handshake resolved no identity (no bearer token or {} param)", GUEST_ID_QUERY_PARAM);
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // No cleanup needed - session attributes are discarded with the WebSocket session itself.
    }
}