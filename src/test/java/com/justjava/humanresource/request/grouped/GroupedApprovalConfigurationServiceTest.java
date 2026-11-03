package com.justjava.humanresource.request.grouped;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.core.enums.*;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.service.EmployeeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupedApprovalConfigurationServiceTest {
    @Mock GroupedApprovalVersionRepository versions;
    @Mock GroupedApprovalDefinitionRepository definitions;
    @Mock EmployeeRepository employees;
    @Mock EmployeeService employeeService;
    @Mock AuthenticationManager auth;
    @InjectMocks GroupedApprovalConfigurationService service;

    @BeforeEach void enabled() { ReflectionTestUtils.setField(service,"enabled",true); }
    GroupedApprovalVersion version(String state) {
        var v = new GroupedApprovalVersion(); v.setId(1L); v.setVersion(2L); v.setState(state);
        v.setName("Path"); v.setRevision(1); return v;
    }
    GroupedApprovalCommand.Level level(Long... ids) { return new GroupedApprovalCommand.Level("Review",ApprovalPolicy.ALL,List.of(ids)); }

    @Test void anonymousCannotReadOrCreateConfiguration() {
        assertThatThrownBy(service::list).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> service.create(new GroupedApprovalCommand("Path",null,List.of(level(11L)))))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(versions,definitions);
    }
    @Test void featureOffRejectsNewConfiguration() {
        when(auth.isAdmin()).thenReturn(true); ReflectionTestUtils.setField(service,"enabled",false);
        assertThatThrownBy(() -> service.create(new GroupedApprovalCommand("Path",null,List.of(level(11L)))))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(versions);
    }
    @Test void publishedVersionCannotBeEdited() {
        when(auth.isAdmin()).thenReturn(true);
        Employee e = employee(11L); when(employees.findById(11L)).thenReturn(Optional.of(e));
        when(versions.lockById(1L)).thenReturn(Optional.of(version("PUBLISHED")));
        assertThatThrownBy(() -> service.update(1L,2L,new GroupedApprovalCommand("Path",null,List.of(level(11L)))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("immutable");
        verify(definitions,never()).deleteByPathVersionId(anyLong());
    }
    @Test void duplicatesAcrossLevelsAreRejected() {
        when(auth.isAdmin()).thenReturn(true); when(employees.findById(11L)).thenReturn(Optional.of(employee(11L)));
        assertThatThrownBy(() -> service.create(new GroupedApprovalCommand("Path",null,List.of(level(11L),level(11L)))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("more than once");
        verifyNoInteractions(versions);
    }
    @Test void requesterExclusionCannotMakeAnEmptyLevel() {
        when(versions.findById(1L)).thenReturn(Optional.of(version("PUBLISHED")));
        var definition = new GroupedApprovalDefinition(); definition.setName("Review"); definition.setPolicy(ApprovalPolicy.ALL); definition.setEmployeeIds(List.of(11L));
        when(definitions.findByPathVersionIdOrderByOrdinal(1L)).thenReturn(List.of(definition));
        assertThatThrownBy(() -> service.effectiveLevels(1L,11L)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("no eligible approvers");
    }
    @Test void disabledPublishedVersionCannotStartAnotherCycle() {
        var version = version("PUBLISHED"); version.setEnabled(false); when(versions.findById(1L)).thenReturn(Optional.of(version));
        assertThatThrownBy(() -> service.published(1L)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void policiesHaveExplicitNonDeadlockingResolutionRules() {
        assertThat(GroupedApprovalService.resolves(ApprovalPolicy.ALL,"APPROVED",false)).isFalse();
        assertThat(GroupedApprovalService.resolves(ApprovalPolicy.ALL,"APPROVED",true)).isTrue();
        for (var policy : ApprovalPolicy.values()) {
            assertThat(GroupedApprovalService.resolves(policy,"REJECTED",false)).isTrue();
            assertThat(GroupedApprovalService.resolves(policy,"RETURNED",false)).isTrue();
        }
        assertThat(GroupedApprovalService.resolves(ApprovalPolicy.ANY_ONE,"APPROVED",false)).isTrue();
    }
    private Employee employee(long id) {
        Employee e = new Employee(); e.setId(id); e.setEmploymentStatus(EmploymentStatus.ACTIVE); e.setStatus(RecordStatus.ACTIVE); return e;
    }
}
