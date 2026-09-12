package jp.co.example.equipmentmanagement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import jp.co.example.equipmentmanagement.entity.Equipment;
import jp.co.example.equipmentmanagement.entity.Lending;

public interface LendingRepository extends JpaRepository<Lending, Long> {

    Optional<Lending> findByEquipmentAndReturnedAtIsNull(Equipment equipment);

    List<Lending> findAllByReturnedAtIsNull();

    List<Lending> findAllByOrderByLentAtDesc();

    boolean existsByEquipment(Equipment equipment);
}
