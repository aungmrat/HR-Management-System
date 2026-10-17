package hrms.model;

import java.time.LocalTime;

/**
 * One punch (IN or OUT) of a staff member's day, stored in
 * {@code attendance_punch}. Odd sequences are IN, even sequences are OUT;
 * the type column is kept for audit but derived from the sequence.
 */
public class AttendancePunch {

  private long id;
  private long attendanceId;
  private int punchSeq;
  private LocalTime punchTime;
  private String punchType;

  public AttendancePunch() {
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getAttendanceId() {
    return attendanceId;
  }

  public void setAttendanceId(long attendanceId) {
    this.attendanceId = attendanceId;
  }

  public int getPunchSeq() {
    return punchSeq;
  }

  public void setPunchSeq(int punchSeq) {
    this.punchSeq = punchSeq;
  }

  public LocalTime getPunchTime() {
    return punchTime;
  }

  public void setPunchTime(LocalTime punchTime) {
    this.punchTime = punchTime;
  }

  public String getPunchType() {
    return punchType;
  }

  public void setPunchType(String punchType) {
    this.punchType = punchType;
  }
}
