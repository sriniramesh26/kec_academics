package com.kec.academic.dao;

import com.kec.academic.db.DBConnection;
import com.kec.academic.engine.AssessmentEngine;
import com.kec.academic.engine.AssessmentEngine.CourseResult;
import com.kec.academic.engine.EvaluationType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Incremental marks entry, mark sheets and reports. */
public class AcademicDAO {

    // ------------------------------------------------------------------
    // Incremental marks entry ("Then and Now")
    // ------------------------------------------------------------------

    /** Phase 1: CIA Test 1 and CIA Test 2 (0-100). */
    public void recordMidSemester(long enrollmentId, Double cia1, Double cia2) throws SQLException {
        String sql = "UPDATE assessment_marks SET cia1 = COALESCE(?, cia1), cia2 = COALESCE(?, cia2) "
                   + "WHERE enrollment_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            setNullableDecimal(ps, 1, cia1);
            setNullableDecimal(ps, 2, cia2);
            ps.setLong(3, enrollmentId);
            ps.executeUpdate();
        }
    }

    /** Phase 2: assignments / quizzes / lab evaluations (0-100). */
    public void recordCiaAssignment(long enrollmentId, double score) throws SQLException {
        String sql = "UPDATE assessment_marks SET cia_assignment = ? WHERE enrollment_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, score);
            ps.setLong(2, enrollmentId);
            ps.executeUpdate();
        }
    }

    /** Phase 3: end-semester exam mark, or absent (AB). */
    public void recordEse(long enrollmentId, Double ese, boolean absent) throws SQLException {
        String sql = "UPDATE assessment_marks SET ese = ?, is_absent_ese = ? WHERE enrollment_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            setNullableDecimal(ps, 1, absent ? null : ese);
            ps.setInt(2, absent ? 1 : 0);
            ps.setLong(3, enrollmentId);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------
    // Mark sheet for one student in one semester
    // ------------------------------------------------------------------

    public static final class MarkSheetLine {
        public final String courseCode;
        public final String title;
        public final double credits;
        public final String courseType;
        public final CourseResult result;
        public final double attendancePct;

        MarkSheetLine(String courseCode, String title, double credits, String courseType,
                      CourseResult result, double attendancePct) {
            this.courseCode = courseCode;
            this.title = title;
            this.credits = credits;
            this.courseType = courseType;
            this.result = result;
            this.attendancePct = attendancePct;
        }
    }

    public List<MarkSheetLine> markSheet(String roll, String academicYear, int semester) throws SQLException {
        String sql =
            "SELECT e.enrollment_id, e.course_code, c.title, c.credits, c.course_type, "
          + "       e.classes_held, e.classes_attended, e.medical_condonation, "
          + "       m.cia1, m.cia2, m.cia_assignment, m.ese, m.is_absent_ese "
          + "FROM enrollments e "
          + "JOIN courses c ON c.course_code = e.course_code "
          + "JOIN assessment_marks m ON m.enrollment_id = e.enrollment_id "
          + "WHERE e.roll_number = ? AND e.academic_year = ? AND e.semester = ? "
          + "ORDER BY e.course_code";

        List<MarkSheetLine> lines = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, roll.toUpperCase());
            ps.setString(2, academicYear);
            ps.setInt(3, semester);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    EvaluationType type = EvaluationType.fromString(rs.getString("course_type"));
                    int held = rs.getInt("classes_held");
                    int attended = rs.getInt("classes_attended");
                    boolean condonation = rs.getInt("medical_condonation") == 1;

                    CourseResult r = AssessmentEngine.evaluate(
                            type,
                            getNullableDouble(rs, "cia1"),
                            getNullableDouble(rs, "cia2"),
                            getNullableDouble(rs, "cia_assignment"),
                            getNullableDouble(rs, "ese"),
                            rs.getInt("is_absent_ese") == 1,
                            held, attended, condonation);

                    lines.add(new MarkSheetLine(
                            rs.getString("course_code"),
                            rs.getString("title"),
                            rs.getDouble("credits"),
                            rs.getString("course_type"),
                            r,
                            AssessmentEngine.attendancePercent(held, attended)));
                }
            }
        }
        return lines;
    }

    // ------------------------------------------------------------------
    // Reports
    // ------------------------------------------------------------------

    /** Students with attendance below 65% in the given semester (SA grade). */
    public List<String> attendanceDefaulters(String academicYear, int semester) throws SQLException {
        String sql =
            "SELECT e.roll_number, s.name, e.course_code, e.classes_held, e.classes_attended, "
          + "       e.medical_condonation "
          + "FROM enrollments e JOIN students s ON s.roll_number = e.roll_number "
          + "WHERE e.academic_year = ? AND e.semester = ? AND e.classes_held > 0 "
          + "ORDER BY e.roll_number, e.course_code";

        List<String> rows = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, academicYear);
            ps.setInt(2, semester);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double pct = AssessmentEngine.attendancePercent(
                            rs.getInt("classes_held"), rs.getInt("classes_attended"));
                    if (pct < 65.0) {
                        rows.add(String.format("%-10s %-25s %-10s %6.2f%%   SA",
                                rs.getString("roll_number"), rs.getString("name"),
                                rs.getString("course_code"), pct));
                    }
                }
            }
        }
        return rows;
    }

    /** Department-wise pass / fail counts and average SGPA for the semester. */
    public List<String> departmentSummary(String academicYear, int semester) throws SQLException {
        String sql =
            "SELECT s.department, e.roll_number, e.course_code, c.credits, c.course_type, "
          + "       e.classes_held, e.classes_attended, e.medical_condonation, "
          + "       m.cia1, m.cia2, m.cia_assignment, m.ese, m.is_absent_ese "
          + "FROM enrollments e "
          + "JOIN students s ON s.roll_number = e.roll_number "
          + "JOIN courses c ON c.course_code = e.course_code "
          + "JOIN assessment_marks m ON m.enrollment_id = e.enrollment_id "
          + "WHERE e.academic_year = ? AND e.semester = ? "
          + "ORDER BY s.department, e.roll_number";

        // dept -> roll -> accumulators
        java.util.Map<String, java.util.Map<String, double[]>> acc = new java.util.TreeMap<>();
        java.util.Map<String, Integer> passCount = new java.util.HashMap<>();
        java.util.Map<String, Integer> failCount = new java.util.HashMap<>();

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, academicYear);
            ps.setInt(2, semester);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String dept = rs.getString("department");
                    String roll = rs.getString("roll_number");
                    EvaluationType type = EvaluationType.fromString(rs.getString("course_type"));
                    CourseResult r = AssessmentEngine.evaluate(type,
                            getNullableDouble(rs, "cia1"), getNullableDouble(rs, "cia2"),
                            getNullableDouble(rs, "cia_assignment"), getNullableDouble(rs, "ese"),
                            rs.getInt("is_absent_ese") == 1,
                            rs.getInt("classes_held"), rs.getInt("classes_attended"),
                            rs.getInt("medical_condonation") == 1);

                    // accumulator: [weighted grade points, credits]
                    double[] a = acc.computeIfAbsent(dept, k -> new java.util.TreeMap<>())
                                    .computeIfAbsent(roll, k -> new double[2]);
                    a[0] += rs.getDouble("credits") * r.gradePoint;
                    a[1] += rs.getDouble("credits");

                    if (r.pass) {
                        passCount.merge(dept, 1, Integer::sum);
                    } else {
                        failCount.merge(dept, 1, Integer::sum);
                    }
                }
            }
        }

        List<String> out = new ArrayList<>();
        for (var deptEntry : acc.entrySet()) {
            String dept = deptEntry.getKey();
            double sgpaSum = 0;
            int students = 0;
            for (double[] a : deptEntry.getValue().values()) {
                sgpaSum += a[1] == 0 ? 0 : a[0] / a[1];
                students++;
            }
            double avg = students == 0 ? 0 : sgpaSum / students;
            out.add(String.format("%-6s  pass=%-4d fail=%-4d  students=%-4d  avg SGPA=%.2f",
                    dept, passCount.getOrDefault(dept, 0), failCount.getOrDefault(dept, 0),
                    students, avg));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void setNullableDecimal(PreparedStatement ps, int idx, Double v) throws SQLException {
        if (v == null) {
            ps.setNull(idx, java.sql.Types.DECIMAL);
        } else {
            ps.setDouble(idx, v);
        }
    }

    private static Double getNullableDouble(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }
}
