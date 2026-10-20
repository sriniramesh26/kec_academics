DROP DATABASE IF EXISTS kec_academic;
CREATE DATABASE kec_academic;
DROP TABLE IF EXISTS assessment_marks;
DROP TABLE IF EXISTS enrollments;
DROP TABLE IF EXISTS courses;
DROP TABLE IF EXISTS students;

DROP TYPE IF EXISTS course_type_t;
CREATE TYPE course_type_t AS ENUM ('THEORY', 'PRACTICAL', 'EMBEDDED');

CREATE TABLE students (
                          roll_number      VARCHAR(10)  NOT NULL,
                          name             VARCHAR(100) NOT NULL,
                          department       VARCHAR(10)  NOT NULL,
                          current_semester SMALLINT     NOT NULL,
                          created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          CONSTRAINT pk_students PRIMARY KEY (roll_number),
                          CONSTRAINT ck_students_department CHECK (department IN
                                                                   ('CSE','ECE','EEE','MECH','CIVIL','IT','AERO','MCT','AIDS','CSD','BME','MBA','MCA')),
                          CONSTRAINT ck_students_semester CHECK (current_semester BETWEEN 1 AND 8),
                          CONSTRAINT ck_students_roll_format CHECK (roll_number ~ '^[0-9]{2}[A-Z]{2,4}[0-9]{3}$')
    );

CREATE TABLE courses (
                         course_code  VARCHAR(10)   NOT NULL,
                         title        VARCHAR(150)  NOT NULL,
                         credits      NUMERIC(3,1)  NOT NULL,
                         course_type  course_type_t NOT NULL,
                         CONSTRAINT pk_courses PRIMARY KEY (course_code),
                         CONSTRAINT ck_courses_credits CHECK (credits > 0 AND credits <= 10)
);

CREATE TABLE enrollments (
                             enrollment_id       BIGSERIAL    PRIMARY KEY,
                             roll_number         VARCHAR(10)  NOT NULL,
                             course_code         VARCHAR(10)  NOT NULL,
                             academic_year       VARCHAR(9)   NOT NULL,
                             semester            SMALLINT     NOT NULL,
                             classes_held        INTEGER      NOT NULL DEFAULT 0,
                             classes_attended    INTEGER      NOT NULL DEFAULT 0,
                             medical_condonation SMALLINT     NOT NULL DEFAULT 0,
                             CONSTRAINT uq_enrollment UNIQUE (roll_number, course_code, academic_year, semester),
                             CONSTRAINT fk_enr_student FOREIGN KEY (roll_number) REFERENCES students(roll_number)
                                 ON UPDATE CASCADE ON DELETE CASCADE,
                             CONSTRAINT fk_enr_course  FOREIGN KEY (course_code) REFERENCES courses(course_code)
                                 ON UPDATE CASCADE ON DELETE RESTRICT,
                             CONSTRAINT ck_enr_semester   CHECK (semester BETWEEN 1 AND 8),
                             CONSTRAINT ck_enr_condon     CHECK (medical_condonation IN (0, 1)),
                             CONSTRAINT ck_enr_counts     CHECK (classes_held >= 0 AND classes_attended >= 0),
                             CONSTRAINT ck_enr_attendance CHECK (classes_attended <= classes_held)
);

CREATE TABLE assessment_marks (
                                  enrollment_id  BIGINT        PRIMARY KEY,
                                  cia1           NUMERIC(5,2)  NULL,
                                  cia2           NUMERIC(5,2)  NULL,
                                  cia_assignment NUMERIC(5,2)  NULL,
                                  ese            NUMERIC(5,2)  NULL,
                                  is_absent_ese  SMALLINT      NOT NULL DEFAULT 0,
                                  updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  CONSTRAINT fk_marks_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(enrollment_id)
                                      ON DELETE CASCADE,
                                  CONSTRAINT ck_cia1 CHECK (cia1 IS NULL OR (cia1 BETWEEN 0 AND 100)),
                                  CONSTRAINT ck_cia2 CHECK (cia2 IS NULL OR (cia2 BETWEEN 0 AND 100)),
                                  CONSTRAINT ck_cia_assign CHECK (cia_assignment IS NULL OR (cia_assignment BETWEEN 0 AND 100)),
                                  CONSTRAINT ck_ese CHECK (ese IS NULL OR (ese BETWEEN 0 AND 100)),
                                  CONSTRAINT ck_absent_flag CHECK (is_absent_ese IN (0, 1)),
                                  CONSTRAINT ck_ese_absent CHECK (NOT (is_absent_ese = 1 AND ese IS NOT NULL))
);

CREATE OR REPLACE FUNCTION touch_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_marks_updated
    BEFORE UPDATE ON assessment_marks
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE INDEX ix_students_dept ON students(department, current_semester);
CREATE INDEX ix_enr_sem ON enrollments(academic_year, semester);

INSERT INTO courses (course_code, title, credits, course_type) VALUES
                                                                   ('24ITR31', 'Database Management Systems',     4.0, 'THEORY'),
                                                                   ('24ITR32', 'Database Management Systems Lab', 2.0, 'PRACTICAL'),
                                                                   ('24ITR33', 'Embedded Systems Design',         3.0, 'EMBEDDED');