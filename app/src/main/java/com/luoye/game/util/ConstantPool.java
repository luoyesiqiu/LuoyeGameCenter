package com.luoye.game.util;

import android.os.Environment;

import java.io.File;

/**
 * Created by zyw on 2017/7/19.
 */
public class ConstantPool {

    public static final String SERVER_NAME = "127.0.0.1";
    public static final String REMOTE_HOST = "localhost";
    public static final int REMOTE_PORT = 5222;

    public static final String DATA_TYPE_NIL = "nil";
    public static final String GAME_ROOT = Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator+"LuoyeGame";
    public static final int OK_SELECT_RESULT_CODE = 101;
}
