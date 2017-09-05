package com.luoye.game.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.IBinder;

import com.securepreferences.SecurePreferences;
import com.luoye.game.ThisApplication;
import com.luoye.game.callback.SmackResultCallback;
import com.luoye.game.util.ConstantPool;
import com.luoye.game.util.NetTool;

import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;

import java.util.HashMap;
import java.util.List;

/**
 * 本类用于消息分发
 * Created by zyw on 2017/7/26.
 */
public class MessageService extends Service {
    private NetTool smackTool;
    //发出去
    public static  String ACTION_CONNECT_FAIL ="action_connect_fail";
    public static  String ACTION_REGISTER_SUCCESS ="action_register_success";
    public static  String ACTION_REG_LOGIN_SUCCESS ="action_reg_login_success";
    public static  String ACTION_REGISTER_FAIL ="action_register_fail";
    public static  String ACTION_LOGIN_SUCCESS ="action_login_success";
    public static  String ACTION_LOGIN_FAIL ="action_login_fail";
    public static  String ACTION_START_FINISH ="action_start_finish";
    //消息
    public static String ACTION_NORMAL_MSG="action_normal_msg";
    public static String ACTION_CMD_MSG="action_cmd_msg";
    public static String ACTION_GAME_INFO_MSG="action_game_info_msg";
    public static String ACTION_GAME_KEY="action_game_key";
    //接受
    public static String ACTION_REQUEST_CONNECT_LOGIN ="action_connect_login";
    public static String ACTION_REQUEST_REGISTER_LOGIN ="action_register_login";
    public static String ACTION_REQUEST_STOP_SERVICE ="action_stop_service";
    public static  boolean isAlive=false;
    private    MessageBroadcastReceiver messageBroadcastReceiver;
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        IntentFilter intentFilter=new IntentFilter();
        intentFilter.addAction(ACTION_REQUEST_REGISTER_LOGIN);
        intentFilter.addAction(ACTION_REQUEST_CONNECT_LOGIN);
        intentFilter.addAction(ACTION_REQUEST_STOP_SERVICE);

        smackTool =((ThisApplication)getApplication()).getSmack();
        messageBroadcastReceiver=new MessageBroadcastReceiver();
        registerReceiver(messageBroadcastReceiver,intentFilter);
        Intent startFinishIntent=new Intent();
        startFinishIntent.setAction(ACTION_START_FINISH);
        sendBroadcast(startFinishIntent);
        isAlive=true;
        log("onStartCommand");
        return START_STICKY;
    }


    /**
     * 从其他地方发来的消息
     */
    private    class MessageBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(ACTION_REQUEST_CONNECT_LOGIN))
            {
                log("ACTION_REQUEST_CONNECT_LOGIN");
                //登录
                String username=intent.getStringExtra("username");
                String password=intent.getStringExtra("pwd");
                connectAndLogin(username,password);
            }
            else if(intent.getAction().equals(ACTION_REQUEST_REGISTER_LOGIN))
            {
                //注册
                String username=intent.getStringExtra("username");
                String password=intent.getStringExtra("pwd");
                registerAndLogin(username,password);
            }
            //杀死自己
            else if(intent.getAction().equals(ACTION_REQUEST_STOP_SERVICE))
            {
                stopSelf();
            }

        }
    }
    private  void registerAndLogin(final String username, final String pwd)
    {
        smackTool.connect(new SmackResultCallback() {
            @Override
            public void onReceive(XMPPTCPConnection connection, String msg, boolean isOK) {
                if (isOK) {
                    HashMap<String, String> map = new HashMap<String, String>();
                    smackTool.register(username, pwd, map, new SmackResultCallback() {
                        @Override
                        public void onReceive(XMPPTCPConnection connection, String msg, boolean isOK) {
                            if (isOK) {
                                //注册成功,登录
                                smackTool.login(username, pwd, new SmackResultCallback() {
                                    @Override
                                    public void onReceive(XMPPTCPConnection connection, String msg, boolean isSuccess) {
                                        if(isSuccess)
                                        {
                                            Intent intent=new Intent();
                                            intent.setAction(ACTION_REG_LOGIN_SUCCESS);
                                            sendBroadcast(intent);
                                        }
                                    }
                                });
                            } else {
                                //注册失败
                                Intent intent=new Intent();
                                intent.setAction(ACTION_REGISTER_FAIL);
                                sendBroadcast(intent);
                            }
                        }
                    });
                }
            }
        });
    }

    /**
     * 连接服务器并登陆
     */
    private  void connectAndLogin(final String username, final String pwd)
    {
        smackTool.connect(new SmackResultCallback() {
        @Override
        public void onReceive(XMPPTCPConnection connection, final String msg, final boolean isOK) {

            if(isOK)
            {
                smackTool.login(username, pwd, new SmackResultCallback() {
                    @Override
                    public void onReceive(XMPPTCPConnection connection, String msg, boolean isSuccess) {
                        if(isSuccess)
                        {
                            //登陆成功
                            receiveListenMsg();
                            Intent intent=new Intent();
                            intent.setAction(ACTION_LOGIN_SUCCESS);
                            sendBroadcast(intent);
                        }
                        else
                        {
                            //登录失败
                            Intent intent=new Intent();
                            intent.setAction(ACTION_LOGIN_FAIL);
                            sendBroadcast(intent);
                        }
                    }
                });
            }
            else
            {
                Intent intent=new Intent();
                intent.setAction(ACTION_CONNECT_FAIL);
                sendBroadcast(intent);
            }

        }
    });//connect
    }

    /**
     * 监听其他客户端发来的消息
     */
    private  void receiveListenMsg()
    {

        smackTool.receiveMsg(new ChatMessageListener() {
            @Override
            public void processMessage(Chat chat, Message message) {
                List<String> list=message.getBodyLanguages();
                String lang=null;
                lang=list.size()>0?list.get(0):"";
                final String data=message.getBody(lang);
                //log("receiveMsg，"+lang+","+data);
                Intent intent=new Intent();
                if(lang.equals("")) {
                    intent.setAction(ACTION_NORMAL_MSG);
                }
                else if(lang.equals(NetTool.MESSAGE_TYPE_CMD))
                {
                    intent.setAction(ACTION_CMD_MSG);
                }
                else if(lang.equals(NetTool.MESSAGE_TYPE_GAME_INFO))
                {
                    String[] arr=data.split(",");
                    String md5=arr[0];
                    String gameName=arr[1];
                    intent.putExtra(NetTool.MESSAGE_TYPE_GAME_MD5,md5);
                    intent.putExtra(NetTool.MESSAGE_TYPE_GAME_NAME,gameName);
                    intent.setAction(ACTION_GAME_INFO_MSG);
                }
                else if(lang.equals(NetTool.MESSAGE_TYPE_KEY))
                {
                    intent.setAction(ACTION_GAME_KEY);
                }

                intent.putExtra("user",message.getFrom());
                intent.putExtra("data",data);
                sendOrderedBroadcast(intent,null);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        log("onDestroy");
        isAlive=false;
        unregisterReceiver(messageBroadcastReceiver);
    }

    private  void log(String log)
    {
        System.out.println("--------------->MessageService "+log);
    }
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
