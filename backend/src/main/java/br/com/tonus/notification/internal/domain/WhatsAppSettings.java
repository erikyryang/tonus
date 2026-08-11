package br.com.tonus.notification.internal.domain;

import br.com.tonus.shared.Audited;
import br.com.tonus.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Which instance the studio speaks through, and which gateway version it wants.
 *
 * Single row with a fixed id, like {@code BillingSettings} — but this one DOES
 * extend {@code BaseEntity}, which that one deliberately does not. The reason
 * is {@link Audited}: the listener that writes audit_log reads the id through
 * BaseEntity and records "unknown" for anything else, which would make the
 * trail useless. The generated id is never exercised: the row is born in the
 * migration and from then on is only loaded and updated.
 */
@Entity
@Table(name = "whatsapp_settings")
@Audited
public class WhatsAppSettings extends BaseEntity {

    /** The id of the only row that can exist. */
    public static final long SINGLETON_ID = 1L;

    @Column(name = "instance_name", nullable = false)
    private String instanceName;

    @Column(name = "desired_phone_version", nullable = false)
    private String desiredPhoneVersion;

    protected WhatsAppSettings() {
    }

    public void update(String instanceName, String desiredPhoneVersion) {
        this.instanceName = instanceName;
        this.desiredPhoneVersion = desiredPhoneVersion;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public String getDesiredPhoneVersion() {
        return desiredPhoneVersion;
    }
}
