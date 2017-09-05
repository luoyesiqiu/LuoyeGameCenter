package com.luoye.game.callback;

/**
 * Created by zyw on 2017/7/28.
 */
public interface ReceiveFileProgress {
    void onProgress(String fileName,long current,long size);
}
