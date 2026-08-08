package br.com.tonus.auth.internal.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Who is logged in, from the front end's point of view.
 *
 * {@code studentId} and {@code teacherId} are left out of the response when
 * null: the ADMIN is neither a student nor a teacher, and sending
 * {@code null} would force the client to distinguish "absent" from "empty"
 * for no benefit.
 *
 * {@code name} is the user's login. The legal name lives in registration, and
 * querying registration on every session check would couple the API's
 * hottest path to another module — see SPEC-006.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MeResponse(String role, String name, Long studentId, Long teacherId) {
}
