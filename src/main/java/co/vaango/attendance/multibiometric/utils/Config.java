package co.vaango.attendance.multibiometric.utils;

public class Config {

    public static final String DATABASE_NAME = "student-db";

    //column names of student table
    public static final String TABLE_STUDENT = "student";
    public static final String COLUMN_STUDENT_ID = "_id";
    public static final String COLUMN_STUDENT_NAME = "name";
    public static final String COLUMN_STUDENT_REGISTRATION = "registration_no";
    public static final String COLUMN_STUDENT_PHONE = "phone";
    public static final String COLUMN_STUDENT_EMAIL = "email";

    //column names of users table
    public static final String TABLE_USERS = "users";
    public static final String COLUMN_USER_ID = "_id";
    public static final String COLUMN_USER_NAME = "name";
    public static final String COLUMN_USER_TYPE = "type";
    public static final String COLUMN_FACE_ID = "face_id";
    public static final String COLUMN_ENROLL_STATUS = "enroll_status";
    public static final String COLUMN_SYNC_STATUS = "sync_status";

    //column names of visits table
    public static final String TABLE_VISITS = "visits";
    public static final String COLUMN_VISIT_ID = "_id";
    public static final String COLUMN_VISITOR_NAME = "user_name";
    public static final String COLUMN_VISIT_FACE_ID = "face_id";
    public static final String COLUMN_VISIT_TIME_IN = "in_time";
    public static final String COLUMN_VISIT_TIME_OUT = "out_time";
    public static final String COLUMN_VISIT_SYNC_STATUS = "sync_status";
    public static final String FACE_ID_CONSTRAINT = "face_id_unique";

    //others for general purpose key-value pair data
    public static final String TITLE = "title";
    public static final String CREATE_STUDENT = "create_student";
    public static final String UPDATE_STUDENT = "update_student";
}