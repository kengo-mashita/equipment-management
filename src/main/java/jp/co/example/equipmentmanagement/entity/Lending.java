package jp.co.example.equipmentmanagement.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 貸出記録。1備品につき有効な記録（returnedAtがnull）は同時に1件のみ
 * （一意性の担保はLendingServiceの業務ロジックで行う）。
 */
@Entity
@Table(name = "lending")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lending {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(name = "borrower_name", nullable = false)
    private String borrowerName;

    @Column(name = "lent_at", nullable = false, updatable = false)
    private LocalDateTime lentAt;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @PrePersist
    protected void onCreate() {
        if (this.lentAt == null) {
            this.lentAt = LocalDateTime.now();
        }
    }
}
