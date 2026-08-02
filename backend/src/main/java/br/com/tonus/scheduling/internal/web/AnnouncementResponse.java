package br.com.tonus.scheduling.internal.web;

import br.com.tonus.scheduling.internal.domain.Announcement;
import java.time.LocalDate;

public record AnnouncementResponse(Long id, LocalDate date, String description, boolean suspendsClasses) {

    public static AnnouncementResponse from(Announcement announcement) {
        return new AnnouncementResponse(announcement.getId(), announcement.getDate(),
                announcement.getDescription(), announcement.suspendsClasses());
    }
}
