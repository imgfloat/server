package dev.kruhlmann.imgfloat.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.kruhlmann.imgfloat.model.db.audit.AuditLogEntry;
import dev.kruhlmann.imgfloat.repository.audit.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogServiceTest {

    private AuditLogRepository repository;
    private AuditLogService service;

    @BeforeEach
    void setup() {
        repository = mock(AuditLogRepository.class);
        service = new AuditLogService(repository);
    }

    @Test
    void recordEntryPersistesNormalizedEntry() {
        service.recordEntry("BroadCaster", "Actor", "ACTION", "some details");

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(repository).save(captor.capture());
        AuditLogEntry entry = captor.getValue();
        assertThat(entry.getBroadcaster()).isEqualTo("broadcaster");
        assertThat(entry.getActor()).isEqualTo("actor");
        assertThat(entry.getAction()).isEqualTo("ACTION");
        assertThat(entry.getDetails()).isEqualTo("some details");
    }

    @Test
    void recordEntrySkipsBlankBroadcaster() {
        service.recordEntry("  ", "actor", "ACTION", "details");
        verify(repository, never()).save(any());
    }

    @Test
    void recordEntrySkipsNullBroadcaster() {
        service.recordEntry(null, "actor", "ACTION", "details");
        verify(repository, never()).save(any());
    }

    @Test
    void recordEntryUsesDefaultActorWhenNull() {
        service.recordEntry("broadcaster", null, "ACTION", "details");

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getActor()).isEqualTo("system");
    }

    @Test
    void recordEntryUsesDefaultActorWhenBlank() {
        service.recordEntry("broadcaster", "  ", "ACTION", "details");

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getActor()).isEqualTo("system");
    }

    @Test
    void recordEntryDoesNotThrowWhenRepositoryFails() {
        doThrow(new DataAccessResourceFailureException("db down")).when(repository).save(any());
        // Must not propagate
        service.recordEntry("broadcaster", "actor", "ACTION", "details");
    }

    @Test
    void listEntriesReturnsEmptyPageForBlankBroadcaster() {
        Page<AuditLogEntry> result = service.listEntries("  ", null, null, null, 0, 20);
        assertThat(result.isEmpty()).isTrue();
        verify(repository, never()).searchEntries(any(), any(), any(), any(), any());
    }

    @Test
    void listEntriesClampsSizeAndDelegatesToRepository() {
        when(repository.searchEntries(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.listEntries("broadcaster", null, null, null, 0, 999);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).searchEntries(any(), any(), any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    void deleteEntriesForBroadcasterDelegatesToRepository() {
        service.deleteEntriesForBroadcaster("Broadcaster");
        verify(repository).deleteByBroadcaster("broadcaster");
    }

    @Test
    void deleteEntriesSkipsBlankBroadcaster() {
        service.deleteEntriesForBroadcaster("  ");
        verify(repository, never()).deleteByBroadcaster(any());
    }
}
