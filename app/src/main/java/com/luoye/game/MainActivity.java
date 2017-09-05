package com.luoye.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.luoye.game.activity.FileListActivity;
import com.luoye.game.activity.HelpActivity;
import com.luoye.game.activity.SettingActivity;
import com.luoye.game.util.IO;
import com.securepreferences.SecurePreferences;
import com.luoye.game.activity.CreateGameActivity;
import com.luoye.game.activity.EmulatorActivity;
import com.luoye.game.callback.ReceiveFileProgress;
import com.luoye.game.service.MessageService;
import com.luoye.game.util.ConstantPool;
import com.luoye.game.util.NesFileFilter;
import com.luoye.game.util.NetTool;
import com.luoye.game.util.Utils;
import com.umeng.analytics.MobclickAgent;

import java.io.File;

public class MainActivity extends Activity implements View.OnClickListener {

    private LinearLayout createGameButton;
    private LinearLayout connectToGameButton;
    private  LinearLayout singleGameButton;
    private  LinearLayout aboutButton;
    private  LinearLayout settingButton;
    private AlertDialog alertDialog;
    private String userRegex="[A-Za-z0-9]{3,20}";
    public static NetTool smackTool;
    private SharedPreferences _sharedPreferences;

    private String userId;
   private String gameName;
    private ProgressDialog progressDialog;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        progressDialog=new ProgressDialog(this);
        progressDialog.setMessage("文件接收中...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        createGameButton=(LinearLayout) findViewById(R.id.create_game);
        connectToGameButton=(LinearLayout)findViewById(R.id.connect_to_game);
        singleGameButton=(LinearLayout)findViewById(R.id.single_game);
        aboutButton=(LinearLayout)findViewById(R.id.about_button);
        settingButton=(LinearLayout)findViewById(R.id.setting_button);
        createGameButton.setOnClickListener(this);
        connectToGameButton.setOnClickListener(this);
        singleGameButton.setOnClickListener(this);
        aboutButton.setOnClickListener(this);
        settingButton.setOnClickListener(this);
        _sharedPreferences=new SecurePreferences(this, ConstantPool.DATA_TYPE_NIL,"data");
        startMessageService();
        if(_sharedPreferences.getString("username","").equals(""))
        {

            showLoginAndRegisterDialog();
        }

        smackTool =((ThisApplication)getApplication()).getSmack();

        registerReceiver();
    }

    private  void registerReceiver(){

        IntentFilter intentFilter=new IntentFilter();
        intentFilter.addAction(MessageService.ACTION_LOGIN_SUCCESS);
        intentFilter.addAction(MessageService.ACTION_LOGIN_FAIL);
        intentFilter.addAction(MessageService.ACTION_CONNECT_FAIL);
        intentFilter.addAction(MessageService.ACTION_GAME_INFO_MSG);
        intentFilter.addAction(MessageService.ACTION_START_FINISH);
        registerReceiver(broadcastReceiver,intentFilter);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        log("onDestroy");
        Intent intent=new Intent();
        intent.setAction(MessageService.ACTION_REQUEST_STOP_SERVICE);
        sendBroadcast(intent);
        unregisterReceiver(broadcastReceiver);

    }


    @Override
    protected void onPause() {
        super.onPause();
        if(progressDialog!=null)
        {
            progressDialog.dismiss();
        }

        MobclickAgent.onPause(this);

        smackTool.removeListenReceiveFile();
    }

    private  void startMessageService(){
        if(!MessageService.isAlive) {
            Intent intent = new Intent(MainActivity.this, MessageService.class);
            startService(intent);
        }
    }

    /**
     * 请求登录
     */
    private  void reqLogin()
    {
        Intent reqLoginIntent = new Intent();
        reqLoginIntent.setAction(MessageService.ACTION_REQUEST_CONNECT_LOGIN);
        reqLoginIntent.putExtra("username",_sharedPreferences.getString("username","") );
        reqLoginIntent.putExtra("pwd",_sharedPreferences.getString("pwd","") );
        sendBroadcast(reqLoginIntent);
    }
    /**
     * 消息接受处
     */
    private  BroadcastReceiver broadcastReceiver=new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //服务启动完成
            if(intent.getAction().equals(MessageService.ACTION_START_FINISH)){
                if(!_sharedPreferences.getString("username","").equals("")) {
                    reqLogin();
                }
            }
            else if(intent.getAction().equals(MessageService.ACTION_REG_LOGIN_SUCCESS))
            {
                SharedPreferences.Editor editor=_sharedPreferences.edit() ;
                editor.putString("username",userTemp)
                        .putString("pwd",pwdTemp)
                        .apply();
                MobclickAgent.onProfileSignIn("userID");
                showToast(getString(R.string.reg_login_success));
                MobclickAgent.onEvent(getApplicationContext(),"register");
            }
            else if(intent.getAction().equals(MessageService.ACTION_LOGIN_SUCCESS))
            {
                //不用每次登录都写入
                if(_sharedPreferences.getString("username","").equals("")) {
                    SharedPreferences.Editor editor = _sharedPreferences.edit();
                    editor.putString("username", userTemp)
                            .putString("pwd", pwdTemp)
                            .apply();
                }


                showToast(getString(R.string.login_success));
                MobclickAgent.onProfileSignIn(userId);
            }
            else if(intent.getAction().equals(MessageService.ACTION_LOGIN_FAIL))
            {
                showToast(getString(R.string.login_fail));
                showLoginAndRegisterDialog();
            }
            else if(intent.getAction().equals(MessageService.ACTION_CONNECT_FAIL))
            {
                showToast(getString(R.string.connect_server_fail));
                showLoginAndRegisterDialog();
            }            //收到游戏信息
            else if(intent.getAction().equals(MessageService.ACTION_GAME_INFO_MSG))
            {
                File nesRoot=new File(ConstantPool.GAME_ROOT);
                if(!nesRoot.exists()) {
                    smackTool.sendGetROM(userId,null);
                    nesRoot.mkdirs();
                }
                File[] files=nesRoot.listFiles(new NesFileFilter(true));
                String md5=intent.getStringExtra(NetTool.MESSAGE_TYPE_GAME_MD5);
                 gameName=intent.getStringExtra(NetTool.MESSAGE_TYPE_GAME_NAME);
                boolean isFind=false;//本地有没有这个文件
                for(File f:files)
                {
                    String tempMd5= Utils.getMD5(f);
                    if(tempMd5==null)
                        continue;
                    if(tempMd5.equals(md5))
                    {
                        //发送已存在
                        smackTool.sendAllOK(userId,null);
                        startEmulatorActivity(f,userId,false,false);
                        isFind=true;
                        break;
                    }
                }
                //log("onReceive MessageService.ACTION_GAME_INFO_MS");
                if(!isFind)
                {
                    //本地没有发现，发送获取ROM
                    smackTool.sendGetROM(userId,null);
                }
            }
        }
    };


    private void recFile()
    {
        final File recDir=new File(ConstantPool.GAME_ROOT);
        smackTool.listenReceiveFile(recDir,new ReceiveFileProgress() {
            @Override
            public void onProgress(final String fileName, final long current, final long size) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        int n=(int)(((double)current/size)*100);
                        log(current+","+size+","+n);
                        if(size>current) {
                            progressDialog.setProgress(n);
                            progressDialog.setMax(100);
                            progressDialog.setIndeterminate(false);
                            progressDialog.show();
                        }
                        else
                        {
                            progressDialog.dismiss();
                            smackTool.sendAllOK(userId,null);
                            startEmulatorActivity(new File(recDir+File.separator+fileName),userId,false,false);
                        }
                    }
                });

            }
        });
    }

    private  void startEmulatorActivity(File f,String remoteUser,boolean isServer,boolean isSinglePlay)
    {
        Intent intentEmulator=new Intent(MainActivity.this,EmulatorActivity.class);
        intentEmulator.setData(Uri.fromFile(f));
        intentEmulator.putExtra("user",remoteUser);
        intentEmulator.putExtra("isServer",isServer);
        intentEmulator.putExtra("isSinglePlay",isSinglePlay);
        startActivity(intentEmulator);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId())
        {

            case R.id.create_game:
                if(_sharedPreferences.getString("username","").equals(""))
                {
                    showLoginAndRegisterDialog();
                }
                else
                {
                    Intent intent=new Intent(MainActivity.this, CreateGameActivity.class);
                    startActivity(intent);
                }
                break;

            case R.id.connect_to_game:
                if(_sharedPreferences.getString("username","").equals(""))
                {
                    showLoginAndRegisterDialog();
                }
                else
                {
                    showConnectDialog();
                }
                break;
            case R.id.single_game:
                Intent intent=new Intent(MainActivity.this, FileListActivity.class);
                startActivityForResult(intent,1);
                break;
            case R.id.setting_button:
                Intent intentSetting=new Intent(MainActivity.this, SettingActivity.class);
                startActivity(intentSetting);
                break;
            case R.id.about_button:
                Intent intentAbout=new Intent(MainActivity.this, HelpActivity.class);
                intentAbout.putExtra("title","关于");
                intentAbout.putExtra("data", IO.getFromAssets(getApplicationContext(),"help.md"));
                startActivity(intentAbout);
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode==ConstantPool.OK_SELECT_RESULT_CODE){
            File f=new File(data.getStringExtra("path"));
            MobclickAgent.onEvent(getApplicationContext(),"single_play");
            startEmulatorActivity(f,null,false,true);
        }
    }

    private  String connectUsernameTemp ="";
    private  void showConnectDialog()
    {
        View view= LayoutInflater.from(this).inflate(R.layout.connect_to_game_dialog,null);
        final EditText userNameView=(EditText) view.findViewById(R.id.connect_to_game_edit);
        userNameView.setText(connectUsernameTemp);
        alertDialog=new AlertDialog.Builder(this)
                .setTitle("连接到")
                .setView(view)
                .setPositiveButton("连接",new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if(!TextUtils.isEmpty(userNameView.getText())) {
                            userId =  userNameView.getText().toString()+"@127.0.0.1/Smack";
                            connectUsernameTemp =userNameView.getText().toString();
                            log(userId);
                            smackTool.sendHello(userId,null);
                        }
                        else
                        {
                            showToast("用户名不能为空");
                            showConnectDialog();
                        }
                    }
                })
                .setNegativeButton("取消",null)
                .create();
        alertDialog.show();
    }


    /**
     * 显示登陆对话框
     */
    private  String userTemp="";
    private  String pwdTemp="";
    public  void showLoginAndRegisterDialog()
    {
        View view= LayoutInflater.from(this).inflate(R.layout.login_reg_dialog,null);
        final EditText userNameView=(EditText) view.findViewById(R.id.tb_username);
        final EditText pwdView=(EditText) view.findViewById(R.id.tb_pwd);
        userNameView.setText(userTemp);
        pwdView.setText(pwdTemp);
        alertDialog=new AlertDialog.Builder(this)
                .setTitle("登录/注册")
                .setView(view)

                .setNegativeButton("注册",new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        userTemp=userNameView.getText().toString();
                        pwdTemp=pwdView.getText().toString();
                        //注册
                        if(TextUtils.isEmpty(userNameView.getText())||TextUtils.isEmpty(pwdView.getText()))
                        {
                            showToast("用户名和密码都不能为空");
                            showLoginAndRegisterDialog();
                        }
                        else if(!userNameView.getText().toString().matches(userRegex))
                        {
                            showToast("用户名只能包含英文和数字字符且不少于3位");
                            showLoginAndRegisterDialog();
                        }
                        else if(pwdView.getText().toString().length()<7)
                        {
                            showToast("密码不能少于7位");
                            showLoginAndRegisterDialog();
                        }
                        else {
                            /**
                             * 注册
                             */

                            Intent intent=new Intent();
                            intent.setAction(MessageService.ACTION_REQUEST_REGISTER_LOGIN);
                            intent.putExtra("username",userTemp);
                            intent.putExtra("pwd",pwdTemp);
                            sendBroadcast(intent);
                        }
                    }
                })
                .setPositiveButton("登录",new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //登录
                        userTemp=userNameView.getText().toString();
                        pwdTemp=pwdView.getText().toString();
                        if(TextUtils.isEmpty(userNameView.getText())||TextUtils.isEmpty(pwdView.getText()))
                        {
                            showToast("用户名和密码都不能为空");
                            showLoginAndRegisterDialog();
                        }else if(!userNameView.getText().toString().matches(userRegex))
                        {
                            showToast("用户名只能包含英文和数字字符且不少于3位");
                            showLoginAndRegisterDialog();
                        }
                        else if(pwdView.getText().toString().length()<7)
                        {
                            showToast("密码不能少于7位");
                            showLoginAndRegisterDialog();
                        }
                        else {
                            /**
                             * 连接并登录
                             */
                            Intent intent=new Intent();
                            intent.setAction(MessageService.ACTION_REQUEST_CONNECT_LOGIN);
                            intent.putExtra("username",userTemp);
                            intent.putExtra("pwd",pwdTemp);
                            sendBroadcast(intent);
                        }
                    }
                })

                .create();
       // alertDialog.setCancelable(false);
        alertDialog.show();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();

    }

    /**
     * 显示toast
     * @param text
     */
    private  Toast toast;
    private void showToast(CharSequence text) {
        if (toast == null) {
            toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        } else {
            toast.setText(text);
        }
        toast.show();
    }

    public void onResume() {
        super.onResume();
        MobclickAgent.onResume(this);
        recFile();
    }

    private  void log(String log)
    {
        System.out.println("--------------->MainActivity"+" "+log);
    }
}
