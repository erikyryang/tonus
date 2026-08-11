package br.com.tonus.notification.internal.web;

import br.com.tonus.notification.internal.SessionState;
import br.com.tonus.notification.internal.WhatsAppSessionGateway;
import java.util.List;

/** Wrapped in an object, not a bare array: the listing gains fields without breaking the front. */
public record WhatsAppInstancesResponse(List<Instance> instances) {

    public static WhatsAppInstancesResponse from(List<WhatsAppSessionGateway.GatewaySession> sessions) {
        return new WhatsAppInstancesResponse(sessions.stream()
                .map(session -> new Instance(session.name(), session.state()))
                .toList());
    }

    /** No phone here: choosing an instance does not require knowing whose number it is. */
    public record Instance(String name, SessionState state) {
    }
}
