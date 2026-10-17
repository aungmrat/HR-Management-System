package hrms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * GazettedHoliday entity mapped from the {@code gazetted_holiday} table.
 * A holiday covers every date from {@code startDate} to {@code endDate}
 * inclusive; recurring is a flag only (Myanmar holidays move every year, so
 * each year's dates are stored as explicit rows).
 */
public class GazettedHoliday {

  private long id;
  private LocalDate startDate;
  private LocalDate endDate;
  private String title;
  private String remark;
  private boolean recurring;
  private String status = "Active";
  private Long createdBy;
  private LocalDateTime createdDate;
  private Long modifiedBy;
  private LocalDateTime modifiedDate;
  private boolean deleted;
  private Long deletedBy;
  private LocalDateTime deletedDate;
  /** Display name of the creator, resolved by the repository via user/staff join. */
  private String creatorName;

  public GazettedHoliday() {
  }

  /** New unsaved holiday for insert (id = 0 until persisted). */
  public static GazettedHoliday create(LocalDate startDate, LocalDate endDate,
          String title, String remark, boolean recurring, String status, Long createdBy) {
    GazettedHoliday holiday = new GazettedHoliday();
    holiday.startDate = startDate;
    holiday.endDate = endDate;
    holiday.title = title;
    holiday.remark = remark;
    holiday.recurring = recurring;
    holiday.status = status == null || status.isBlank() ? "Active" : status;
    holiday.createdBy = createdBy;
    return holiday;
  }

  /** Day count of the range, at least 1 for a valid row. */
  public int getDays() {
    if (startDate == null || endDate == null) {
      return 0;
    }
    return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
  }

  /** True when this holiday covers {@code date}. */
  public boolean covers(LocalDate date) {
    return startDate != null && endDate != null
            && !date.isBefore(startDate) && !date.isAfter(endDate);
  }

  public boolean isActive() {
    return status == null || status.isBlank() || "Active".equalsIgnoreCase(status);
  }

  /**
   * Row shape expected by {@code HrmsTable}:
   * ID, Title, From, To, Days, Recurring, Status, Created By, Created Date.
   */
  public Object[] toTableRow() {
    return new Object[]{
      id,
      title == null ? "" : title,
      startDate == null ? "" : startDate.toString(),
      endDate == null ? "" : endDate.toString(),
      getDays(),
      recurring ? "Yes" : "No",
      status == null || status.isBlank() ? "Active" : status,
      creatorName != null ? creatorName
              : createdBy == null ? "—" : String.valueOf(createdBy),
      createdDate == null ? "" : createdDate.toString().replace('T', ' ')
    };
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getRemark() {
    return remark;
  }

  public void setRemark(String remark) {
    this.remark = remark;
  }

  public boolean isRecurring() {
    return recurring;
  }

  public void setRecurring(boolean recurring) {
    this.recurring = recurring;
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

  public String getCreatorName() {
    return creatorName;
  }

  public void setCreatorName(String creatorName) {
    this.creatorName = creatorName;
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
}
