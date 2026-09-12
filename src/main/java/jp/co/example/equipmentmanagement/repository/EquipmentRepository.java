package jp.co.example.equipmentmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import jp.co.example.equipmentmanagement.entity.Equipment;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    boolean existsByAssetNumber(String assetNumber);

    boolean existsByAssetNumberAndIdNot(String assetNumber, Long id);
}
