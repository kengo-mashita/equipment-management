package jp.co.example.equipmentmanagement.config;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import jp.co.example.equipmentmanagement.entity.Equipment;
import jp.co.example.equipmentmanagement.entity.EquipmentStatus;
import jp.co.example.equipmentmanagement.entity.Lending;
import jp.co.example.equipmentmanagement.entity.Role;
import jp.co.example.equipmentmanagement.entity.User;
import jp.co.example.equipmentmanagement.repository.EquipmentRepository;
import jp.co.example.equipmentmanagement.repository.LendingRepository;
import jp.co.example.equipmentmanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * 起動時に初期ユーザー・サンプル備品・貸出履歴を投入する（5章）。
 * 既にデータが存在する場合は何もしない（ファイルモードH2は再起動後もデータが残るため）。
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final EquipmentRepository equipmentRepository;
    private final LendingRepository lendingRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        initUsers();
        initEquipmentAndLendings();
    }

    private void initUsers() {
        if (userRepository.count() > 0) {
            log.info("初期ユーザーは投入済みのためスキップします（{}件）", userRepository.count());
            return;
        }

        userRepository.save(User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .role(Role.ADMIN)
                .build());
        userRepository.save(User.builder()
                .username("user")
                .password(passwordEncoder.encode("user123"))
                .role(Role.USER)
                .build());

        log.info("初期ユーザーを投入しました（admin / user）");
    }

    private void initEquipmentAndLendings() {
        if (equipmentRepository.count() > 0) {
            log.info("初期備品データは投入済みのためスキップします（{}件）", equipmentRepository.count());
            return;
        }

        Equipment pc1 = equipmentRepository.save(
                newEquipment("ノートPC", "EQ-0001", "本社3F倉庫", EquipmentStatus.AVAILABLE, LocalDate.of(2023, 4, 1)));
        Equipment pc2 = equipmentRepository.save(
                newEquipment("ノートPC", "EQ-0002", "本社3F倉庫", EquipmentStatus.LENT, LocalDate.of(2023, 4, 1)));
        Equipment projector1 = equipmentRepository.save(
                newEquipment("プロジェクター", "EQ-0003", "本社2F会議室", EquipmentStatus.AVAILABLE, LocalDate.of(2022, 10, 15)));
        equipmentRepository.save(
                newEquipment("プロジェクター", "EQ-0004", "本社2F会議室", EquipmentStatus.BROKEN, LocalDate.of(2021, 6, 1)));
        Equipment camera = equipmentRepository.save(
                newEquipment("デジタルカメラ", "EQ-0005", "本社1F受付", EquipmentStatus.AVAILABLE, LocalDate.of(2024, 1, 20)));
        Equipment tablet1 = equipmentRepository.save(
                newEquipment("タブレット", "EQ-0006", "本社3F倉庫", EquipmentStatus.LENT, LocalDate.of(2023, 9, 5)));
        equipmentRepository.save(
                newEquipment("タブレット", "EQ-0007", "本社3F倉庫", EquipmentStatus.AVAILABLE, LocalDate.of(2023, 9, 5)));
        equipmentRepository.save(
                newEquipment("モバイルプリンター", "EQ-0008", "本社2F事務室", EquipmentStatus.AVAILABLE, null));

        log.info("初期備品データを{}件投入しました", equipmentRepository.count());

        // 「貸出中」の備品に対応する未返却の貸出記録
        lendingRepository.save(Lending.builder()
                .equipment(pc2)
                .borrowerName("山田太郎")
                .lentAt(LocalDateTime.now().minusDays(3))
                .dueDate(LocalDate.now().plusDays(4))
                .build());
        lendingRepository.save(Lending.builder()
                .equipment(tablet1)
                .borrowerName("佐藤花子")
                .lentAt(LocalDateTime.now().minusDays(1))
                .dueDate(LocalDate.now().plusDays(6))
                .build());

        // 返却済みの履歴データ
        lendingRepository.save(Lending.builder()
                .equipment(projector1)
                .borrowerName("鈴木一郎")
                .lentAt(LocalDateTime.now().minusDays(10))
                .dueDate(LocalDate.now().minusDays(3))
                .returnedAt(LocalDateTime.now().minusDays(4))
                .build());
        lendingRepository.save(Lending.builder()
                .equipment(camera)
                .borrowerName("田中次郎")
                .lentAt(LocalDateTime.now().minusDays(20))
                .dueDate(LocalDate.now().minusDays(13))
                .returnedAt(LocalDateTime.now().minusDays(15))
                .build());

        log.info("初期貸出記録データを投入しました");
    }

    private Equipment newEquipment(String name, String assetNumber, String location,
            EquipmentStatus status, LocalDate purchaseDate) {
        return Equipment.builder()
                .name(name)
                .assetNumber(assetNumber)
                .location(location)
                .status(status)
                .purchaseDate(purchaseDate)
                .build();
    }
}
