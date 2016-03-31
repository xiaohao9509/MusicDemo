package com.nick.musicdemo;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements ServiceConnection, AdapterView.OnItemClickListener, View.OnClickListener, Handler.Callback, SeekBar.OnSeekBarChangeListener {
    public static final int UPDATE_PROGRESS = 0;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("mm:ss", Locale.CHINA);
    public static final int UPDATE_LIST = 1;
    public static final int UPDATE_PLAY_BUTTON = 2;
    private SimpleCursorAdapter adapter;
    private MusicService service;
    private Handler handler = new Handler(this);
    private TextView current;
    private TextView duration;
    private SeekBar seekBar;
    private boolean userFlag;
    int i = 0;
    private ImageButton btn_play;
    private ListView listView;
    private int position;
    private View pre;
    private View next;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listView = (ListView) findViewById(R.id.main_list);
        adapter = new SimpleCursorAdapter(this, R.layout.item_music,
                null, new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST},
                new int[]{R.id.item_point, R.id.item_pic, R.id.item_title, R.id.item_art},
                SimpleCursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER);
        //设置绑定View,listView会在itemView上每一个控件里都调用一次,可以重新给itemView上的每一个控件重新设置一次
        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            //第一个参数,是itemView上的子控件对应的view
            //第二参数是对应条数信息的cursor
            //第三参数是,itemView的子控件的信息在cursor上的列编号
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                switch (view.getId()) {
                    case R.id.item_point:
                        if (cursor.getPosition() == service.getPosition()) {
                            //如果刷新的itemView时的position与正在播放的position相同,则显示正在播放的绿色竖条
                            view.setVisibility(View.VISIBLE);
                        } else {
                            view.setVisibility(View.INVISIBLE);//不同则不显示,但占位
                        }
                        return true;
                    case R.id.item_pic:
                        //Uri.withAppendedPath(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,cursor.getString(columnIndex)
                        //得到含有这张图片保存的地方的id的uri
                        Cursor query = getContentResolver().query(Uri.withAppendedPath(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
                                , cursor.getString(columnIndex))
                                , null, null, null, null);
                        if (query.getCount() == 1) {//如果有图片存着,则读取图片
                            query.moveToFirst();//跳到第一条数据
                            //得到图片的路径
                            String pathName = query.getString(query.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART));
                            if (pathName != null) {
                                //如果路径不为空,将图片设置到ImageView上
                                ((ImageView) view).setImageBitmap(BitmapFactory.decodeFile(pathName));
                            } else {
                                //如果路径为空,设置其他图片
                                ((ImageView) view).setImageResource(R.mipmap.ic_launcher);
                            }
                        } else {
                            //如果没有找到该图片的cursor,设置其他图片
                            ((ImageView) view).setImageResource(android.R.mipmap.sym_def_app_icon);
                        }
                        query.close();//关闭cursor
                        return true;
                }

                return false;
            }
        });
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(this);//设置监听ListView的Item的点击事件
        pre = findViewById(R.id.main_previous);
        pre.setOnClickListener(this);
        btn_play = (ImageButton) findViewById(R.id.main_play);
        btn_play.setOnClickListener(this);
        next = findViewById(R.id.main_next);
        next.setOnClickListener(this);


        //判断权限是否有拥有,如果没有则申请权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            bindMusicService();//拥有权限则绑定服务
        } else {
            //没有权限,申请权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
        }
        current = (TextView) findViewById(R.id.main_current);
        duration = (TextView) findViewById(R.id.main_duration);
        seekBar = (SeekBar) findViewById(R.id.main_seek);
        seekBar.setOnSeekBarChangeListener(this);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (service != null) {
            unbindService(this);
        }
    }

    //绑定播放器服务
    private void bindMusicService() {
        Intent intent = new Intent(this, MusicService.class);
        Messenger messenger = new Messenger(handler);//设置信使,让服务能传指令消息过来
        //使用Parcelable来序列化
        intent.putExtra("msg", messenger);
        startService(intent);//先启动服务再绑定,在解绑的时候服务不会被销毁,直接绑定,在解绑时,会销毁服务
        bindService(intent, this, BIND_AUTO_CREATE);
    }

    //获取权限后的返回结果方法
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        //判断是否获得权限,获得则绑定播放器服务,没有获得则结束activity
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            bindMusicService();
        } else {
            finish();
        }

    }


    //服务的连接对象,从中获得服务对象和cursor对象,并将Cursor放入adapter中,并发送消息更新播放进度条
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        this.service = ((MusicService.MusicBind) service).getService();
        Cursor cursor = this.service.getCursor();
        adapter.swapCursor(cursor);
        handler.sendEmptyMessage(UPDATE_PROGRESS);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    //当点击item时,运行服务的playOrPause方法,
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        service.playOrPause(position);

//        adapter.notifyDataSetChanged();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.main_previous://如果点击了上一首,判断是否小于零,小于零则将position设置为最后一首
                int position = service.getPosition() - 1;
                if (position < 0) {
                    position = adapter.getCount() - 1;
                }
                service.playOrPause(position);
                break;
            case R.id.main_play://点击播放键,则运行playOrPause方法
                service.playOrPause(Math.max(service.getPosition(), 0));
                break;
            case R.id.main_next://点击下一首,判断是否大于了歌曲的最大值,大于则将position设置为第一首
                int p = service.getPosition() + 1;
                if (p >= adapter.getCount()) {
                    p = 0;
                }
                service.playOrPause(p);
                break;
        }
    }


    //处理从服务,绑定服务成功和定时发更新进度条发送来的消息
    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case UPDATE_PROGRESS://更新进度条
                int current = service.getCurrent();
                int duration = service.getDuration();
                this.current.setText(sdf.format(new Date(current)));
                this.duration.setText(sdf.format(new Date(duration)));
                if (duration != 0 && !userFlag) {
                    seekBar.setProgress(current * seekBar.getMax() / duration);
                }
                handler.sendEmptyMessageDelayed(UPDATE_PROGRESS, 500);
                break;
            case UPDATE_LIST://当播放的歌发生变化时,更新listvie
                adapter.notifyDataSetChanged();
                int serP = service.getPosition();
                int temp = position - serP;
                if (temp > 10 || temp < -10) {
                    listView.setSelection(serP);//listView的item直接跳转,不慢慢滑至该处
                } else {
                    listView.smoothScrollToPosition(serP);//listView的item慢滑至该处
                }
                position = serP;
//                listView.smooth
                break;
            case UPDATE_PLAY_BUTTON://当歌曲播放状态改变时,更改播放和暂停对应的图标
                if (((Boolean) msg.obj)) {
                    btn_play.setImageResource(android.R.drawable.ic_media_pause);
                } else {
                    btn_play.setImageResource(android.R.drawable.ic_media_play);
                }
        }
        return true;
    }

    /**
     * 进度改变
     *
     * @param seekBar
     * @param progress
     * @param fromUser 是否来自用户的改变
     */
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//        if (fromUser){
//            int max = seekBar.getMax();
//            int duration = service.getDuration();
//            if (duration!=0){
//                service.seekTo(progress*duration/max);
//            }
//        }
    }

    /**
     * 开始触摸
     *
     * @param seekBar
     */
    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        //当用户触摸进度条时,设置为用户操作
        userFlag = true;
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        //当触摸停止时,获得进度条变化,返回给播放器服务,并重置用户操作标识
        int max = seekBar.getMax();
        int progress = seekBar.getProgress();
        int duration = service.getDuration();
        if (duration != 0) {
            service.seekTo(progress * duration / max);
        }
        userFlag = false;

    }
}