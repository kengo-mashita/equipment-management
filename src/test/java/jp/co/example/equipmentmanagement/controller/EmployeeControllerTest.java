package jp.co.example.equipmentmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jp.co.example.equipmentmanagement.entity.Employee;
import jp.co.example.equipmentmanagement.entity.Lending;
import jp.co.example.equipmentmanagement.repository.EmployeeRepository;
import jp.co.example.equipmentmanagement.repository.LendingRepository;

/**
 * 社員マスタCRUD（登録・編集・削除）の結合テスト。バリデーション表示や
 * 貸出履歴による削除制限など、Controller-Service間の連携を確認する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LendingRepository lendingRepository;

    @Test
    @WithMockUser(roles = "ADMIN")
    void 新規登録が成功すると一覧にリダイレクトされ保存される() throws Exception {
        mockMvc.perform(post("/employees").with(csrf())
                        .param("employeeNumber", "E-9001")
                        .param("name", "テスト太郎")
                        .param("department", "テスト部"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employees"))
                .andExpect(flash().attributeExists("message"));

        assertThat(employeeRepository.existsByEmployeeNumber("E-9001")).isTrue();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 社員番号が重複していると登録が拒否されエラーが表示される() throws Exception {
        Employee existing = employeeRepository.findAll().get(0);

        mockMvc.perform(post("/employees").with(csrf())
                        .param("employeeNumber", existing.getEmployeeNumber())
                        .param("name", "テスト太郎"))
                .andExpect(status().isOk())
                .andExpect(view().name("employee/form"))
                .andExpect(model().attributeHasFieldErrors("employeeForm", "employeeNumber"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 氏名が空だと登録が拒否される() throws Exception {
        mockMvc.perform(post("/employees").with(csrf())
                        .param("employeeNumber", "E-9002")
                        .param("name", ""))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("employeeForm", "name"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 編集が成功すると内容が更新される() throws Exception {
        Employee target = employeeRepository.save(Employee.builder()
                .employeeNumber("E-9003")
                .name("編集前")
                .build());

        mockMvc.perform(post("/employees/{id}/edit", target.getId()).with(csrf())
                        .param("employeeNumber", "E-9003")
                        .param("name", "編集後")
                        .param("department", "開発部"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employees"));

        Employee updated = employeeRepository.findById(target.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("編集後");
        assertThat(updated.getDepartment()).isEqualTo("開発部");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 存在しないIDの編集画面はエラーメッセージ付きで一覧にリダイレクトされる() throws Exception {
        mockMvc.perform(get("/employees/{id}/edit", 999999L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employees"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 貸出履歴のある社員は削除が拒否される() throws Exception {
        Lending activeLending = lendingRepository.findAllByReturnedAtIsNull().get(0);
        Long employeeId = activeLending.getEmployee().getId();

        mockMvc.perform(post("/employees/{id}/delete", employeeId).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        assertThat(employeeRepository.existsById(employeeId)).isTrue();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void 貸出履歴のない社員は削除できる() throws Exception {
        Employee noHistory = employeeRepository.save(Employee.builder()
                .employeeNumber("E-9999")
                .name("削除確認用社員")
                .build());

        mockMvc.perform(post("/employees/{id}/delete", noHistory.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("message"));

        assertThat(employeeRepository.existsById(noHistory.getId())).isFalse();
    }

    @Test
    @WithMockUser(roles = "USER")
    void USERは一覧を閲覧できる() throws Exception {
        mockMvc.perform(get("/employees")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void USERは新規登録画面にアクセスできない() throws Exception {
        mockMvc.perform(get("/employees/new")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void USERは登録できない() throws Exception {
        mockMvc.perform(post("/employees").with(csrf())
                        .param("employeeNumber", "E-8001")
                        .param("name", "テスト太郎"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void USERは編集画面にアクセスできない() throws Exception {
        Long id = employeeRepository.findAll().get(0).getId();
        mockMvc.perform(get("/employees/{id}/edit", id)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void USERは削除できない() throws Exception {
        Long id = employeeRepository.findAll().get(0).getId();
        mockMvc.perform(post("/employees/{id}/delete", id).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void ADMINは新規登録画面にアクセスできる() throws Exception {
        mockMvc.perform(get("/employees/new")).andExpect(status().isOk());
    }
}
