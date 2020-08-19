package co.vaango.attendance.multibiometric.Database;


import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import co.vaango.attendance.multibiometric.utils.Config;
import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.Logger;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static DatabaseHelper databaseHelper;

    // All Static variables
    private static final int DATABASE_VERSION = 1;

    // Database Name
    private static final String DATABASE_NAME = Config.DATABASE_NAME;

    // Constructor
    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        Logger.addLogAdapter(new AndroidLogAdapter());
    }

    public static synchronized DatabaseHelper getInstance(Context context){
        if(databaseHelper==null){
            databaseHelper = new DatabaseHelper(context);
        }
        return databaseHelper;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        // Create tables SQL execution
        String CREATE_STUDENT_TABLE = "CREATE TABLE " + Config.TABLE_STUDENT + "("
                + Config.COLUMN_STUDENT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Config.COLUMN_STUDENT_NAME + " TEXT NOT NULL, "
                + Config.COLUMN_STUDENT_REGISTRATION + " INTEGER NOT NULL UNIQUE, "
                + Config.COLUMN_STUDENT_PHONE + " TEXT, " //nullable
                + Config.COLUMN_STUDENT_EMAIL + " TEXT " //nullable
                + ")";

        Logger.d("Table create SQL: " + CREATE_STUDENT_TABLE);

        String CREATE_USERS_TABLE = "CREATE TABLE " + Config.TABLE_USERS + "("
                + Config.COLUMN_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Config.COLUMN_USER_NAME + " TEXT NOT NULL, "
                + Config.COLUMN_USER_TYPE + " INTEGER NOT NULL, "
                + Config.COLUMN_FACE_ID + " INTEGER NOT NULL UNIQUE, "
                + Config.COLUMN_ENROLL_STATUS + " TEXT, " //nullable
                + Config.COLUMN_SYNC_STATUS + " INTEGER NOT NULL "
                + ")";

        String CREATE_VISITS_TABLE = "CREATE TABLE " + Config.TABLE_VISITS + "("
                + Config.COLUMN_VISIT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Config.COLUMN_VISIT_FACE_ID + " INTEGER NOT NULL, "
                + Config.COLUMN_VISITOR_NAME + " TEXT NOT NULL, "
                + Config.COLUMN_VISIT_TIME_IN + " TEXT NOT NULL, "
                + Config.COLUMN_VISIT_TIME_OUT + " TEXT NOT NULL, "
                + Config.COLUMN_VISIT_SYNC_STATUS + " INTEGER NOT NULL, "
                + "FOREIGN KEY (" + Config.COLUMN_VISIT_FACE_ID + ") REFERENCES " + Config.TABLE_USERS + "(" + Config.COLUMN_FACE_ID + ") ON UPDATE CASCADE ON DELETE CASCADE)";

        db.execSQL(CREATE_STUDENT_TABLE);
        db.execSQL(CREATE_USERS_TABLE);
        db.execSQL(CREATE_VISITS_TABLE);

        Logger.d("DB created!");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + Config.TABLE_STUDENT);
        db.execSQL("DROP TABLE IF EXISTS " + Config.TABLE_USERS);
        db.execSQL("DROP TABLE IF EXISTS " + Config.TABLE_VISITS);
        // Create tables again
        onCreate(db);
    }

}