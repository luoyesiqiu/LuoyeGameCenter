package com.luoye.game.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.Toast;

import com.androidemu.EmuMedia;
import com.androidemu.Emulator;
import com.androidemu.EmulatorView;
import com.androidemu.nes.input.Keyboard;
import com.androidemu.nes.input.VirtualKeypad;
import com.luoye.game.R;
import  com.androidemu.nes.input.GameKeyListener;
import com.luoye.game.ThisApplication;
import com.luoye.game.callback.OnSendFileFinish;
import com.luoye.game.callback.ReceiveFileProgress;
import com.luoye.game.service.MessageService;
import com.luoye.game.util.ConstantPool;
import com.luoye.game.util.MediaScanner;
import com.luoye.game.util.NetTool;
import com.luoye.game.util.Utils;
import com.umeng.analytics.MobclickAgent;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smackx.ping.PingManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by zyw on 2017/7/19.
 */
public class EmulatorActivity extends Activity implements
        SurfaceHolder.Callback
        ,Emulator.FrameUpdateListener
        ,Emulator.OnFrameDrawnListener
        ,GameKeyListener
        ,View.OnTouchListener
{
    private EmulatorView emulatorView;
    private SharedPreferences sharedPrefs;
    private Emulator emulator;
    private int surfaceWidth;
    private int surfaceHeight;
    private VirtualKeypad vkeypad;
    private Rect  surfaceRegion=new Rect();;
    private boolean flipScreen;
    private Keyboard keyboard;
    private NetTool smackTool;
    private final   int maxFrameAhead=10;
    private Timer fpsTimer;
    private final int updateStateTime=10*1000;
    private static final int GAMEPAD_LEFT_RIGHT =
            (Emulator.GAMEPAD_LEFT | Emulator.GAMEPAD_RIGHT);
    private static final int GAMEPAD_UP_DOWN =
            (Emulator.GAMEPAD_UP | Emulator.GAMEPAD_DOWN);
    private static final int GAMEPAD_DIRECTION =
            (GAMEPAD_UP_DOWN | GAMEPAD_LEFT_RIGHT);
    private boolean inFastForward;
    private int fdsTotalSides;
    private ImageButton imageButton;
    private MediaScanner mediaScanner;
    private PopupWindow popupWindow;
    private  int frameCount=0;
    private LinkedList<Integer> localKeyList =new LinkedList<>();
    private LinkedList<Integer> remoteKeyList=new LinkedList<>();
    private Paint paint=new Paint();
    private PingManager pingManager;
    private Timer timer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(1024, 1024);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        final SharedPreferences prefs = sharedPrefs;
        // prefs.registerOnSharedPreferenceChangeListener(this);

        emulator = Emulator.createInstance(getApplicationContext(),
                getEmulatorEngine(prefs));
        configEmulator();
        EmuMedia.setOnFrameDrawnListener(this);

        setContentView(R.layout.emulator_main);
        imageButton=(ImageButton)findViewById(R.id.menu_button);
        imageButton.setOnClickListener(new MyOnClickListener());
        emulatorView = (EmulatorView) findViewById(R.id.emulator_view);
        emulatorView.getHolder().addCallback(this);
        emulatorView.setOnTouchListener(this);
        emulatorView.requestFocus();

        smackTool = ((ThisApplication)getApplication()).getSmack();
        pingManager = PingManager.getInstanceFor(smackTool.getConnection());

        configEmulatorView();

        if (!loadROM()) {
            finish();
            return;
        }
        emulator.setKeyStates(0);
        if(!isSinglePlay()) {
            emulator.setFrameUpdateListener(this);
            if (isServer()) {
                MobclickAgent.onEvent(getApplicationContext(),"net_play");
                timer = new Timer();
                timer.schedule(new NetPlaySyncTask(), updateStateTime, updateStateTime);
            } else {
                recFile();
            }
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(MessageService.ACTION_NORMAL_MSG);
            intentFilter.addAction(MessageService.ACTION_CMD_MSG);
            registerReceiver(broadcastReceiver, intentFilter);
        }
        paint.setColor(Color.YELLOW);
        paint.setTextSize(10);
        paint.setAntiAlias(true);
    }

    /**
     * 接受文件
     */
    private  void recFile()
    {
        smackTool.removeListenReceiveFile();//移除文件监听
        smackTool.listenReceiveFile(getCacheDir(), new ReceiveFileProgress() {
            @Override
            public void onProgress(String fileName, long current, long size) {
                if (current>=size) {
                    final File f= getTempStateFileNew();
                    if(f.exists()&&f.length()>0) {
                        //log("loadState");
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {

                                    emulator.loadState(f.getAbsolutePath());

                                }catch (Exception e){
                                    e.printStackTrace();
                                    log("load state fail.");
                                }
                            }
                        }).start();

                    }
                    //smackTool.resetFrame();
                }
            }
        });
    }

    /**
     * 广播接收者
     */
    private  BroadcastReceiver broadcastReceiver=new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(MessageService.ACTION_NORMAL_MSG))
            {
                String msg=intent.getStringExtra("data");
                String userShort=getRemoteUser().substring(0, getRemoteUser().indexOf('@'));
                showToast(userShort+":"+msg);
            }
            else if(intent.getAction().equals(MessageService.ACTION_CMD_MSG))
            {
                String data=intent.getStringExtra("data");
                if(data.equals(NetTool.CMD_END_GAME)){
                    onExit();
                    finish();
                }else if(data.equals(NetTool.CMD_RESET_ROM)){
                    emulator.reset();
                    smackTool.resetFrame();
                }
            }
        }
    };
    private  class MyOnClickListener implements View.OnClickListener{
        @Override
        public void onClick(View view) {
            if(view.getId()==R.id.menu_button)
            {
                showPopupWindow();
            }
        }
    }

    /**
     * 显示popupwindow
     */
    private  void showPopupWindow()
    {
        imageButton.setVisibility(View.INVISIBLE);
        View popupView=LayoutInflater.from(getApplicationContext()).inflate(R.layout.popup_window,null);
         popupWindow=new PopupWindow(popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setContentView(popupView);
        popupWindow.setBackgroundDrawable(new BitmapDrawable());//有了这句才使setOutsideTouchable生效
        popupWindow.setOutsideTouchable(true);

        popupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                imageButton.setVisibility(View.VISIBLE);
            }
        });

        //截图
        ImageButton screenshotButton= (ImageButton) popupView.findViewById(R.id.popup_screenshot);
        screenshotButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onScreenshot();
                popupWindow.dismiss();
            }
        });
        //关闭游戏
        ImageButton powerButton=(ImageButton)popupView.findViewById(R.id.popup_power);
        powerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(isServer()) {
                    smackTool.sendEndGame(getRemoteUser(), null);
                    onExit();
                    finish();
                }
                else if(isSinglePlay())
                {
                    onExit();
                    finish();
                }
                else
                {
                    showToast("创建游戏的人才能主动关闭游戏哦~");
                }
                popupWindow.dismiss();
            }
        });
        //重置游戏
        ImageButton resetButton=(ImageButton)popupView.findViewById(R.id.popup_restart);
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(isServer()) {
                    smackTool.sendReset(getRemoteUser(), null);

                    emulator.reset();
                    smackTool.resetFrame();
                }
                else if(isSinglePlay())
                {
                    emulator.reset();
                }
                else
                {
                    showToast("创建游戏的人才能主动重置游戏哦~");
                }
                popupWindow.dismiss();
            }
        });
        final String[] items=getResources().getStringArray(R.array.popup_list_array);
        ArrayAdapter<String> arrayAdapter=new ArrayAdapter<>(getApplicationContext(),R.layout.popup_list_item,items);
        ListView listView=(ListView) popupView.findViewById(R.id.popup_list);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if(!isSinglePlay()) {
                    smackTool.sendTextMsg(getRemoteUser(), items[i], null);
                    popupWindow.dismiss();
                }
                else{
                    showToast("单机时不能发送消息哦~");
                    popupWindow.dismiss();
                }
            }
        });

        listView.setAdapter(arrayAdapter);

        popupWindow.showAsDropDown(imageButton,0,imageButton.getHeight()*-1);
    }

    /**
     * 截图
     */
    private void onScreenshot() {
        File dir = new File(ConstantPool.GAME_ROOT+File.separator+"Screenshot");
        if (!dir.exists() && !dir.mkdir()) {
            return;
        }
        SimpleDateFormat simpleDateFormat=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String date=simpleDateFormat.format(new Date());
        String name ="Screenshot " +date + ".png";
        File file = new File(dir, name);

        pauseEmulator();

        FileOutputStream out = null;
        try {
            try {
                out = new FileOutputStream(file);
                Bitmap bitmap = getScreenshot();
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                bitmap.recycle();

                Toast.makeText(this, "截图已保存："+file.getAbsolutePath(),
                        Toast.LENGTH_SHORT).show();

                if (mediaScanner == null)
                    mediaScanner = new MediaScanner(this);
                mediaScanner.scanFile(file.getAbsolutePath(), "image/png");

            } finally {
                if (out != null)
                    out.close();
            }
        } catch (IOException e) {}

        resumeEmulator();
    }

    /**
     * 获取截图
     * @return
     */
    private Bitmap getScreenshot() {
        final int w = emulator.getVideoWidth();
        final int h = emulator.getVideoHeight();

        ByteBuffer buffer = ByteBuffer.allocateDirect(w * h * 2);
        emulator.getScreenshot(buffer);

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        bitmap.copyPixelsFromBuffer(buffer);
        return bitmap;
    }
    private   class NetPlaySyncTask extends TimerTask{
        @Override
        public void run() {
            onNetPlaySync();
        }
    }
    /**
     * 同步
     */
    private void onNetPlaySync() {

        final File file = getTempStateFile();
        final File fileNew = getTempStateFileNew();
        emulator.saveState(file.getAbsolutePath());//文件追加模式
        try {
            Utils.writeFile(fileNew,Utils.readFile(file));
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(file.exists())
            file.delete();
        // log("onNetPlaySync "+fileNew.getAbsolutePath()+","+fileNew.exists());
        if(fileNew.exists()) {
            smackTool.sendState(getRemoteUser(), fileNew, new OnSendFileFinish() {
                @Override
                public void onFinish(File f, boolean isSuccess) {
                    if(isSuccess)
                    {
                        smackTool.resetFrame();
                    }
                }
            });
        }
    }

    /**
     * 配置模拟器，重要
     */
    private  void configEmulator()
    {
        emulator.setOption("flipScreen", false);
        emulator.setOption("frameSkipMode", "auto");
        if(sharedPrefs.getBoolean("gameSound",true)) {
            emulator.setOption("soundEnabled", true);

            emulator.setOption("soundVolume",100);
        }else
        {
            emulator.setOption("soundEnabled", true);//设置成false会爆炸
            emulator.setOption("soundVolume",0);
        }
        emulator.setOption("maxFrameSkips",2);
        emulator.setOption("maxFramesAhead",0);
        emulator.setOption("refreshRate","default");

        emulator.setOption("accurateRendering",true);
        if(isSinglePlay()) {
            emulator.setOption("secondController", "none");
        }
        else {
            emulator.setOption("secondController", "gamepad");
        }
        emulator.enableCheats(false);
    }

    /**
     * 配置视图
     */
    private  void configEmulatorView()
    {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        //注意
        float ratio = 1.3333f;
        if (ratio != 0) {
            float dpiRatio = metrics.xdpi / metrics.ydpi;
            // some models seem to report wrong dpi
            if (dpiRatio < 1.6667f && dpiRatio > 0.6f)
                ratio *= dpiRatio;
        }
        //emulatorView.setAspectRatio(ratio);
        sharedPrefs.edit()
                .putString("vkeypadLayout", "bottom_bottom")
                .apply();
        vkeypad=new VirtualKeypad(emulatorView,this);

        keyboard=new Keyboard(emulatorView,this);
        emulatorView.setScalingMode(EmulatorView.SCALING_PROPORTIONAL);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

    }
    /**
     * 判断ROM是否支持
     * @param file
     * @return
     */
    private boolean isROMSupported(String file) {
        file = file.toLowerCase();

        String[] filters = getResources().
                getStringArray(R.array.file_chooser_filters);
        for (String f : filters) {
            if (file.endsWith(f))
                return true;
        }
        return false;
    }

    /**
     * 加载ROM
     * @return
     */
    private boolean loadROM() {
        String path = getROMFilePath();

        if (!isROMSupported(path)) {
            Toast.makeText(this, R.string.rom_not_supported,
                    Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }
        if (!emulator.loadROM(path)) {
            Toast.makeText(this, R.string.load_rom_failed,
                    Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }
        // reset fast-forward on ROM load
        inFastForward = false;

        emulatorView.setActualSize(
                emulator.getVideoWidth(), emulator.getVideoHeight());
         fdsTotalSides = emulator.getOption("fdsTotalSides");

//        if (sharedPrefs.getBoolean("quickLoadOnStart", true))
//            quickLoad();

        return true;
    }

//    private void quickLoad() {
//
//    }

    /**
     * 获取ROM文件
     * @return
     */
    private String getROMFilePath() {
        String path=getIntent().getData().getPath();
        return path;
    }
    /**
     * 获取远程用户
     * @return
     */
    private String getRemoteUser() {
        String user=getIntent().getStringExtra("user");

        return user;
    }

    /**
     * 获取是否当作服务器
     * @return
     */
    private boolean isServer() {
        boolean isServer=getIntent().getBooleanExtra("isServer",false);
        return isServer;
    }
    /**
     * 获取是否当作单人游戏
     * @return
     */
    private boolean isSinglePlay() {
        boolean isSinglePlay=getIntent().getBooleanExtra("isSinglePlay",false);
        return isSinglePlay;
    }
    private String getEmulatorEngine(SharedPreferences prefs) {
        return "nes";
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        emulator.setSurface(surfaceHolder);
    }

    public void surfaceChanged(SurfaceHolder holder,
                               int format, int width, int height) {

        surfaceWidth = width;
        surfaceHeight = height;

        if (vkeypad != null)
            vkeypad.resize(width, height);

        final int w = emulator.getVideoWidth();
        final int h = emulator.getVideoHeight();
        surfaceRegion.left = (width - w) / 2;
        surfaceRegion.top = (height - h) / 2;
        surfaceRegion.right = surfaceRegion.left + w;
        surfaceRegion.bottom = surfaceRegion.top + h;

        emulator.setSurfaceRegion(
                surfaceRegion.left, surfaceRegion.top, w, h);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        if (vkeypad != null)
            vkeypad.destroy();

        emulator.setSurface(null);
    }


    @Override
    public void onGameKeyChanged() {

        int states = keyboard.getKeyStates();

        if (vkeypad != null)
            states |= vkeypad.getKeyStates();

        // resolve conflict keys
        if ((states & GAMEPAD_LEFT_RIGHT) == GAMEPAD_LEFT_RIGHT)
            states &= ~GAMEPAD_LEFT_RIGHT;
        if ((states & GAMEPAD_UP_DOWN) == GAMEPAD_UP_DOWN)
            states &= ~GAMEPAD_UP_DOWN;

        emulator.setKeyStates(states);

    }

    private int flipGameKeys(int states) {
        return 0;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {

            // reset keys
            keyboard.reset();
            if (vkeypad != null)
                vkeypad.reset();
            emulator.setKeyStates(0);

            emulator.resume();
        }
//        else
//            emulator.pause();
    }
    public boolean onTouch(View v, MotionEvent event) {
        if (vkeypad != null)
            return vkeypad.onTouch(event, flipScreen);

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int x = (int) event.getX() *
                    surfaceWidth / emulatorView.getWidth();
            int y = (int) event.getY() *
                    surfaceHeight / emulatorView.getHeight();
            if (flipScreen) {
                x = surfaceWidth - x;
                y = surfaceHeight - y;
            }
            if (surfaceRegion.contains(x, y)) {
                x -= surfaceRegion.left;
                y -= surfaceRegion.top;
                emulator.fireLightGun(x, y);
                return true;
            }
        }
        return false;
    }



    @Override
    public void onFrameDrawn(Canvas canvas) {
        if (vkeypad != null)
            vkeypad.draw(canvas);
        if(!isSinglePlay()) {

            try {
                if (pingManager.pingMyServer()) {
                    canvas.drawText("ping:"+pingManager.getPingInterval(), 20, 20, paint);
                }
            } catch (SmackException.NotConnectedException e) {
                e.printStackTrace();
            }
        }
    }


    private  void onExit()
    {
        if (emulator != null)
            emulator.unloadROM();
        emulator.setFrameUpdateListener(null);
        smackTool.removeListenReceiveFile();
        if(timer!=null)
            timer.cancel();
    }

    @Override
    protected void onPause() {
        super.onPause();
        pauseEmulator();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumeEmulator();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            unregisterReceiver(broadcastReceiver);
        }catch (Exception e)
        {
        }
        onExit();
    }

    private void loadKeyBindings(SharedPreferences prefs) {

    }
    private static int getScalingMode(String mode) {
        if (mode.equals("original"))
            return EmulatorView.SCALING_ORIGINAL;
        if (mode.equals("2x"))
            return EmulatorView.SCALING_2X;
        if (mode.equals("proportional"))
            return EmulatorView.SCALING_PROPORTIONAL;
        return EmulatorView.SCALING_STRETCH;
    }

    private static int getScreenOrientation(String orientation) {
        if (orientation.equals("landscape"))
            return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        if (orientation.equals("portrait"))
            return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    }

    private void setGameSpeed(float speed) {
        pauseEmulator();
        emulator.setOption("gameSpeed", Float.toString(speed));
        resumeEmulator();
    }

    private void pauseEmulator() {
        emulator.pause();
    }

    private void resumeEmulator() {
        if (hasWindowFocus())
            emulator.resume();
    }

    private File getTempStateFile() {
        return new File(getCacheDir(), "saved_state");
    }
    private File getTempStateFileNew() {
        return new File(getCacheDir(), "saved_state_be_sent");
    }


    /**
     * 此处可以设置键值决定玩家是1p还是2p
     * @param keys
     * @return
     * @throws IOException
     * @throws InterruptedException
     */

//    public int onFrameUpdate1(int keys) throws IOException, InterruptedException {
//        //log("onFrameUpdate，"+keys);
//        frameCount++;
//        localKeyList.add(keys);
//        if(frameCount==20) {
//            log("frameCount==100");
//            frameCount=0;
//            final Integer[] remoteKeys = smackTool.sendFrameUpdate(getRemoteUser(), localKeyList.toString(), null);
//            if(remoteKeys==null) {
//                log("remoteKeys==null");
//                return 0;
//            }
//            for(Iterator<Integer> iterator=remoteKeyList.iterator();iterator.hasNext();) {
//                remoteKeyList.add(iterator.next());
//            }
//        }
//        if(localKeyList.size()>0&&remoteKeyList.size()>0)
//        {
//            int localKey= localKeyList.pollFirst();
//            int remoteKey=remoteKeyList.pollFirst();
//            log(localKey+","+remoteKey);
//            if (isServer())
//                return makeKeyStates(localKey, remoteKey);//这是传回底层的
//            else
//                return makeKeyStates(remoteKey, keys);
//        }
//        else
//        {
//            return makeKeyStates(0, 0);
//        }
//    }

    @Override
    public int onFrameUpdate(int keys) throws IOException, InterruptedException {

        final int remoteKey = smackTool.sendFrameUpdate(getRemoteUser(),keys, null);
        if (isServer())
            return makeKeyStates(keys, remoteKey);//这是传回底层的
        else
            return makeKeyStates(remoteKey, keys);
    }

    /**
     * 合并高低位，取p1高位，取p2低位
     * @param p1
     * @param p2
     * @return
     */
    private static int makeKeyStates(int p1, int p2) {
        return (p2 << 16) //去除高位
                |
                (p1 & 0xffff);//去除低位
    }

    private void setFlipScreen(SharedPreferences prefs, Configuration config) {
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE)
            flipScreen = prefs.getBoolean("flipScreen", false);
        else
            flipScreen = false;
        log("setFlipScreen");
        emulator.setOption("flipScreen", flipScreen);
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
    private static void log(String log){
        System.out.println("------------------>EmulatorActivity："+log);
    }

    @Override
    public void onBackPressed() {
        //啥都不做
    }
}
