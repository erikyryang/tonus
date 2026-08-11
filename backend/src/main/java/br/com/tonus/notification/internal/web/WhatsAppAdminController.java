package br.com.tonus.notification.internal.web;

import br.com.tonus.notification.internal.WhatsAppAdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The first endpoints notification exposes (SPEC-013).
 *
 * They live here and not in shared or auth for the same reason the rule that
 * kept them out existed: whoever talks to Evolution is notification and nobody
 * else. A controller elsewhere would force another module to know the gateway,
 * which is exactly what that rule protects.
 *
 * Not /me/...: there is no "own data" here. The session is one, and it is the
 * studio's. Authorization is by route in SecurityConfig, like the rest.
 */
@RestController
@RequestMapping("/whatsapp")
class WhatsAppAdminController {

    private final WhatsAppAdminService service;

    WhatsAppAdminController(WhatsAppAdminService service) {
        this.service = service;
    }

    @GetMapping("/status")
    WhatsAppStatusResponse status() {
        return WhatsAppStatusResponse.from(service.status());
    }

    @GetMapping("/instances")
    WhatsAppInstancesResponse instances() {
        return WhatsAppInstancesResponse.from(service.instances());
    }

    @PutMapping("/settings")
    WhatsAppStatusResponse updateSettings(@Valid @RequestBody WhatsAppSettingsRequest request) {
        return WhatsAppStatusResponse.from(
                service.updateSettings(request.instance(), request.desiredPhoneVersion()));
    }

    @PostMapping("/session")
    WhatsAppSessionResponse connect() {
        return WhatsAppSessionResponse.from(service.connect());
    }

    @DeleteMapping("/session")
    WhatsAppStatusResponse disconnect() {
        return WhatsAppStatusResponse.from(service.disconnect());
    }
}
