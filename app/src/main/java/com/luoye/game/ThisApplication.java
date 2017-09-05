package com.luoye.game;

import android.app.Application;

import com.luoye.game.util.NetTool;
import com.umeng.analytics.MobclickAgent;

import org.jivesoftware.smack.android.AndroidSmackInitializer;

/**
 * Created by zyw on 2017/7/24.
 */
public class ThisApplication extends Application{
    private NetTool smackTool;
    @Override
    public void onCreate() {
        super.onCreate();
        AndroidSmackInitializer androidSmackInitializer=new AndroidSmackInitializer();
        androidSmackInitializer.initialize();
        smackTool= NetTool.getInstance(this);

        MobclickAgent.setScenarioType(this, MobclickAgent.EScenarioType.E_UM_NORMAL);
    }

    public NetTool getSmack()
    {
        return  smackTool;
    }
}
