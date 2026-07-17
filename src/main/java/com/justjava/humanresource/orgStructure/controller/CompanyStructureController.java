package com.justjava.humanresource.orgStructure.controller;

import com.justjava.humanresource.orgStructure.dto.CompanyStructureDTO;
import com.justjava.humanresource.orgStructure.services.CompanyStructureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyStructureController {

    private final CompanyStructureService companyStructureService;

    // GET /api/companies/{companyId}/structure
    @GetMapping("/{companyId}/structure")
    @ResponseBody
    public CompanyStructureDTO getCompanyStructure(@PathVariable Long companyId) {
        return companyStructureService.getCompanyStructure(companyId);
    }
}