package br.com.tonus.notification.internal;

import br.com.tonus.notification.internal.domain.WhatsAppSettings;
import br.com.tonus.notification.internal.domain.WhatsAppSettingsRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only thing that touches the settings row, and the only thing that opens a
 * transaction.
 *
 * Split out of {@link WhatsAppAdminService} so that no call to Evolution ever
 * happens inside one. The gateway has a 60s read timeout for session
 * operations; a connection held open for that long — a *write* connection, in
 * the update path — is a pool starved by something that has nothing to do with
 * the database.
 *
 * Returns a snapshot record rather than the entity: detached JPA state crossing
 * back into a caller that will not flush is a trap, and nothing outside here
 * needs to mutate it.
 */
@Component
class WhatsAppSettingsStore {

    private final WhatsAppSettingsRepository repository;

    WhatsAppSettingsStore(WhatsAppSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    Snapshot read() {
        return snapshotOf(load());
    }

    @Transactional
    Snapshot write(String instanceName, String desiredPhoneVersion) {
        WhatsAppSettings settings = load();
        settings.update(instanceName, desiredPhoneVersion);
        // Flushed here so the audit record is written inside this transaction
        // rather than at some later, less obvious point.
        return snapshotOf(repository.saveAndFlush(settings));
    }

    private WhatsAppSettings load() {
        return repository.findById(WhatsAppSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "whatsapp_settings sem a linha singleton — a migration V025 semeia id=1"));
    }

    private static Snapshot snapshotOf(WhatsAppSettings settings) {
        return new Snapshot(settings.getInstanceName(), settings.getDesiredPhoneVersion());
    }

    record Snapshot(String instanceName, String desiredPhoneVersion) {
    }
}
