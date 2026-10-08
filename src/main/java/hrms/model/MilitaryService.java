package hrms.model;

import java.time.LocalDateTime;

/**
 * Military service entity mapped from the {@code military_service} table.
 */
public class MilitaryService {

    private long id;
    private long staffId;
    private String batchNo;
    private String serviceNo;
    private String status;
    private Long createdBy;
    private LocalDateTime createdDate;
    private Long modifiedBy;
    private LocalDateTime modifiedDate;
    private boolean deleted;
    private Long deletedBy;
    private LocalDateTime deletedDate;

    public MilitaryService() {
    }

    public static MilitaryService create(long staffId, String batchNo, String serviceNo,
                                         String status, Long createdBy) {
        MilitaryService ms = new MilitaryService();
        ms.staffId = staffId;
        ms.batchNo = batchNo;
        ms.serviceNo = serviceNo;
        ms.status = status;
        ms.createdBy = createdBy;
        return ms;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getStaffId() {
        return staffId;
    }

    public void setStaffId(long staffId) {
        this.staffId = staffId;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getServiceNo() {
        return serviceNo;
    }

    public void setServiceNo(String serviceNo) {
        this.serviceNo = serviceNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public Long getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(Long modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public LocalDateTime getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(LocalDateTime modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public Long getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(Long deletedBy) {
        this.deletedBy = deletedBy;
    }

    public LocalDateTime getDeletedDate() {
        return deletedDate;
    }

    public void setDeletedDate(LocalDateTime deletedDate) {
        this.deletedDate = deletedDate;
    }

    @Override
    public String toString() {
        return "MilitaryService{id=" + id + ", staffId=" + staffId +
                ", batchNo='" + batchNo + '\'' + ", serviceNo='" + serviceNo + '\'' +
                ", status='" + status + '\'' + '}';
    }
}
