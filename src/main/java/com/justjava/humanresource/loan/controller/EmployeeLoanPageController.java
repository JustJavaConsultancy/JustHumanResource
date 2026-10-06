package com.justjava.humanresource.loan.controller;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Employee portal pages. Also used by assigned custom approvers, who need no HR/Finance role.
 * The pages load their data from /api/employee/loans, where ownership and assignment are enforced.
 */
@Controller
@RequiredArgsConstructor
public class EmployeeLoanPageController {

    private final AuthenticationManager authenticationManager;
    private final EmployeeService employeeService;

    @GetMapping("/employee/loans")
    public String loans(Model model) {
        populateEmployeeModel(model);
        model.addAttribute("title", "Loans");
        model.addAttribute("subTitle", "Apply for a loan, track applications, and view your repayments");
        return "loan/employee-main";
    }

    @GetMapping("/employee/loans/{id:\\d+}")
    public String loanDetail(Model model) {
        populateEmployeeModel(model);
        model.addAttribute("title", "Loan Detail");
        model.addAttribute("subTitle", "Review loan terms, approvals, repayment schedule, and attachments");
        return "loan/employee-detail";
    }

    private void populateEmployeeModel(Model model) {
        String email = (String) authenticationManager.get("email");
        if (email != null) {
            Employee loginEmployee = employeeService.getByEmail(email);
            if (loginEmployee != null) {
                model.addAttribute("employee", employeeService.getEmployeeWithBankDetails(loginEmployee.getId()));
            }
        }
    }
}
