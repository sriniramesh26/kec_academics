package com.kec.academic;

import com.kec.academic.dao.AcademicDAO;
import com.kec.academic.dao.StudentDAO;
import com.kec.academic.engine.AssessmentEngine;
import com.kec.academic.engine.EvaluationType;

import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/** Menu-driven console interface for KEC R2024 data entry and reporting. */
public class MainApp {

    private static final String ACADEMIC_YEAR = "2026-2027";

    private final Scanner in = new Scanner(System.in);
    private final StudentDAO studentDAO = new StudentDAO();
    private final AcademicDAO academicDAO = new AcademicDAO();

    public static void main(String[] args) {
        new MainApp().run();
    }

    private void run() {
        System.out.println("=== KEC Academic & Continuous Evaluation System (R2024) ===");
        boolean running = true;
        while (running) {
            System.out.println();
            System.out.println("1. Add student");
            System.out.println("2. Add course");
            System.out.println("3. Enrol student in course (with attendance)");
            System.out.println("4. Update attendance");
            System.out.println("5. Enter CIA Test 1 / CIA Test 2 (mid-semester)");
            System.out.println("6. Enter Assignment / Quiz / Lab (CIA)");
            System.out.println("7. Enter End Semester Exam (ESE) or mark AB");
            System.out.println("8. Student semester mark sheet");
            System.out.println("9. Attendance defaulter report");
            System.out.println("10. Department-wise result summary");
            System.out.println("0. Exit");
            System.out.print("Choose: ");

            String choice = in.nextLine().trim();
            try {
                switch (choice) {
                    case "1":  addStudent(); break;
                    case "2":  addCourse(); break;
                    case "3":  enroll(); break;
                    case "4":  updateAttendance(); break;
                    case "5":  enterMidSemester(); break;
                    case "6":  enterAssignment(); break;
                    case "7":  enterEse(); break;
                    case "8":  markSheet(); break;
                    case "9":  defaulterReport(); break;
                    case "10": departmentSummary(); break;
                    case "0":  running = false; break;
                    default:   System.out.println("Invalid option.");
                }
            } catch (SQLException e) {
                System.out.println("Database error: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                System.out.println("Input error: " + e.getMessage());
            }
        }
        System.out.println("Goodbye.");
    }

    // ------------------------------------------------------------------
    // 1. Data entry
    // ------------------------------------------------------------------

    private void addStudent() throws SQLException {
        String roll = prompt("Roll number (e.g. 24ITR001): ");
        String name = prompt("Name: ");
        String dept = prompt("Department (CSE, ECE, MECH, ...): ");
        int sem = promptInt("Current semester (1-8): ", 1, 8);
        studentDAO.addStudent(roll, name, dept, sem);
        System.out.println("Student added.");
    }

    private void addCourse() throws SQLException {
        String code = prompt("Course code (e.g. 24ITR31): ");
        String title = prompt("Title: ");
        double credits = promptDouble("Credits: ");
        String type = prompt("Type (THEORY / PRACTICAL / EMBEDDED): ").toUpperCase();
        EvaluationType.fromString(type); // validates the type
        studentDAO.addCourse(code, title, credits, type);
        System.out.println("Course added.");
    }

    private void enroll() throws SQLException {
        String roll = prompt("Roll number: ");
        String code = prompt("Course code: ");
        int sem = promptInt("Semester (1-8): ", 1, 8);
        requireExists(roll, code);
        studentDAO.enroll(roll, code, ACADEMIC_YEAR, sem);
        int held = promptInt("Classes held so far: ", 0, 10000);
        int attended = promptInt("Classes attended: ", 0, held);
        long id = findEnrollmentId(roll, code, sem);
        studentDAO.updateAttendance(id, held, attended);
        System.out.println("Enrolled. Enrollment id = " + id);
    }

    private void updateAttendance() throws SQLException {
        long id = promptLong("Enrollment id: ");
        int held = promptInt("Total classes held: ", 0, 10000);
        int attended = promptInt("Classes attended: ", 0, held);
        studentDAO.updateAttendance(id, held, attended);
        boolean condonation = promptYesNo("Medical condonation approved? (y/n): ");
        studentDAO.setMedicalCondonation(id, condonation);
        System.out.println("Attendance updated.");
    }

    // ------------------------------------------------------------------
    // 2. Incremental marks entry
    // ------------------------------------------------------------------

    private void enterMidSemester() throws SQLException {
        long id = promptLong("Enrollment id: ");
        Double cia1 = promptOptionalMark("CIA Test 1 (0-100, blank to skip): ");
        Double cia2 = promptOptionalMark("CIA Test 2 (0-100, blank to skip): ");
        academicDAO.recordMidSemester(id, cia1, cia2);
        System.out.println("CIA 1/2 recorded.");
    }

    private void enterAssignment() throws SQLException {
        long id = promptLong("Enrollment id: ");
        double score = promptMark("Assignment / Quiz / Lab score (0-100): ");
        academicDAO.recordCiaAssignment(id, score);
        System.out.println("Assignment/Lab score recorded.");
    }

    private void enterEse() throws SQLException {
        long id = promptLong("Enrollment id: ");
        boolean absent = promptYesNo("Absent for ESE (AB)? (y/n): ");
        Double ese = absent ? null : promptMark("ESE mark (0-100): ");
        academicDAO.recordEse(id, ese, absent);
        System.out.println(absent ? "Recorded as AB." : "ESE recorded.");
    }

    // ------------------------------------------------------------------
    // 3. Reports
    // ------------------------------------------------------------------

    private void markSheet() throws SQLException {
        String roll = prompt("Roll number: ");
        int sem = promptInt("Semester (1-8): ", 1, 8);
        List<AcademicDAO.MarkSheetLine> lines = academicDAO.markSheet(roll, ACADEMIC_YEAR, sem);
        if (lines.isEmpty()) {
            System.out.println("No enrolments found for " + roll + " in semester " + sem + ".");
            return;
        }

        System.out.println();
        System.out.println("KONGU ENGINEERING COLLEGE (AUTONOMOUS) - REGULATION 2024");
        System.out.println("Semester Mark Sheet : " + roll.toUpperCase() + "  Sem " + sem + "  " + ACADEMIC_YEAR);
        System.out.println("---------------------------------------------------------------------------------------------");
        System.out.printf("%-9s %-26s %-4s %-9s %6s %6s %7s %-5s %-6s %-7s%n",
                "Code", "Title", "Cr", "Type", "CIA", "ESE", "Total", "Grade", "Result", "Att%");
        System.out.println("---------------------------------------------------------------------------------------------");

        double[] credits = new double[lines.size()];
        int[] gps = new int[lines.size()];
        int i = 0;
        for (AcademicDAO.MarkSheetLine l : lines) {
            AssessmentEngine.CourseResult r = l.result;
            System.out.printf("%-9s %-26s %-4.1f %-9s %6s %6s %7s %-5s %-6s %6.2f%%%n",
                    l.courseCode, truncate(l.title, 26), l.credits, l.courseType,
                    fmt(r.ciaMark), fmt(r.eseMark), fmt(r.totalMark),
                    r.grade, r.pass ? "PASS" : "FAIL", l.attendancePct);
            credits[i] = l.credits;
            gps[i] = r.gradePoint;
            i++;
        }

        double sgpa = AssessmentEngine.sgpa(credits, gps);
        System.out.println("---------------------------------------------------------------------------------------------");
        System.out.printf("SGPA = %.2f   Equivalent Percentage = %.2f%%%n",
                sgpa, AssessmentEngine.equivalentPercentage(sgpa));
        System.out.println("Note: SGPA counts all enrolled credits; RA / SA / AB score 0 grade points.");
    }

    private void defaulterReport() throws SQLException {
        int sem = promptInt("Semester (1-8): ", 1, 8);
        List<String> rows = academicDAO.attendanceDefaulters(ACADEMIC_YEAR, sem);
        System.out.println();
        System.out.println("ATTENDANCE DEFAULTER REPORT (below 65%) - Semester " + sem);
        System.out.println("Roll       Name                      Course     Att%   Grade");
        System.out.println("-----------------------------------------------------------------");
        if (rows.isEmpty()) {
            System.out.println("No defaulters.");
        } else {
            rows.forEach(System.out::println);
        }
    }

    private void departmentSummary() throws SQLException {
        int sem = promptInt("Semester (1-8): ", 1, 8);
        List<String> rows = academicDAO.departmentSummary(ACADEMIC_YEAR, sem);
        System.out.println();
        System.out.println("CLASS PERFORMANCE & RESULT SUMMARY - Semester " + sem);
        System.out.println("---------------------------------------------------------------");
        if (rows.isEmpty()) {
            System.out.println("No results found.");
        } else {
            rows.forEach(System.out::println);
        }
    }

    // ------------------------------------------------------------------
    // Input helpers
    // ------------------------------------------------------------------

    private void requireExists(String roll, String code) throws SQLException {
        if (!studentDAO.studentExists(roll)) {
            throw new IllegalArgumentException("Student " + roll + " does not exist.");
        }
        if (!studentDAO.courseExists(code)) {
            throw new IllegalArgumentException("Course " + code + " does not exist.");
        }
    }

    private long findEnrollmentId(String roll, String code, int sem) throws SQLException {
        // Looks up the id just created so attendance can be set in the same step.
        return com.kec.academic.dao.EnrollmentLookup.find(roll, code, ACADEMIC_YEAR, sem);
    }

    private String prompt(String msg) {
        System.out.print(msg);
        return in.nextLine().trim();
    }

    private int promptInt(String msg, int min, int max) {
        while (true) {
            try {
                int v = Integer.parseInt(prompt(msg));
                if (v < min || v > max) {
                    throw new NumberFormatException();
                }
                return v;
            } catch (NumberFormatException e) {
                System.out.println("Enter a whole number between " + min + " and " + max + ".");
            }
        }
    }

    private long promptLong(String msg) {
        while (true) {
            try {
                return Long.parseLong(prompt(msg));
            } catch (NumberFormatException e) {
                System.out.println("Enter a valid number.");
            }
        }
    }

    private double promptDouble(String msg) {
        while (true) {
            try {
                return Double.parseDouble(prompt(msg));
            } catch (NumberFormatException e) {
                System.out.println("Enter a valid number.");
            }
        }
    }

    /** Mark between 0 and 100 inclusive. */
    private double promptMark(String msg) {
        while (true) {
            try {
                double v = Double.parseDouble(prompt(msg));
                if (v < 0 || v > 100) {
                    throw new NumberFormatException();
                }
                return v;
            } catch (NumberFormatException e) {
                System.out.println("Mark must be between 0 and 100.");
            }
        }
    }

    /** Blank input returns null (skip this field). */
    private Double promptOptionalMark(String msg) {
        while (true) {
            String s = prompt(msg);
            if (s.isEmpty()) {
                return null;
            }
            try {
                double v = Double.parseDouble(s);
                if (v < 0 || v > 100) {
                    throw new NumberFormatException();
                }
                return v;
            } catch (NumberFormatException e) {
                System.out.println("Mark must be between 0 and 100, or blank to skip.");
            }
        }
    }

    private boolean promptYesNo(String msg) {
        return prompt(msg).toLowerCase().startsWith("y");
    }

    private static String fmt(Double d) {
        return d == null ? "-" : String.format("%.2f", d);
    }

    private static String truncate(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }
}
