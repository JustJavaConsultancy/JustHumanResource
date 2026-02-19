package com.justjava.humanresource.jobStructure;

import com.justjava.humanresource.hr.dto.CreateJobGradeWithStepsCommand;
import com.justjava.humanresource.hr.dto.JobGradeResponseDTO;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.service.SetupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@Controller
public class JobStructureController {
    @Autowired
    private SetupService setupService;

    @GetMapping("/job-structure")
    public String getJobStructure(Model model) {

        List<JobGradeResponseDTO> jobGrades = setupService.getAllJobGrades();
        List<Department> departments = setupService.getAllDepartments();

        // Total steps across all grades
        int totalSteps = jobGrades.stream()
                .mapToInt(grade -> grade.getSteps().size())
                .sum();

        model.addAttribute("departments", departments);
        model.addAttribute("grades", jobGrades);
        model.addAttribute("totalSteps", totalSteps);
        model.addAttribute("title","Job Structure Management");
        model.addAttribute("subTitle","Define and manage job roles, hierarchies, and reporting structures");

        return "jobStructure/main";
    }

    @PostMapping("/addJobGroup")
    public String addJobGroup(CreateJobGradeWithStepsCommand command) {
        System.out.println("Received command to add job group: " + command.getGradeName());
        setupService.createJobGradeWithSteps(command);
        return "redirect:/job-structure";
    }
}
