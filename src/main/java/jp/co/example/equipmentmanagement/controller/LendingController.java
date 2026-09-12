package jp.co.example.equipmentmanagement.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import jp.co.example.equipmentmanagement.dto.LendingForm;
import jp.co.example.equipmentmanagement.service.EquipmentNotAvailableException;
import jp.co.example.equipmentmanagement.service.EquipmentNotFoundException;
import jp.co.example.equipmentmanagement.service.LendingService;
import jp.co.example.equipmentmanagement.service.NoActiveLendingException;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class LendingController {

    private final LendingService lendingService;

    @PostMapping("/equipment/{id}/lend")
    public String lend(@PathVariable Long id, @Valid @ModelAttribute LendingForm form,
            BindingResult bindingResult, RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "借用者名を入力してください");
            return "redirect:/equipment";
        }

        lendingService.lend(id, form);
        redirectAttributes.addFlashAttribute("message", "貸出を登録しました");
        return "redirect:/equipment";
    }

    @PostMapping("/equipment/{id}/return")
    public String returnEquipment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        lendingService.returnEquipment(id);
        redirectAttributes.addFlashAttribute("message", "返却を登録しました");
        return "redirect:/equipment";
    }

    @GetMapping("/lendings")
    public String history(Model model) {
        model.addAttribute("lendingList", lendingService.findAllOrderByLentAtDesc());
        return "lending/list";
    }

    @ExceptionHandler({ EquipmentNotAvailableException.class, EquipmentNotFoundException.class,
            NoActiveLendingException.class })
    public String handleLendingError(RuntimeException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", ex.getMessage());
        return "redirect:/equipment";
    }
}
