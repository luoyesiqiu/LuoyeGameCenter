package com.luoye.game.callback;

import org.jivesoftware.smack.tcp.XMPPTCPConnection;

/**
 * Created by zyw on 2017/7/19.
 */
public interface SmackResultCallback {
    public  void onReceive(XMPPTCPConnection connection,String msg,boolean isSuccess);
}
