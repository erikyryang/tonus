package br.com.tonus.scheduling;

/**
 * Manual dispatch of a free-form message to every active student, requested by the ADMIN.
 *
 * There is no domain fact behind this event — nothing changes in the schedule —, and that is
 * why it carries {@code broadcastId}: a UUID generated at the moment of the
 * dispatch, used as the {@code reference_key} of the notification log. The date would not
 * work: the ADMIN can legitimately dispatch twice on the same day, and
 * keying by date would make the second dispatch be swallowed by the UNIQUE as if it
 * were a redelivery of the first.
 */
public record BroadcastRequested(String broadcastId, String message) {
}
