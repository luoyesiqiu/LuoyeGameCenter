package com.luoye.game.callback;

import java.io.InputStream;

/**
 * Created by zyw on 2017/7/28.
 */
public interface ReceiveFileCallback {
    void onReceive(InputStream in,String fileName,long fileSize);
}
