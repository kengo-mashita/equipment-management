package jp.co.example.equipmentmanagement.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import jp.co.example.equipmentmanagement.entity.Equipment;
import jp.co.example.equipmentmanagement.entity.Lending;

public interface LendingRepository extends JpaRepository<Lending, Long> {

    Optional<Lending> findByEquipmentAndReturnedAtIsNull(Equipment equipment);
}
