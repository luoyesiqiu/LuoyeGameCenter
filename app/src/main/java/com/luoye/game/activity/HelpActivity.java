package com.luoye.game.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.ProgressBar;

import com.luoye.game.R;
import com.luoye.game.util.IO;
import com.luoye.game.util.StatusBar;
import com.luoye.game.view.MdWebView;

/**
 * Created by zyw on 2017/8/10.
 */
public class HelpActivity extends Activity {

    private MdWebView wv;
    private ProgressBar progressBar;
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {

        // TODO: Implement this method
        super.onCreate(savedInstanceState);
        setContentView(R.layout.preview_compose);
        wv=(MdWebView)findViewById(R.id.previewmdWebView1);
        progressBar=(ProgressBar)findViewById(R.id.webview_progressBar) ;

        wv.setWebChromeClient(new WebChromeClient(){
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if(newProgress==100){
                    progressBar.setVisibility(View.GONE);//加载完网页进度条消失
                }
                else{
                    progressBar.setVisibility(View.VISIBLE);//开始加载网页时显示进度条
                    progressBar.setProgress(newProgress);//设置进度值
                }
            }
        });
        wv.setDownloadListener(new MyWebViewDownLoadListener());
        Intent intent=getIntent();
        String data=intent.getStringExtra("data");
        String title=intent.getStringExtra("title");
        setTitle(title);
        StringBuilder sb=new StringBuilder();
        sb.append("<html>\n<head>\n\n");
        sb.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n");
        //sb.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"markdown.css\">\n");
        sb.append("<style type=\"text/css\">\n");
        sb.append(IO.getFromAssets(this, "markdown.css"));
        sb.append("</style>");
        sb.append("</head>\n<body>\n");
        sb.append(IO.md2html(data));
        sb.append("\n</body>\n");
        sb.append("</html>");
        wv.loadData(sb.toString());

        getActionBar().setDisplayHomeAsUpEnabled(true);
    }
    private class MyWebViewDownLoadListener implements DownloadListener {

        @Override
        public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype,
                                    long contentLength) {
            Log.i("tag", "url="+url);
            Log.i("tag", "userAgent="+userAgent);
            Log.i("tag", "contentDisposition="+contentDisposition);
            Log.i("tag", "mimetype="+mimetype);
            Log.i("tag", "contentLength="+contentLength);
            Uri uri = Uri.parse(url);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        }
    }
    @Override
    public void onBackPressed() {
        wv.goBack();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case android.R.id.home:
                finish();
                break;

        }
        return super.onOptionsItemSelected(item);
    }

}
