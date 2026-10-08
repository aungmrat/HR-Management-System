package hrms.model;

import java.time.LocalDateTime;

/**
 * Language skill entity mapped from the {@code language_skill} table.
 */
public class LanguageSkill {

    private long id;
    private long staffId;
    private String languageName;
    private LanguageProficiency speaking;
    private LanguageProficiency listening;
    private LanguageProficiency writing;
    private LanguageProficiency reading;
    private Long createdBy;
    private LocalDateTime createdDate;
    private Long modifiedBy;
    private LocalDateTime modifiedDate;
    private boolean deleted;
    private Long deletedBy;
    private LocalDateTime deletedDate;

    public LanguageSkill() {
    }

    public LanguageSkill(long id, long staffId, String languageName,
                         LanguageProficiency speaking, LanguageProficiency listening,
                         LanguageProficiency writing, LanguageProficiency reading,
                         Long createdBy, LocalDateTime createdDate) {
        this.id = id;
        this.staffId = staffId;
        this.languageName = languageName;
        this.speaking = speaking;
        this.listening = listening;
        this.writing = writing;
        this.reading = reading;
        this.createdBy = createdBy;
        this.createdDate = createdDate;
    }

    public static LanguageSkill create(long staffId, String languageName,
                                       LanguageProficiency speaking, LanguageProficiency listening,
                                       LanguageProficiency writing, LanguageProficiency reading,
                                       Long createdBy) {
        LanguageSkill ls = new LanguageSkill();
        ls.staffId = staffId;
        ls.languageName = languageName;
        ls.speaking = speaking;
        ls.listening = listening;
        ls.writing = writing;
        ls.reading = reading;
        ls.createdBy = createdBy;
        return ls;
    }

    // Getters and setters
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

    public String getLanguageName() {
        return languageName;
    }

    public void setLanguageName(String languageName) {
        this.languageName = languageName;
    }

    public LanguageProficiency getSpeaking() {
        return speaking;
    }

    public void setSpeaking(LanguageProficiency speaking) {
        this.speaking = speaking;
    }

    public LanguageProficiency getListening() {
        return listening;
    }

    public void setListening(LanguageProficiency listening) {
        this.listening = listening;
    }

    public LanguageProficiency getWriting() {
        return writing;
    }

    public void setWriting(LanguageProficiency writing) {
        this.writing = writing;
    }

    public LanguageProficiency getReading() {
        return reading;
    }

    public void setReading(LanguageProficiency reading) {
        this.reading = reading;
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
        return "LanguageSkill{id=" + id + ", staffId=" + staffId +
                ", languageName='" + languageName + '\'' +
                ", speaking=" + speaking + ", listening=" + listening +
                ", writing=" + writing + ", reading=" + reading + '}';
    }

    /**
     * Compares data fields (excluding audit fields) for change detection.
     */
    public boolean equalsData(LanguageSkill other) {
        if (other == null) return false;
        return (languageName != null ? languageName.equals(other.languageName) : other.languageName == null)
                && speaking == other.speaking
                && listening == other.listening
                && writing == other.writing
                && reading == other.reading;
    }
}