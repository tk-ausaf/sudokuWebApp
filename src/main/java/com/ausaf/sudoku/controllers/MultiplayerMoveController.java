package com.ausaf.sudoku.controllers;

import com.ausaf.sudoku.config.GuestHandshakeInterceptor;
import com.ausaf.sudoku.dto.MultiplayerMoveRequest;
import com.ausaf.sudoku.security.CallerIdentity;
import com.ausaf.sudoku.service.MultiplayerGameEngine;
import com.ausaf.sudoku.service.MultiplayerMoveRejectedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * STOMP endpoint for submitting a multiplayer move: one cell position and digit, never the whole
 * grid. Caller identity comes from session attributes {@link GuestHandshakeInterceptor} stashed
 * during the WebSocket handshake, not from the message payload.
 */
@Controller
public class MultiplayerMoveController {

    @Autowired
    private MultiplayerGameEngine gameEngine;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Handles a move sent to {@code /app/games/{gameId}/move}. A rejected (malformed/out-of-turn)
     * attempt is reported only to the sender's own private error queue and never ends the game -
     * a real on-turn attempt (right or wrong, or arriving after the deadline) always goes through
     * {@link MultiplayerGameEngine} and can end it.
     */
    @MessageMapping("/games/{gameId}/move")
    public void submitMove(@DestinationVariable String gameId, MultiplayerMoveRequest moveRequest,
                            SimpMessageHeaderAccessor headerAccessor) {
        CallerIdentity identity = identityFrom(headerAccessor);
        try {
            gameEngine.applyMove(gameId, identity, moveRequest.getRow(), moveRequest.getCol(), moveRequest.getValue());
        } catch (MultiplayerMoveRejectedException e) {
            String sessionId = headerAccessor.getSessionId();
            messagingTemplate.convertAndSendToUser(sessionId, "/queue/games/" + gameId + "/errors",
                    e.getMessage(), createHeaders(sessionId));
        }
    }

    /** Reads the identity {@link GuestHandshakeInterceptor} resolved at handshake time from this session's attributes. */
    private CallerIdentity identityFrom(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes != null) {
            Object username = sessionAttributes.get(GuestHandshakeInterceptor.SESSION_ATTR_USERNAME);
            if (username != null) {
                return CallerIdentity.ofUser(username.toString());
            }
            Object anonymousId = sessionAttributes.get(GuestHandshakeInterceptor.SESSION_ATTR_ANONYMOUS_ID);
            if (anonymousId != null) {
                return CallerIdentity.ofGuest(anonymousId.toString());
            }
        }
        return CallerIdentity.ofGuest(null);
    }

    /** Message headers that route a user-destination send to one specific session, per Spring's documented pattern for anonymous WS users. */
    private Map<String, Object> createHeaders(String sessionId) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        if (sessionId != null) {
            headerAccessor.setSessionId(sessionId);
        }
        headerAccessor.setLeaveMutable(true);
        return headerAccessor.getMessageHeaders();
    }
}