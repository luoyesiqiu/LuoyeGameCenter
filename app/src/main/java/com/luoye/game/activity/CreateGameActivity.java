package com.luoye.game.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.luoye.game.R;
import com.luoye.game.ThisApplication;
import com.luoye.game.callback.SendFileCallback;
import com.luoye.game.service.MessageService;
import com.luoye.game.util.ConstantPool;
import com.luoye.game.util.NetTool;
import com.luoye.game.util.StatusBar;
import com.luoye.game.util.Utils;
import com.securepreferences.SecurePreferences;

import java.io.File;

/**
 * Created by zyw on 2017/7/24.
 */
public class CreateGameActivity extends Activity {
    private Button selectFileButton;
    private EditText gameNameEditText;
    private EditText acceptUserEditText;
    private Button createGameButton;
    private ProgressDialog progressWaitDialog;
    private NetTool smackTool;
    private  String path;
    private  String gameName;
    private  String gameMd5;
    private  String acceptUser;
    private  String gameInfo;
    private  String userId;
    private  ProgressDialog progressDialog;
    private SharedPreferences sharedPreferences;
    private  ActivityState state;
    private enum ActivityState{
        NORMAL,WAIT,BUSY
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.create_game_layout);
        sharedPreferences=new SecurePreferences(this, ConstantPool.DATA_TYPE_NIL,"data");
        getActionBar().setDisplayHomeAsUpEnabled(true);
        selectFileButton=(Button)findViewById(R.id.button_select_file);
        selectFileButton.setOnClickListener(new ClickEvent());
        gameNameEditText=(EditText)findViewById(R.id.editText_game_name);
        acceptUserEditText=(EditText)findViewById(R.id.editText_accept_user);
        createGameButton=(Button) findViewById(R.id.button_create_game);
        createGameButton.setOnClickListener(new ClickEvent());

        progressWaitDialog =new ProgressDialog(this);
        progressWaitDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                state=ActivityState.NORMAL;
            }
        });
        smackTool =((ThisApplication)getApplication()).getSmack();
        state=ActivityState.NORMAL;
        IntentFilter intentFilter=new IntentFilter();
        intentFilter.addAction(MessageService.ACTION_CMD_MSG);
        registerReceiver(broadcastReceiver,intentFilter);
    }

    private BroadcastReceiver broadcastReceiver=new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(MessageService.ACTION_CMD_MSG)) {
                //收到HELLO消息
                    String data = intent.getStringExtra("data");
                    String user = intent.getStringExtra("user");
                    String userShort=user.substring(0, user.indexOf('@'));
                    acceptUser = acceptUserEditText.getText().toString();
                    if (data.equals(NetTool.CMD_HELLO)) {
                        if(acceptUser.equals("")||acceptUser.equals(userShort)) {
                            //等待状态才回应
                            if (state == ActivityState.WAIT) {
                                state = ActivityState.BUSY;
                                userId = user;
                                showToast( userShort+ " 已连接");
                                gameInfo = gameMd5 + "," + gameName;
                                smackTool.sendGameInfo(userId, gameInfo, null);

                                log("Hello");
                            }
                        }
                    }
                    else if(data.equals(NetTool.CMD_GET_ROM))
                    {
                        if(progressWaitDialog!=null)
                            progressWaitDialog.dismiss();

                        progressDialog =new ProgressDialog(CreateGameActivity.this);
                        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                        progressDialog.setMessage("文件传输中...");
                        progressDialog.show();
                        smackTool.sendROM(userId, new File(path), new SendFileCallback() {
                            @Override
                            public void onSendFileProgress(double progress) {
                            if(progress<1.0) {
                                progressDialog.setProgress((int)(progress*100));
                                progressDialog.setMax(100);
                            }
                            }
                        });
                    }
                    else if(data.equals(NetTool.CMD_ALL_OK)){
                        if(progressDialog!=null)
                            progressDialog.dismiss();
                        if(progressWaitDialog!=null)
                            progressWaitDialog.dismiss();
                        state=ActivityState.NORMAL;
                        startEmulatorActivity(new File(path),intent.getStringExtra("user"));
                    }
            }


        }
    };
    private final  class ClickEvent implements View.OnClickListener
    {
        @Override
        public void onClick(View view) {
            if(view.getId()==R.id.button_select_file)
            {
                Intent intent=new Intent(CreateGameActivity.this,FileListActivity.class);
                startActivityForResult(intent,0);
            }
            else if(view.getId()==R.id.button_create_game)
            {
                if(path!=null) {
                    state = ActivityState.WAIT;
                    progressWaitDialog.setMessage(""+sharedPreferences.getString("username","null")+"，等待玩家连接中...");
                    progressWaitDialog.show();
                }else
                {
                    showToast("请先选择ROM文件");
                }
            }
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //super.onActivityResult(requestCode, resultCode, data);
        if(resultCode== ConstantPool.OK_SELECT_RESULT_CODE)
        {
            String path=data.getStringExtra("path");
            String fileName=new File(path).getName();
            gameName=fileName.substring(0,fileName.lastIndexOf('.'));
            this.path=path;
            this.gameMd5= Utils.getMD5(new File(path));
            selectFileButton.setText(fileName);
            gameNameEditText.setText(gameName);
        }
    }
    private  void startEmulatorActivity(File f,String remoteUser)
    {
        Intent intentEmulator=new Intent(CreateGameActivity.this,EmulatorActivity.class);
        intentEmulator.setData(Uri.fromFile(f));
        intentEmulator.putExtra("user",remoteUser);
        intentEmulator.putExtra("isServer",true);
        startActivity(intentEmulator);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId()==android.R.id.home)
        {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 显示toast
     * @param text
     */
    private Toast toast;
    private void showToast(CharSequence text) {
        if (toast == null) {
            toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        } else {
            toast.setText(text);
        }
        toast.show();
    }

    private  void log(String log)
    {
        System.out.println("--------------->CreateGameActivity"+" "+log);
    }
}
