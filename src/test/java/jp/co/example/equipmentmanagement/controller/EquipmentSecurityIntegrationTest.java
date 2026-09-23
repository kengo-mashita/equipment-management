package jp.co.example.equipmentmanagement.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jp.co.example.equipmentmanagement.repository.EquipmentRepository;

/**
 * 4.1節のロール（ROLE_ADMIN/ROLE_USER）に基づくアクセス制御が
 * SecurityConfigの設定通りに機能することを確認する結合テスト。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EquipmentSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Test
    void 未認証でアクセスするとログイン画面へリダイレクトされる() throws Exception {
        mockMvc.perform(get("/equipment"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void 未認証でCSVダウンロードにアクセスするとログイン画面へリダイレクトされる() throws Exception {
        mockMvc.perform(get("/equipment/csv"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    @WithMockUser(roles = "USER")
    void USERは一覧を閲覧できる() throws Exception {
        mockMvc.perform(get("/equipment")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void USERは新規登録画面にアクセスできない() throws Exception {
        mockMvc.perform(get("/equipment/new")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void USERは登録できない() throws Exception {
        mockMvc.perform(post("/equipment").with(csrf())
                        .param("name", "テスト機器")
                        .param("assetNumber", "EQ-8001"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void USERは編集画面にアクセスできない() throws Exception {
        Long id = equipmentRepository.findAll().get(0).getId();
        mockMvc.perform(get("/equipment/{id}/edit", id)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void USERは削除できない() throws Exception {
        Long id = equipmentRepository.findAll().get(0).getId();
        mockMvc.perform(post("/equipment/{id}/delete", id).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void ADMINは新規登録画面にアクセスできる() throws Exception {
        mockMvc.perform(get("/equipment/new")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void ADMINは編集画面にアクセスできる() throws Exception {
        Long id = equipmentRepository.findAll().get(0).getId();
        mockMvc.perform(get("/equipment/{id}/edit", id)).andExpect(status().isOk());
    }

    @Test
    void ログイン画面は未認証でも表示できる() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
    }
}
