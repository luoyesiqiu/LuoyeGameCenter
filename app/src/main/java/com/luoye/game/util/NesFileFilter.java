package com.luoye.game.util;

import java.io.File;
import java.io.FileFilter;

/**
 * Created by zyw on 2017/7/25.
 */
public class NesFileFilter implements FileFilter {

    private  boolean isFilterDir;

    /**
     * @param isFilterDir 是否过滤文件夹
     */
    public NesFileFilter(boolean isFilterDir)
    {
        this.isFilterDir=isFilterDir;
    }
    @Override
    public boolean accept(File file) {
        if(file.isDirectory()&&!isFilterDir)
        {
            return true;
        }
        return file.getName().toLowerCase().endsWith(".nes");
    }
}
