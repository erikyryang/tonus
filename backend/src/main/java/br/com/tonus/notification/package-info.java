/**
 * Notices to the student via WhatsApp. The only module that talks to the
 * outside world.
 *
 * Depends on registration to resolve the student's name and phone, and on
 * the modules whose events it consumes — consuming an event means
 * referencing the record the publisher exposes at its root, and that's a
 * compile-time dependency like any other. The runtime coupling stays
 * asynchronous: there's no path back, nobody calls notification.
 *
 * All edges are acyclic: none of these modules know about notification.
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"shared", "registration", "scheduling", "billing"})
package br.com.tonus.notification;
