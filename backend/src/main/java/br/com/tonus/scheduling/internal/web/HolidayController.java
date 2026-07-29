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
@RequestMapping("/holidays")
class HolidayController {

    private final SchedulingService schedulingService;

    HolidayController(SchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    @PostMapping
    ResponseEntity<HolidayResponse> create(@Valid @RequestBody HolidayRequest request) {
        HolidayResponse response = HolidayResponse.from(
                schedulingService.createHoliday(request.date(), request.description()));
        return ResponseEntity.created(URI.create("/holidays/" + response.id())).body(response);
    }

    /**
     * Intervalo opcional: a lista cresce um punhado de linhas por ano e nunca
     * é grande, mas "os feriados deste semestre" é a pergunta real de quem
     * monta a agenda.
     */
    @GetMapping
    Page<HolidayResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault Pageable pageable) {
        return schedulingService.findHolidays(from, to, pageable).map(HolidayResponse::from);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        schedulingService.deleteHoliday(id);
        return ResponseEntity.noContent().build();
    }
}
