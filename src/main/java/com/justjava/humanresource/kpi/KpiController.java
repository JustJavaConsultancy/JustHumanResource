package com.justjava.humanresource.kpi;

import com.justjava.humanresource.kpi.entity.KpiDefinition;
import com.justjava.humanresource.kpi.service.KpiDefinitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@Controller
public class KpiController {
    @Autowired
    private KpiDefinitionService kpiDefinitionService;

    @GetMapping("/kpi")
    public String attendancePage(Model model) {
        List<KpiDefinition> kpiDefinitions = kpiDefinitionService.getAll();
        kpiDefinitions.forEach(
                kpi -> System.out.println("KPI Name: " + kpi.getName() + ", Description: " + kpi.getDescription() + ", Target Value: " + kpi.getTargetValue())
        );
        model.addAttribute("kpiDefinitions", kpiDefinitions);
        model.addAttribute("title","KPI Management");
        model.addAttribute("subTitle","Track and analyze employee performance metrics");
        return "kpi/main";
    }
    @PostMapping("/kpi/definition")
    public String kpiDefinition(KpiDefinition kpi) {
       kpiDefinitionService.create(kpi);
         return "redirect:/kpi";
    }
}
