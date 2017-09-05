package com.luoye.game.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.luoye.game.R;
import com.luoye.game.callback.OnSendFileFinish;
import com.luoye.game.callback.ReceiveFileCallback;
import com.luoye.game.callback.ReceiveFileProgress;
import com.luoye.game.callback.SendFileCallback;
import com.luoye.game.callback.SmackResultCallback;
import com.luoye.game.service.MessageService;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.chat.ChatManagerListener;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.filetransfer.FileTransferListener;
import org.jivesoftware.smackx.filetransfer.FileTransferManager;
import org.jivesoftware.smackx.filetransfer.FileTransferRequest;
import org.jivesoftware.smackx.filetransfer.IncomingFileTransfer;
import org.jivesoftware.smackx.filetransfer.OutgoingFileTransfer;
import org.jivesoftware.smackx.iqregister.AccountManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * Created by zyw on 2017/1/29.
 */
public class NetTool {

    private static XMPPTCPConnection connection;
    private Context context;
    private Chat chat;
    private static ChatManager chatManager;//这里静态很重要
    private ChatManagerListener chatManagerListener;
    private static NetTool smackTool;
    private  FileTransferListener fileTransferListener;
    private FileTransferManager fileTransferManager;
    public static final String MESSAGE_TYPE_CMD = "cmd";
    public static final String MESSAGE_TYPE_KEY="key";

    public static final String MESSAGE_TYPE_GAME_INFO ="game_info";//游戏信息,包含md5,游戏名称，游戏描述
    public static final String MESSAGE_TYPE_GAME_MD5 ="game_md5";
    public static final String MESSAGE_TYPE_GAME_NAME ="game_name";//游戏名称
    public static final String MESSAGE_TYPE_GAME_DESC ="game_desc";//游戏描述

    public static final String CMD_HELLO="hello";
    public static final String CMD_RESET_ROM="reset_rom";
    public static final String CMD_END_GAME ="end_game";
    public static final String CMD_ALL_OK="all_ok";
    public static final String CMD_GET_ROM="get_rom";
    public String curHost;
    private int remoteKeys;
    private final Object frameLock=new Object();
    private static int remoteFrameCount;
    private static int localFrameCount;
    private final   int maxFrameAhead=10;
    private Queue<Integer> blockingQueue;
    /**
     * 接收聊天消息
     * @param chatMessageListener
     */
    public void   receiveMsg(final ChatMessageListener chatMessageListener){
        //添加消息接收器
        if(this.chatManagerListener==null) {
            chatManager.addChatListener(this.chatManagerListener = new ChatManagerListener() {
                /**
                 * @param chat
                 * @param b    消息是否来自本地用户
                 */
                @Override
                public void chatCreated(Chat chat, boolean b) {
                    if (!b) {
                        chat.addMessageListener(chatMessageListener);
                    }
                }
            });
        }
    }

    private  BroadcastReceiver broadcastReceiver=new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(MessageService.ACTION_GAME_KEY))
            {
                String strKey=intent.getStringExtra("data");
                int key=Integer.parseInt(strKey);
//                String[] arr=strKey
//                        .replace("[", "")
//                        .replace("]", "")
//                        .replace(" ", "")
//                        .split(",");

                synchronized (frameLock)
                {
                   // log("frameUpdate frameLock "+Thread.currentThread().getName()+",local:"+localFrameCount+",remote:"+remoteFrameCount+",key:"+strKey);
                    //remoteKeys=key;
//                    blockingQueue.clear();
//                    for(String str:arr) {
                        blockingQueue.add(key);
//                    }
                    if (++remoteFrameCount - localFrameCount>=1) {
                       // log("frameUpdate frameLock notify"+",local:"+localFrameCount+",remote:"+remoteFrameCount);
                        frameLock.notify();
                    }
                }
            }
        }
    };

    /**
     * 发送帧更新
     * @param userId
     * @param keys
     * @param smackResultCallback
     * @return
     */
    public  int sendFrameUpdate(String userId,final int keys, final SmackResultCallback smackResultCallback)
    {
        if(connection==null) {
            smackResultCallback.onReceive(connection,null,false);
            return 0;
        }
        if(chat==null) {
            chat = chatManager.createChat(userId);
        }
        Thread thread=  new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Message msg=new Message();
                    msg.addBody(MESSAGE_TYPE_KEY,String.valueOf(keys));
                    chat.sendMessage(msg);
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,null,true);
                }
                catch (Exception e) {
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,null,false);
                    e.printStackTrace();
                }
            }
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        synchronized (frameLock) {
            //log("sendFrameUpdate frameLock," + Thread.currentThread().getName() + ",local:" + localFrameCount + ",remote:" + remoteFrameCount);
            localFrameCount++;

            while (localFrameCount - remoteFrameCount > maxFrameAhead) {
                try {
                    //log("sendFrameUpdate frameLock wait," + Thread.currentThread().getName() + ",local:" + localFrameCount + ",remote:" + remoteFrameCount);
                    frameLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            while (blockingQueue.size()>0) {
                return blockingQueue.poll();
            }
        }
        return 0;
    }
//    public  Integer[] sendFrameUpdate(String userId,final String keys, final SmackResultCallback smackResultCallback)
//    {
//        if(connection==null) {
//            smackResultCallback.onReceive(connection,null,false);
//            return null;
//        }
//        if(chat==null) {
//            chat = chatManager.createChat(userId);
//        }
//        Thread thread=  new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    Message msg=new Message();
//                    msg.addBody(MESSAGE_TYPE_KEY,String.valueOf(keys));
//                    chat.sendMessage(msg);
//                    if(smackResultCallback!=null)
//                        smackResultCallback.onReceive(connection,null,true);
//                }
//                catch (Exception e) {
//                    if(smackResultCallback!=null)
//                        smackResultCallback.onReceive(connection,null,false);
//                    e.printStackTrace();
//                }
//            }
//        });
//        thread.start();
//        try {
//            thread.join();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        synchronized (frameLock) {
//            //log("sendFrameUpdate frameLock," + Thread.currentThread().getName() + ",local:" + localFrameCount + ",remote:" + remoteFrameCount);
//            localFrameCount++;
//
//            while (localFrameCount - remoteFrameCount > maxFrameAhead) {
//                try {
//                    //log("sendFrameUpdate frameLock wait," + Thread.currentThread().getName() + ",local:" + localFrameCount + ",remote:" + remoteFrameCount);
//                    frameLock.wait();
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//
//        }
//        return (Integer[])blockingQueue.toArray();
//    }
    /**
     * 监听接收文件
     * @param receiveFileProgress
     * @param dir 存放的文件夹
     */
    public  void listenReceiveFile(final File dir, final ReceiveFileProgress receiveFileProgress)
    {
        receiveFile(new ReceiveFileCallback() {
            @Override
            public void onReceive(InputStream in,String fileName,long fileSize) {

                if(in==null)
                    return ;
                FileOutputStream fileOutputStream = null;
                try {
                    fileOutputStream=new FileOutputStream(dir+File.separator+fileName);
                    byte[] buf=new byte[1024];
                    int len=-1;
                    int cur=0;
                    while ((len=in.read(buf))!=-1)
                    {
                        fileOutputStream.write(buf,0,len);
                        fileOutputStream.flush();
                        cur+=len;
                        receiveFileProgress.onProgress(fileName,cur,fileSize);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                finally {
                        try {
                            in.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    if(fileOutputStream!=null)
                        try {
                            fileOutputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                }

            }
        });
    }

    /**
     * 移除监听接收文件
     */
    public  void removeListenReceiveFile()
    {
        if(fileTransferListener!=null)
            fileTransferManager.removeFileTransferListener(fileTransferListener);
    }
    /**
     * 接收文件
     */
    private void   receiveFile(final ReceiveFileCallback receiveFileCallback){
        //添加消息接收器
        if(connection==null) {
            return;
        }
        if(fileTransferManager==null)
            fileTransferManager=FileTransferManager.getInstanceFor(connection);

        fileTransferManager.addFileTransferListener(fileTransferListener=new FileTransferListener() {
            @Override
            public void fileTransferRequest(FileTransferRequest fileTransferRequest) {
                IncomingFileTransfer incomingFileTransfer= fileTransferRequest.accept();

                    try {
                        receiveFileCallback.onReceive(incomingFileTransfer.recieveFile(),incomingFileTransfer.getFileName(),incomingFileTransfer.getFileSize());
                    } catch (XMPPException.XMPPErrorException e) {
                        receiveFileCallback.onReceive(null,null,0);
                        e.printStackTrace();
                    } catch (SmackException e) {
                        receiveFileCallback.onReceive(null,null,0);
                        e.printStackTrace();
                    }
            }
        });

    }
    /**
     * 发送文本消息
     * @param userId
     * @param msg
     * @param smackResultCallback
     */
    public void   sendTextMsg(String userId, final String msg, final SmackResultCallback smackResultCallback){
        if(connection==null) {
            smackResultCallback.onReceive(connection,null,false);
            return;
        }
        if(chat==null) {
            chat = chatManager.createChat(userId);
        }
        Thread thread= new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    chat.sendMessage(msg);
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.send_msg_success),true);
                }
                catch (Exception e) {
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.send_msg_fail),false);
                    e.printStackTrace();
                }
            }
        });
        thread.start();

    }

    /**
     * 注册
     * @param username
     * @param password
     * @param smackResultCallback
     * @param attributes
     * @return
     */
    public void   register(final String username, final String password, final Map<String, String> attributes , final SmackResultCallback smackResultCallback){

        Thread thread= new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    AccountManager.sensitiveOperationOverInsecureConnectionDefault(true);
                    AccountManager.getInstance(connection).createAccount(username, password, attributes);
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.register_success),true);
                } catch (Exception e) {
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.register_fail),false);
                    e.printStackTrace();
                }
            }
        });
        thread.start();

    }

    /**
     * 登录
     * @param username
     * @param password
     * @return
     */
    public void  login(final String username, final String password, final SmackResultCallback smackResultCallback)
    {
        Thread thread=  new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    connection.login(username,password);
                    chatManager = ChatManager.getInstanceFor(connection);
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.login_success),true);
                } catch (XMPPException |SmackException |IOException e) {
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.login_fail),false);
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }
    /**
     * 连接到服务器
     */
    public void connect(final SmackResultCallback smackResultCallback)
    {
        XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
                .setServiceName(ConstantPool.SERVER_NAME)
                .setHost(ConstantPool.REMOTE_HOST)
                .setPort(ConstantPool.REMOTE_PORT)
                .setConnectTimeout(20000)
                .setCompressionEnabled(false)
                .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
                .build();
        this.curHost=ConstantPool.REMOTE_HOST;
        connection = new XMPPTCPConnection(config);

        Thread thread= new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        connection= (XMPPTCPConnection) connection.connect();
                        //配置断线重连
                        ReconnectionManager reconnectionManager= ReconnectionManager.getInstanceFor(connection);
                        reconnectionManager.enableAutomaticReconnection();
                        if(connection.isConnected())
                        {
                            connection.addConnectionListener(mConnectionListener);
                        }
                        if(smackResultCallback!=null)
                            smackResultCallback.onReceive(connection,context.getString(R.string.connect_server_success),true);
                    } catch (Exception e) {
                        if(smackResultCallback!=null)
                            smackResultCallback.onReceive(connection,context.getString(R.string.connect_server_fail),false);
                        e.printStackTrace();
                    }
                }
            });
        thread.start();
    }


    /**
     * 发送HELLO
     * @param userId
     * @param smackResultCallback
     */
    public void sendHello(String userId, final SmackResultCallback smackResultCallback)
    {
        if(connection==null) {
            smackResultCallback.onReceive(connection,null,false);
            return;
        }
        if(chat==null) {
            chat = chatManager.createChat(userId);
        }

        Thread thread=  new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Message msg=new Message();
                    msg.addBody(MESSAGE_TYPE_CMD,CMD_HELLO);
                    chat.sendMessage(msg);
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,null,true);
                }
                catch (Exception e) {
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,null,false);
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    /**
     * 发送重置
     * @param userId
     * @param smackResultCallback
     */
    public void sendReset(String userId, final SmackResultCallback smackResultCallback)
    {
        if(connection==null) {
            smackResultCallback.onReceive(connection,null,false);
            return;
        }
        if(chat==null) {
            chat = chatManager.createChat(userId);
        }

        Thread thread=  new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Message msg=new Message();
                    msg.addBody(MESSAGE_TYPE_CMD,CMD_RESET_ROM);
                    chat.sendMessage(msg);
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,null,true);
                }
                catch (Exception e) {
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,null,false);
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    public void resetFrame() {
        localFrameCount = remoteFrameCount = 0;
        remoteKeys = 0;
    }
    /**
     * 发送获取ROM
     * @param userId
     * @param smackResultCallback
     */
    public void sendGetROM(String userId, final SmackResultCallback smackResultCallback)
    {
        if(connection==null) {
            smackResultCallback.onReceive(connection,null,false);
            return;
        }
        if(chat==null) {
            chat = chatManager.createChat(userId);
        }

        Thread thread=  new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Message msg=new Message();
                    msg.addBody(MESSAGE_TYPE_CMD,CMD_GET_ROM);
                    chat.sendMessage(msg);
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,null,true);
                }
                catch (Exception e) {
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,null,false);
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    /**
     * 发送ROM
     * @param userId
     * @param sendFileCallback
     */
    public void sendROM(String userId, final File romFile, final SendFileCallback sendFileCallback)
    {
        if(connection==null) {
            sendFileCallback.onSendFileProgress(-1);
            return;
        }
        fileTransferManager=FileTransferManager.getInstanceFor(connection);

        final OutgoingFileTransfer outgoingFileTransfer=fileTransferManager.createOutgoingFileTransfer(userId);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        FileInputStream fileInputStream=new FileInputStream(romFile);

                        outgoingFileTransfer.sendStream(fileInputStream,romFile.getName(),romFile.length(),null);
                        double progress=0;
                        while ((progress=outgoingFileTransfer.getProgress())<1.0) {
                            sendFileCallback.onSendFileProgress(progress);
                        }
                        sendFileCallback.onSendFileProgress(1.0);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();

    }
    /**
     * 发送所有已经完成
     * @param userId
     * @param smackResultCallback
     */
    public void sendAllOK(String userId, final SmackResultCallback smackResultCallback)
    {
        if(connection==null) {
            smackResultCallback.onReceive(connection,null,false);
            return;
        }
        if(chat==null) {
            if(chatManager==null)
                return;
            chat = chatManager.createChat(userId);
        }

        Thread thread=  new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Message msg=new Message();
                    msg.addBody(MESSAGE_TYPE_CMD,CMD_ALL_OK);
                    chat.sendMessage(msg);
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,null,true);
                }
                catch (Exception e) {
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,null,false);
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }
    /**
     * 发送游戏信息
     * @param userId
     * @param smackResultCallback
     */
    public void sendGameInfo(String userId, final String info, final SmackResultCallback smackResultCallback)
    {
        if(connection==null) {
            smackResultCallback.onReceive(connection,null,false);
            return;
        }
        if(chat==null) {
            chat = chatManager.createChat(userId);
        }

        Thread thread=  new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Message msg=new Message();
                    msg.addBody(MESSAGE_TYPE_GAME_INFO,info);
                    chat.sendMessage(msg);
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,null,true);
                }
                catch (Exception e) {
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,null,false);
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }
    /**
     * 发送关闭游戏
     * @param userId
     * @param smackResultCallback
     */
    public void sendEndGame(String userId, final SmackResultCallback smackResultCallback)
    {
        if(connection==null) {
            smackResultCallback.onReceive(connection,null,false);
            return;
        }
        if(chat==null) {
            chat = chatManager.createChat(userId);
        }

        Thread thread=  new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Message msg=new Message();
                    msg.addBody(MESSAGE_TYPE_CMD, CMD_END_GAME);
                    chat.sendMessage(msg);
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,null,true);
                }
                catch (Exception e) {
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,null,false);
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    /**
     * 发送游戏状态
     * @param userId
     */
    public void sendState(String userId, final File stateFile, final OnSendFileFinish onSendFileFinish)
    {
        if(connection==null) {
            if(onSendFileFinish!=null)
            onSendFileFinish.onFinish(stateFile,false);
            return;
        }
        FileTransferManager fileTransferManager= FileTransferManager.getInstanceFor(connection);

        final OutgoingFileTransfer outgoingFileTransfer=fileTransferManager.createOutgoingFileTransfer(userId);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FileInputStream fileInputStream=new FileInputStream(stateFile);
                    outgoingFileTransfer.sendStream(fileInputStream,stateFile.getName(),stateFile.length(),null);
                    while (outgoingFileTransfer.getProgress()<1.0);
                    if(onSendFileFinish!=null)
                        onSendFileFinish.onFinish(stateFile,true);
                }  catch (IOException e) {
                    e.printStackTrace();
                    if(onSendFileFinish!=null)
                        onSendFileFinish.onFinish(stateFile,false);
                }
            }
        }).start();

    }


    /**
     * 断线重连
     */
   private static ConnectionListener mConnectionListener=new ConnectionListener() {
        @Override
        public void connected(XMPPConnection connection) {
            Log.v("smackTool","connected");
        }

        @Override
        public void authenticated(XMPPConnection connection, boolean resumed) {
            Log.v("smackTool","authenticated");
        }

        @Override
        public void connectionClosed() {
            Log.v("smackTool","connectionClosed");
        }

        @Override
        public void connectionClosedOnError(Exception e) {
            Log.v("smackTool","connectionClosedOnError");
        }

        @Override
        public void reconnectionSuccessful() {
            Log.v("smackTool","reconnectionSuccessful");
        }

        @Override
        public void reconnectingIn(int seconds) {
            Log.v("smackTool","reconnectingIn");
        }

        @Override
        public void reconnectionFailed(Exception e) {
            Log.v("smackTool","reconnectionFailed");
        }
    };

    private  void log(String log)
    {
        System.out.println("--------------->Smack"+" "+log);
    }
    /**
     * 获取连接
     * @return
     */
    public  XMPPTCPConnection getConnection()
    {
        return  connection;
    }
    private NetTool(Context context)
    {
        this.context=context;
        IntentFilter intentFilter=new IntentFilter();
        intentFilter.addAction(MessageService.ACTION_GAME_KEY);
        context.registerReceiver(broadcastReceiver,intentFilter);
        blockingQueue=new LinkedList<>();
    }
    public static NetTool getInstance(Context context)
    {
        if(smackTool==null)
        {
            return new NetTool(context);
        }
        return smackTool;
    }

}
