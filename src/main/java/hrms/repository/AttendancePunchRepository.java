package hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import hrms.model.AttendancePunch;

/**
 * JDBC access for the attendance_punch table: the ordered IN/OUT punches
 * behind one attendance summary row.
 */
public final class AttendancePunchRepository {

  private AttendancePunchRepository() {
  }

  /** Punches of one attendance row, ordered by sequence. */
  public static List<AttendancePunch> listByAttendance(long attendanceId) {
    return Sql.query("""
            SELECT id, attendance_id, punch_seq, punch_time, punch_type
              FROM attendance_punch
             WHERE attendance_id = ?
             ORDER BY punch_seq
            """,
            AttendancePunchRepository::mapRow,
            attendanceId);
  }

  /** Punch times only, ordered — the shape the UI editor works with. */
  public static List<LocalTime> listTimesByAttendance(long attendanceId) {
    List<LocalTime> times = new ArrayList<>();
    for (AttendancePunch punch : listByAttendance(attendanceId)) {
      times.add(punch.getPunchTime());
    }
    return times;
  }

  /** Replaces all punches of an attendance row with the given ordered times. */
  public static void replaceAll(long attendanceId, List<LocalTime> times) {
    Sql.update("DELETE FROM attendance_punch WHERE attendance_id = ?", attendanceId);
    if (times == null || times.isEmpty()) {
      return;
    }
    int seq = 1;
    for (LocalTime time : times) {
      insertRow(attendanceId, seq, time, seq % 2 == 1 ? "IN" : "OUT");
      seq++;
    }
  }

  /** Soft-deleted attendance keeps its punch rows; hard delete on purge. */
  public static void deleteAll(long attendanceId) {
    Sql.update("DELETE FROM attendance_punch WHERE attendance_id = ?", attendanceId);
  }

  private static void insertRow(long attendanceId, int seq, LocalTime time, String type) {
    Sql.insert("""
            INSERT INTO attendance_punch
              (attendance_id, punch_seq, punch_time, punch_type)
            VALUES (?, ?, ?, ?)
            """,
            attendanceId, seq, time, type);
  }

  private static AttendancePunch mapRow(ResultSet rs) throws SQLException {
    AttendancePunch punch = new AttendancePunch();
    punch.setId(rs.getLong("id"));
    punch.setAttendanceId(rs.getLong("attendance_id"));
    punch.setPunchSeq(rs.getInt("punch_seq"));
    punch.setPunchTime(rs.getObject("punch_time", LocalTime.class));
    punch.setPunchType(rs.getString("punch_type"));
    return punch;
  }
}
