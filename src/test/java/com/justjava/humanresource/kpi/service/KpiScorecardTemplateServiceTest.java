package com.justjava.humanresource.kpi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import com.justjava.humanresource.kpi.dto.KpiScorecardTemplateCommand;
import com.justjava.humanresource.kpi.entity.KpiDefinition;
import com.justjava.humanresource.kpi.entity.KpiScorecardTemplate;
import com.justjava.humanresource.kpi.entity.KpiScorecardTemplateItem;
import com.justjava.humanresource.kpi.entity.KpiScorecardTemplateVersion;
import com.justjava.humanresource.kpi.entity.KpiTemplateAssignment;
import com.justjava.humanresource.kpi.enums.KpiScorecardTemplateStatus;
import com.justjava.humanresource.kpi.repositories.KpiDefinitionRepository;
import com.justjava.humanresource.kpi.repositories.KpiAssignmentRepository;
import com.justjava.humanresource.kpi.repositories.KpiScorecardTemplateItemRepository;
import com.justjava.humanresource.kpi.repositories.KpiScorecardTemplateRepository;
import com.justjava.humanresource.kpi.repositories.KpiScorecardTemplateVersionRepository;
import com.justjava.humanresource.kpi.repositories.KpiScoringRubricRepository;
import com.justjava.humanresource.kpi.repositories.KpiTemplateAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KpiScorecardTemplateServiceTest {

    @Mock private KpiScorecardTemplateRepository templateRepository;
    @Mock private KpiScorecardTemplateItemRepository itemRepository;
    @Mock private KpiScorecardTemplateVersionRepository versionRepository;
    @Mock private KpiTemplateAssignmentRepository templateAssignmentRepository;
    @Mock private KpiDefinitionRepository kpiRepository;
    @Mock private KpiAssignmentRepository assignmentRepository;
    @Mock private KpiScoringRubricRepository rubricRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private JobStepRepository jobStepRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private KpiAssignmentService assignmentService;

    private KpiScorecardTemplateService service;

    @BeforeEach
    void setUp() {
        service = new KpiScorecardTemplateService(
                templateRepository,
                itemRepository,
                versionRepository,
                templateAssignmentRepository,
                kpiRepository,
                assignmentRepository,
                rubricRepository,
                employeeRepository,
                jobStepRepository,
                departmentRepository,
                assignmentService,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "maxKpiWeight", new BigDecimal("1.00"));

        lenient().when(kpiRepository.findById(1L)).thenReturn(Optional.of(kpi(1L, "Financial")));
        lenient().when(kpiRepository.findById(2L)).thenReturn(Optional.of(kpi(2L, "Cost Management")));
        lenient().when(kpiRepository.findById(3L)).thenReturn(Optional.of(kpi(3L, "Budget Management")));
        lenient().when(kpiRepository.findById(4L)).thenReturn(Optional.of(kpi(4L, "People")));

        lenient().when(templateRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(KpiScorecardTemplate.class)))
                .thenAnswer(invocation -> {
                    KpiScorecardTemplate template = invocation.getArgument(0);
                    template.setId(100L);
                    return template;
                });
        lenient().when(itemRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(KpiScorecardTemplateItem.class)))
                .thenAnswer(invocation -> {
                    KpiScorecardTemplateItem item = invocation.getArgument(0);
                    item.setId(item.getKpi().getId());
                    return item;
                });
        lenient().when(versionRepository.save(any(KpiScorecardTemplateVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void activeTemplateRequiresTotalWeightToEqualConfiguredMax() {
        KpiScorecardTemplateCommand command = command(
                item("financial", null, 1L, "0.40"),
                item("cost", "financial", 2L, "0.20"),
                item("budget", "financial", 3L, "0.20")
        );

        assertThrows(IllegalArgumentException.class, () -> service.create(command));
    }

    @Test
    void activeTemplateAcceptsBalancedScorecardAtConfiguredMax() {
        KpiScorecardTemplateCommand command = command(
                item("financial", null, 1L, "0.40"),
                item("cost", "financial", 2L, "0.20"),
                item("budget", "financial", 3L, "0.20"),
                item("people", null, 4L, "0.60")
        );

        assertDoesNotThrow(() -> service.create(command));
    }

    @Test
    void importedTemplateRecordsSourceMetadata() {
        KpiScorecardTemplateCommand command = command(
                item("financial", null, 1L, "0.40"),
                item("cost", "financial", 2L, "0.20"),
                item("budget", "financial", 3L, "0.20"),
                item("people", null, 4L, "0.60")
        );

        KpiScorecardTemplate template = service.createImported(command, "head-hr.xlsx", "hr@example.com");

        assertEquals("EXCEL_IMPORT", template.getSourceType());
        assertEquals("head-hr.xlsx", template.getSourceFileName());
        assertEquals("hr@example.com", template.getImportedBy());
    }

    @Test
    void childWeightsMustEqualParentWeight() {
        KpiScorecardTemplateCommand command = command(
                item("financial", null, 1L, "0.40"),
                item("cost", "financial", 2L, "0.30"),
                item("budget", "financial", 3L, "0.20"),
                item("people", null, 4L, "0.60")
        );

        assertThrows(IllegalArgumentException.class, () -> service.create(command));
    }

    @Test
    void draftTemplateCanBeSavedBeforeWeightsAreBalanced() {
        KpiScorecardTemplateCommand command = command(
                item("financial", null, 1L, "0.40"),
                item("cost", "financial", 2L, "0.10"),
                item("people", null, 4L, "0.30")
        );
        command.setActive(false);

        assertDoesNotThrow(() -> service.create(command));
    }

    @Test
    void publishCreatesImmutableTemplateVersion() {
        KpiScorecardTemplate template = KpiScorecardTemplate.builder()
                .name("Head HR Scorecard")
                .active(false)
                .status(KpiScorecardTemplateStatus.DRAFT)
                .build();
        template.setId(100L);

        KpiScorecardTemplateItem financial = templateItem(10L, template, kpi(1L, "Financial"), null, "0.40");
        KpiScorecardTemplateItem cost = templateItem(11L, template, kpi(2L, "Cost Management"), financial, "0.20");
        KpiScorecardTemplateItem budget = templateItem(12L, template, kpi(3L, "Budget Management"), financial, "0.20");
        KpiScorecardTemplateItem people = templateItem(13L, template, kpi(4L, "People"), null, "0.60");

        lenient().when(templateRepository.findById(100L)).thenReturn(Optional.of(template));
        lenient().when(itemRepository.findByTemplate_IdOrderBySortOrderAscIdAsc(100L))
                .thenReturn(List.of(financial, cost, budget, people));

        KpiScorecardTemplateVersion version = service.publish(100L);

        assertEquals(1, version.getVersionNumber());
        assertEquals(KpiScorecardTemplateStatus.PUBLISHED, template.getStatus());
        assertEquals("system", version.getPublishedBy());
        verify(versionRepository).save(any(KpiScorecardTemplateVersion.class));
    }

    @Test
    void publishedTemplateCannotBeEditedDirectly() {
        KpiScorecardTemplate template = KpiScorecardTemplate.builder()
                .name("Published")
                .active(true)
                .status(KpiScorecardTemplateStatus.PUBLISHED)
                .build();
        template.setId(200L);
        when(templateRepository.findById(200L)).thenReturn(Optional.of(template));

        assertThrows(IllegalStateException.class, () -> service.update(200L, command(
                item("financial", null, 1L, "1.00")
        )));
    }

    @Test
    void archiveRequiresForceWhenTemplateHasActiveAssignments() {
        KpiScorecardTemplate template = KpiScorecardTemplate.builder()
                .name("Assigned")
                .active(true)
                .status(KpiScorecardTemplateStatus.PUBLISHED)
                .build();
        template.setId(300L);
        when(templateRepository.findById(300L)).thenReturn(Optional.of(template));
        when(templateAssignmentRepository.findByTemplate_IdAndActiveTrue(300L))
                .thenReturn(List.of(KpiTemplateAssignment.builder().active(true).build()));

        assertThrows(IllegalStateException.class, () -> service.archive(300L));
    }

    private KpiScorecardTemplateCommand command(KpiScorecardTemplateCommand.Item... items) {
        KpiScorecardTemplateCommand command = new KpiScorecardTemplateCommand();
        command.setName("Head HR Scorecard");
        command.setActive(true);
        command.setItems(List.of(items));
        return command;
    }

    private KpiScorecardTemplateCommand.Item item(String key, String parentKey, Long kpiId, String weight) {
        KpiScorecardTemplateCommand.Item item = new KpiScorecardTemplateCommand.Item();
        item.setClientKey(key);
        item.setParentClientKey(parentKey);
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
        return kpi;
    }

    private KpiScorecardTemplateItem templateItem(
            Long id,
            KpiScorecardTemplate template,
            KpiDefinition kpi,
            KpiScorecardTemplateItem parent,
            String weight
    ) {
        KpiScorecardTemplateItem item = KpiScorecardTemplateItem.builder()
                .template(template)
                .kpi(kpi)
                .parentItem(parent)
                .weight(new BigDecimal(weight))
                .mandatory(true)
                .build();
        item.setId(id);
        return item;
    }
}
