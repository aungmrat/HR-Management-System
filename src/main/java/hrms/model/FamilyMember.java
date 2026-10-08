package hrms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Family member entity mapped from the {@code family_member} table.
 */
public class FamilyMember {

    private long id;
    private long staffId;
    private String name;
    private String relationship;
    private LocalDate dob;
    private String address;
    private String phone;
    private Long createdBy;
    private LocalDateTime createdDate;
    private Long modifiedBy;
    private LocalDateTime modifiedDate;
    private boolean deleted;
    private Long deletedBy;
    private LocalDateTime deletedDate;

    public FamilyMember() {
    }

    public static FamilyMember create(long staffId, String name, String relationship,
                                      LocalDate dob, String address, String phone,
                                      Long createdBy) {
        FamilyMember member = new FamilyMember();
        member.staffId = staffId;
        member.name = name;
        member.relationship = relationship;
        member.dob = dob;
        member.address = address;
        member.phone = phone;
        member.createdBy = createdBy;
        return member;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public LocalDate getDob() {
        return dob;
    }

    public void setDob(LocalDate dob) {
        this.dob = dob;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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
        return "FamilyMember{id=" + id + ", staffId=" + staffId +
                ", name='" + name + '\'' + ", relationship='" + relationship + '\'' + '}';
    }
}
