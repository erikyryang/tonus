package br.com.tonus.scheduling;

import java.time.LocalDate;

/**
 * Announcement registered. The consumer notifies every active student — who they
 * are is a decision of the consumer, not of this event.
 *
 * Carries the description and the effect because the message depends on both: "we won't
 * have class" and "classes happen as usual" are different notices, and the
 * reason is what makes the notice worth anything. Without them the consumer would have to
 * query the schedule to build the text — and `notification` does not ask `scheduling`
 * anything, by architectural decision.
 */
public record AnnouncementCreated(LocalDate date, String description, boolean suspendsClasses) {
}
