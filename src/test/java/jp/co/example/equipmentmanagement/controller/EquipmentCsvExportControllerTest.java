package jp.co.example.equipmentmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import jp.co.example.equipmentmanagement.entity.Equipment;
import jp.co.example.equipmentmanagement.entity.EquipmentStatus;
import jp.co.example.equipmentmanagement.repository.EquipmentRepository;
import jp.co.example.equipmentmanagement.service.EquipmentService;

/**
 * 備品一覧CSVダウンロード（GET /equipment/csv）の結合テスト。
 * レスポンスヘッダー・BOM・列見出し・ロールごとの利用可否・検索条件での絞り込み・不正条件・一覧画面のリンクを確認する。
 * 初期データが存在しうるため、期待値は EquipmentService.search の結果または本テストで作成した行で判定する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EquipmentCsvExportControllerTest {

    private static final String HEADER = "品名,管理番号,保管場所,状態,購入日,借用者\r\n";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private EquipmentService equipmentService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void ADMINは全件のCSVをダウンロードできる() throws Exception {
        equipmentRepository.save(equipment("CSV試験機器, 15インチ", "EQ-CSV-001", EquipmentStatus.AVAILABLE));

        MvcResult result = mockMvc.perform(get("/equipment/csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv;charset=UTF-8")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        matchesPattern("attachment; filename=\"equipment_\\d{8}_\\d{6}\\.csv\"")))
                .andReturn();

        byte[] csv = result.getResponse().getContentAsByteArray();
        assertThat(Arrays.copyOf(csv, 3)).containsExactly(0xEF, 0xBB, 0xBF);
        String body = body(csv);
        assertThat(body).startsWith(HEADER);
        // カンマを含む品名は1セルとしてクォートされる
        assertThat(body).contains("\"CSV試験機器, 15インチ\",EQ-CSV-001,");
        // 見出し行 + 全備品の行
        assertThat(dataLines(body)).hasSize(equipmentService.search(null, null).size());
    }

    @Test
    void USERもADMINと同じ内容のCSVをダウンロードできる() throws Exception {
        equipmentRepository.save(equipment("CSV試験機器", "EQ-CSV-002", EquipmentStatus.AVAILABLE));

        byte[] adminCsv = mockMvc.perform(get("/equipment/csv").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        byte[] userCsv = mockMvc.perform(get("/equipment/csv").with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(userCsv).isEqualTo(adminCsv);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void ADMINの一覧画面にCSVダウンロードのリンクが表示される() throws Exception {
        mockMvc.perform(get("/equipment"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/equipment/csv")))
                .andExpect(content().string(containsString("CSVダウンロード")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void USERの一覧画面にもCSVダウンロードのリンクが表示される() throws Exception {
        mockMvc.perform(get("/equipment"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/equipment/csv")))
                .andExpect(content().string(containsString("CSVダウンロード")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void 状態で絞り込むと該当する状態の備品のみを一覧と同じ順序で出力する() throws Exception {
        saveFilterTestData();
        List<Equipment> expected = equipmentService.search(null, EquipmentStatus.BROKEN);

        List<String> lines = dataLines(downloadCsv("/equipment/csv?status=BROKEN"));

        assertThat(lines).hasSize(expected.size());
        for (int i = 0; i < lines.size(); i++) {
            assertThat(lines.get(i)).contains("," + expected.get(i).getAssetNumber() + ",").contains(",故障中,");
        }
    }

    @Test
    @WithMockUser(roles = "USER")
    void 品名で絞り込むと部分一致する備品のみをID順で出力する() throws Exception {
        saveFilterTestData();

        List<String> lines = dataLines(downloadCsv("/equipment/csv?name=CSV絞込試験"));

        assertThat(lines).containsExactly(
                "CSV絞込試験A,EQ-CSV-101,,故障中,,",
                "CSV絞込試験B,EQ-CSV-102,,利用可,,",
                "CSV絞込試験C,EQ-CSV-103,,故障中,,");
    }

    @Test
    @WithMockUser(roles = "USER")
    void 品名と状態の両方で絞り込むと両条件を満たす備品のみを出力する() throws Exception {
        saveFilterTestData();

        List<String> lines = dataLines(downloadCsv("/equipment/csv?name=CSV絞込試験&status=BROKEN"));

        assertThat(lines).containsExactly(
                "CSV絞込試験A,EQ-CSV-101,,故障中,,",
                "CSV絞込試験C,EQ-CSV-103,,故障中,,");
    }

    @Test
    @WithMockUser(roles = "USER")
    void 一致する備品がなければ列見出し行のみを出力する() throws Exception {
        assertThat(downloadCsv("/equipment/csv?name=存在しない品名")).isEqualTo(HEADER);
    }

    @Test
    @WithMockUser(roles = "USER")
    void 空文字の検索条件は条件なしとして全件を出力する() throws Exception {
        assertThat(dataLines(downloadCsv("/equipment/csv?name=&status=")))
                .hasSize(equipmentService.search(null, null).size());
    }

    @ParameterizedTest
    @ValueSource(strings = { "UNKNOWN", "available" })
    @WithMockUser(roles = "USER")
    void 不正な状態を指定するとエラーメッセージ付きで一覧へリダイレクトされる(String status) throws Exception {
        mockMvc.perform(get("/equipment/csv").param("status", status))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/equipment"))
                .andExpect(flash().attribute("error", "検索条件が不正です。条件を指定し直してください。"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    @WithMockUser(roles = "USER")
    void 一覧画面のCSVリンクは現在適用されている検索条件を引き継ぐ() throws Exception {
        mockMvc.perform(get("/equipment").param("name", "PC").param("status", "BROKEN"))
                .andExpect(status().isOk())
                .andExpect(content().string(matchesPattern(
                        "(?s).*href=\"/equipment/csv\\?name=PC&amp;status=BROKEN\".*")));
    }

    /** 絞り込み確認用の備品（品名は初期データと重ならない一意な文字列）を ID 昇順になるよう保存する */
    private void saveFilterTestData() {
        equipmentRepository.save(equipment("CSV絞込試験A", "EQ-CSV-101", EquipmentStatus.BROKEN));
        equipmentRepository.save(equipment("CSV絞込試験B", "EQ-CSV-102", EquipmentStatus.AVAILABLE));
        equipmentRepository.save(equipment("CSV絞込試験C", "EQ-CSV-103", EquipmentStatus.BROKEN));
    }

    /** CSVをダウンロードし、BOM を除いた本文を返す */
    private String downloadCsv(String url) throws Exception {
        byte[] csv = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        return body(csv);
    }

    private String body(byte[] csv) {
        return new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
    }

    /** 見出し行を除いたデータ行（本テストのデータは値に改行を含まないため CRLF で分割できる） */
    private List<String> dataLines(String body) {
        List<String> lines = Arrays.asList(body.split("\r\n"));
        return lines.subList(1, lines.size());
    }

    private Equipment equipment(String name, String assetNumber, EquipmentStatus status) {
        return Equipment.builder()
                .name(name)
                .assetNumber(assetNumber)
                .status(status)
                .build();
    }
}
