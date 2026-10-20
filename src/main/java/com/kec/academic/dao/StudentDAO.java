package com.kec.academic.dao;

import com.kec.academic.db.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** CRUD for students and courses. */
public class StudentDAO {

    public void addStudent(String roll, String name, String dept, int semester) throws SQLException {
        String sql = "INSERT INTO students (roll_number, name, department, current_semester) VALUES (?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, roll.toUpperCase());
            ps.setString(2, name);
            ps.setString(3, dept.toUpperCase());
            ps.setInt(4, semester);
            ps.executeUpdate();
        }
    }

    public void addCourse(String code, String title, double credits, String type) throws SQLException {
        // ?::course_type_t casts the text value to the PostgreSQL enum type
        String sql = "INSERT INTO courses (course_code, title, credits, course_type) VALUES (?,?,?,?::course_type_t)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, code.toUpperCase());
            ps.setString(2, title);
            ps.setDouble(3, credits);
            ps.setString(4, type.toUpperCase());
            ps.executeUpdate();
        }
    }

    /** Enrols a student and creates the empty marks row in the same transaction. */
    public void enroll(String roll, String courseCode, String academicYear, int semester) throws SQLException {
        String insEnr = "INSERT INTO enrollments (roll_number, course_code, academic_year, semester) VALUES (?,?,?,?)";
        String insMarks = "INSERT INTO assessment_marks (enrollment_id) VALUES (?)";
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(insEnr,
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, roll.toUpperCase());
                ps.setString(2, courseCode.toUpperCase());
                ps.setString(3, academicYear);
                ps.setInt(4, semester);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    try (PreparedStatement pm = con.prepareStatement(insMarks)) {
                        pm.setLong(1, keys.getLong(1));
                        pm.executeUpdate();
                    }
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    public void updateAttendance(long enrollmentId, int held, int attended) throws SQLException {
        String sql = "UPDATE enrollments SET classes_held = ?, classes_attended = ? WHERE enrollment_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, held);
            ps.setInt(2, attended);
            ps.setLong(3, enrollmentId);
            ps.executeUpdate();
        }
    }

    public void setMedicalCondonation(long enrollmentId, boolean approved) throws SQLException {
        String sql = "UPDATE enrollments SET medical_condonation = ? WHERE enrollment_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, approved ? 1 : 0);
            ps.setLong(2, enrollmentId);
            ps.executeUpdate();
        }
    }

    public boolean studentExists(String roll) throws SQLException {
        String sql = "SELECT 1 FROM students WHERE roll_number = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, roll.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean courseExists(String code) throws SQLException {
        String sql = "SELECT 1 FROM courses WHERE course_code = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, code.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
