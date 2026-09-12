package jp.co.example.equipmentmanagement.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import jp.co.example.equipmentmanagement.dto.EmployeeForm;
import jp.co.example.equipmentmanagement.entity.Employee;
import jp.co.example.equipmentmanagement.service.EmployeeDeletionNotAllowedException;
import jp.co.example.equipmentmanagement.service.EmployeeNotFoundException;
import jp.co.example.equipmentmanagement.service.EmployeeService;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("employeeList", employeeService.findAll());
        return "employee/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("employeeForm", EmployeeForm.empty());
        return "employee/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("employeeForm") EmployeeForm form,
            BindingResult bindingResult, RedirectAttributes redirectAttributes) {

        if (employeeService.isEmployeeNumberTaken(form.getEmployeeNumber(), null)) {
            bindingResult.rejectValue("employeeNumber", "duplicate", "この社員番号は既に使用されています");
        }

        if (bindingResult.hasErrors()) {
            return "employee/form";
        }

        employeeService.create(form);
        redirectAttributes.addFlashAttribute("message", "社員を登録しました");
        return "redirect:/employees";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Employee employee = employeeService.findById(id);
        model.addAttribute("employeeForm", EmployeeForm.from(employee));
        return "employee/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("employeeForm") EmployeeForm form,
            BindingResult bindingResult, RedirectAttributes redirectAttributes) {

        if (employeeService.isEmployeeNumberTaken(form.getEmployeeNumber(), id)) {
            bindingResult.rejectValue("employeeNumber", "duplicate", "この社員番号は既に使用されています");
        }

        if (bindingResult.hasErrors()) {
            return "employee/form";
        }

        employeeService.update(id, form);
        redirectAttributes.addFlashAttribute("message", "社員を更新しました");
        return "redirect:/employees";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        employeeService.delete(id);
        redirectAttributes.addFlashAttribute("message", "社員を削除しました");
        return "redirect:/employees";
    }

    @ExceptionHandler({ EmployeeNotFoundException.class, EmployeeDeletionNotAllowedException.class })
    public String handleEmployeeError(RuntimeException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", ex.getMessage());
        return "redirect:/employees";
    }
}
