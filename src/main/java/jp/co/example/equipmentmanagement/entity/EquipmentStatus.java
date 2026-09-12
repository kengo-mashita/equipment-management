package jp.co.example.equipmentmanagement.entity;

/**
 * 備品の状態。LENT（貸出中）への遷移は貸出操作を通じてのみ行われ、
 * 編集画面から直接選択させてはならない（LendingServiceが管理する）。
 */
public enum EquipmentStatus {
    AVAILABLE("利用可"),
    LENT("貸出中"),
    BROKEN("故障中");

    private final String label;

    EquipmentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
