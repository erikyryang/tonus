package br.com.tonus.notification.internal;

import java.util.List;
import java.util.Optional;

/**
 * Stands in when base-url or api-key are missing.
 *
 * A bean that always fails, rather than no bean at all: the endpoints exist
 * either way, and the screen needs to be told that the deployment is
 * incomplete — a 404 would look like a product that does not have the feature.
 */
class NotConfiguredSessionGateway implements WhatsAppSessionGateway {

    @Override
    public List<GatewaySession> instances() {
        throw notConfigured();
    }

    @Override
    public Optional<GatewaySession> find(String instance) {
        throw notConfigured();
    }

    @Override
    public String connect(String instance) {
        throw notConfigured();
    }

    @Override
    public void logout(String instance) {
        throw notConfigured();
    }

    private WhatsAppGatewayNotConfiguredException notConfigured() {
        return new WhatsAppGatewayNotConfiguredException(
                "notification.evolution.base-url e api-key são obrigatórios para administrar a sessão");
    }
}
