package hrms.model;

import java.time.LocalDateTime;

/**
 * A row of the polymorphic {@code file} table: {@code source_type +
 * source_id} points at the owning record (currently {@code STAFF} documents),
 * while the bytes live on disk under {@code file_path}.
 */
public class StoredFile {

  private long id;
  private long sourceId;
  private String sourceType;
  private String fileName;
  private String filePath;
  private String fileExt;
  private Long createdBy;
  private LocalDateTime createdDate;

  /** Display only: staff name when {@code source_type = 'STAFF'}. */
  private String staffName;

  /** Display only: creator staff name or username. */
  private String creatorName;

  public static StoredFile of(long sourceId, String sourceType, String fileName,
          String filePath, String fileExt) {
    StoredFile file = new StoredFile();
    file.sourceId = sourceId;
    file.sourceType = sourceType;
    file.fileName = fileName;
    file.filePath = filePath;
    file.fileExt = fileExt;
    return file;
  }

  /**
   * Row shape expected by {@code HrmsTable}:
   * ID, Name, Type, Source, Uploaded By, Uploaded.
   */
  public Object[] toTableRow() {
    return new Object[]{
      id,
      fileName == null ? "" : fileName,
      fileExt == null ? "" : fileExt,
      staffName != null ? staffName
              : sourceType == null ? "" : sourceType + " #" + sourceId,
      creatorName != null ? creatorName
              : createdBy == null ? "" : String.valueOf(createdBy),
      createdDate == null ? "" : createdDate.toString().replace('T', ' ')
    };
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getSourceId() {
    return sourceId;
  }

  public void setSourceId(long sourceId) {
    this.sourceId = sourceId;
  }

  public String getSourceType() {
    return sourceType;
  }

  public void setSourceType(String sourceType) {
    this.sourceType = sourceType;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public String getFileExt() {
    return fileExt;
  }

  public void setFileExt(String fileExt) {
    this.fileExt = fileExt;
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

  public String getStaffName() {
    return staffName;
  }

  public void setStaffName(String staffName) {
    this.staffName = staffName;
  }

  public String getCreatorName() {
    return creatorName;
  }

  public void setCreatorName(String creatorName) {
    this.creatorName = creatorName;
  }
}
