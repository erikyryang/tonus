/**
 * Schedule: default schedule, one-off exceptions and holidays.
 *
 * Depends on registration because the schedule does not exist without a student — and the teacher
 * of each class comes from the student's link, never from a field of its own here.
 *
 * The edge is acyclic: registration does not know scheduling. Side effect
 * outside the module is an event (ClassCanceled, ClassRescheduled,
 * AnnouncementCreated), never a direct call.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared", "registration"})
package br.com.tonus.scheduling;
