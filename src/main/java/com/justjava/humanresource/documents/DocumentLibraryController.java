package com.justjava.humanresource.documents;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class DocumentLibraryController {

    private final DocumentLibraryService documentLibraryService;

    @GetMapping("/documents/library")
    public String library(Model model) {
        documentLibraryService.requireDocumentLibraryAccess();
        model.addAttribute("title", "Document Library");
        model.addAttribute("subTitle", "Search, preview, and download employee documents and request attachments");
        return "documents/library";
    }

    @GetMapping("/api/documents/library")
    @ResponseBody
    public DocumentLibraryResponse search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate uploadedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate uploadedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return documentLibraryService.search(query, sourceType, employeeId, uploadedFrom, uploadedTo, page, size);
    }

    @GetMapping("/api/documents/me")
    @ResponseBody
    public List<DocumentLibraryItemDTO> myDocuments() {
        return documentLibraryService.currentEmployeeDocuments();
    }

}
