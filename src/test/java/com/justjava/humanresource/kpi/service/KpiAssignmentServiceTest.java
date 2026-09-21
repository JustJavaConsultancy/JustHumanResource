package com.justjava.humanresource.kpi.service;

import com.justjava.humanresource.hr.dto.KpiAssignmentItemRequestDTO;
import com.justjava.humanresource.hr.dto.KpiBulkAssignmentRequestDTO;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import com.justjava.humanresource.kpi.entity.KpiAssignment;
import com.justjava.humanresource.kpi.entity.KpiDefinition;
import com.justjava.humanresource.kpi.repositories.KpiAssignmentRepository;
import com.justjava.humanresource.kpi.repositories.KpiDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KpiAssignmentServiceTest {

    @Mock
    private KpiAssignmentRepository assignmentRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private JobStepRepository jobStepRepository;

    @Mock
    private KpiDefinitionRepository kpiRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    private KpiAssignmentService service;
    private KpiDefinition parent;
    private KpiDefinition childA;
    private KpiDefinition childB;
    private KpiDefinition standalone;

    @BeforeEach
    void setUp() {
        service = new KpiAssignmentService(
                assignmentRepository,
                employeeRepository,
                jobStepRepository,
                kpiRepository,
                departmentRepository
        );
        ReflectionTestUtils.setField(service, "maxKpiWeight", new BigDecimal("1.00"));

        parent = kpi(1L, "Parent");
        childA = kpi(2L, "Child A");
        childB = kpi(3L, "Child B");
        standalone = kpi(4L, "Standalone");

        childA.setParentDefinition(parent);
        childB.setParentDefinition(parent);
        parent.setChildren(new ArrayList<>(List.of(childA, childB)));

        Employee employee = new Employee();
        employee.setId(10L);

        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(assignmentRepository.findByEmployee_IdAndActiveTrue(10L)).thenReturn(List.of());
        lenient().when(assignmentRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(kpiRepository.findById(1L)).thenReturn(Optional.of(parent));
        lenient().when(kpiRepository.findById(2L)).thenReturn(Optional.of(childA));
        lenient().when(kpiRepository.findById(3L)).thenReturn(Optional.of(childB));
        lenient().when(kpiRepository.findById(4L)).thenReturn(Optional.of(standalone));
    }

    @Test
    void parentAndBalancedChildrenCountOnlyParentWeight() {
        KpiBulkAssignmentRequestDTO request = request(
                item(1L, "0.40"),
                item(2L, "0.20"),
                item(3L, "0.20")
        );

        assertDoesNotThrow(() -> service.bulkAssign(request));
    }

    @Test
    void standaloneAndBalancedParentGroupCanReachConfiguredMax() {
        KpiBulkAssignmentRequestDTO request = request(
                item(4L, "0.60"),
                item(1L, "0.40"),
                item(2L, "0.20"),
                item(3L, "0.20")
        );

        assertDoesNotThrow(() -> service.bulkAssign(request));
    }

    @Test
    void standaloneAndParentGroupCannotExceedConfiguredMax() {
        KpiBulkAssignmentRequestDTO request = request(
                item(4L, "0.70"),
                item(1L, "0.40"),
                item(2L, "0.20"),
                item(3L, "0.20")
        );

        assertThrows(IllegalArgumentException.class, () -> service.bulkAssign(request));
    }

    @Test
    void childWeightsMustEqualParentWeight() {
        KpiBulkAssignmentRequestDTO request = request(
                item(1L, "0.40"),
                item(2L, "0.30"),
                item(3L, "0.20")
        );

        assertThrows(IllegalArgumentException.class, () -> service.bulkAssign(request));
    }

    @Test
    void childRequiresParentInSameScope() {
        KpiBulkAssignmentRequestDTO request = request(item(2L, "0.20"));

        assertThrows(IllegalArgumentException.class, () -> service.bulkAssign(request));
    }

    private KpiBulkAssignmentRequestDTO request(KpiAssignmentItemRequestDTO... items) {
        KpiBulkAssignmentRequestDTO request = new KpiBulkAssignmentRequestDTO();
        request.setEmployeeId(10L);
        request.setKpis(List.of(items));
        return request;
    }

    private KpiAssignmentItemRequestDTO item(Long kpiId, String weight) {
        KpiAssignmentItemRequestDTO item = new KpiAssignmentItemRequestDTO();
        item.setKpiId(kpiId);
        item.setWeight(new BigDecimal(weight));
        item.setMandatory(true);
        return item;
    }

    private KpiDefinition kpi(Long id, String name) {
        KpiDefinition kpi = new KpiDefinition();
        kpi.setId(id);
        kpi.setName(name);
        kpi.setCode(name.toUpperCase().replace(' ', '_'));
        kpi.setActive(true);
        kpi.setChildren(new ArrayList<>());
        return kpi;
    }
}
