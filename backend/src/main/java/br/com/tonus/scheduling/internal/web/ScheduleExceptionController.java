package br.com.tonus.scheduling.internal.web;

import br.com.tonus.scheduling.internal.InvalidExceptionTargetException;
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
@RequestMapping("/schedule-exceptions")
class ScheduleExceptionController {

    private final SchedulingService schedulingService;

    ScheduleExceptionController(SchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    /**
     * O tipo escolhe o método do serviço em vez de virar um parâmetro dele:
     * cancelar e remarcar publicam eventos diferentes e têm invariantes
     * diferentes, então um método só com ifs dentro esconderia as duas regras.
     */
    @PostMapping
    ResponseEntity<ScheduleExceptionResponse> create(@Valid @RequestBody ScheduleExceptionRequest request) {
        ScheduleExceptionResponse response = ScheduleExceptionResponse.from(switch (request.type()) {
            case CANCELED -> {
                if (request.newDate() != null || request.newTime() != null) {
                    throw new InvalidExceptionTargetException("Cancelamento não aceita nova data nem nova hora");
                }
                yield schedulingService.cancelClass(request.studentId(), request.date());
            }
            case RESCHEDULED -> schedulingService.rescheduleClass(
                    request.studentId(), request.date(), request.newDate(), request.newTime());
        });
        return ResponseEntity.created(URI.create("/schedule-exceptions/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    ScheduleExceptionResponse get(@PathVariable Long id) {
        return ScheduleExceptionResponse.from(schedulingService.findException(id));
    }

    /**
     * Filtros por aluno e por intervalo, todos opcionais. O intervalo é sobre
     * {@code date} — a data em que a mudança acontece — e não sobre a data de
     * destino: quem procura "o que mudou nesta semana" está perguntando pelos
     * dias que deixaram de ser normais.
     */
    @GetMapping
    Page<ScheduleExceptionResponse> list(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault Pageable pageable) {
        return schedulingService.findExceptions(studentId, from, to, pageable)
                .map(ScheduleExceptionResponse::from);
    }

    /**
     * Apagar devolve o dia ao template. É o desfazer de um cancelamento
     * marcado por engano — sem ele, a correção só existiria no banco.
     */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        schedulingService.deleteException(id);
        return ResponseEntity.noContent().build();
    }
}
