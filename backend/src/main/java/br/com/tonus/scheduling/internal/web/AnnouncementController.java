package br.com.tonus.scheduling.internal.web;

import br.com.tonus.scheduling.internal.SchedulingService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/announcements")
class AnnouncementController {

    private final SchedulingService schedulingService;

    AnnouncementController(SchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    @PostMapping
    ResponseEntity<AnnouncementResponse> create(@Valid @RequestBody AnnouncementRequest request) {
        AnnouncementResponse response = AnnouncementResponse.from(
                schedulingService.createAnnouncement(request.date(), request.description(), request.suspendsClasses()));
        return ResponseEntity.created(URI.create("/announcements/" + response.id())).body(response);
    }

    /**
     * Intervalo opcional: a lista cresce um punhado de linhas por ano e nunca
     * é grande, mas "os comunicados deste semestre" é a pergunta real de quem
     * monta a agenda.
     */
    @GetMapping
    Page<AnnouncementResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault Pageable pageable) {
        return schedulingService.findAnnouncements(from, to, pageable).map(AnnouncementResponse::from);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        schedulingService.deleteAnnouncement(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Mensagem livre para todo aluno ativo (mudança 9) — fora do ciclo de
     * comunicado de dia/efeito. 202: o disparo só publica o evento, o envio de
     * verdade é assíncrono e não termina dentro desta requisição.
     */
    @PostMapping("/broadcast")
    ResponseEntity<Void> broadcast(@Valid @RequestBody BroadcastRequest request) {
        schedulingService.requestBroadcast(request.message());
        return ResponseEntity.accepted().build();
    }
}
