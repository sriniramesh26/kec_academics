package com.kec.academic.dao;

import com.kec.academic.db.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Small helper to fetch an enrollment id by its natural key. */
public final class EnrollmentLookup {

    private EnrollmentLookup() {
    }

    public static long find(String roll, String courseCode, String academicYear, int semester)
            throws SQLException {
        String sql = "SELECT enrollment_id FROM enrollments "
                   + "WHERE roll_number = ? AND course_code = ? AND academic_year = ? AND semester = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, roll.toUpperCase());
            ps.setString(2, courseCode.toUpperCase());
            ps.setString(3, academicYear);
            ps.setInt(4, semester);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Enrollment not found");
                }
                return rs.getLong(1);
            }
        }
    }
}
