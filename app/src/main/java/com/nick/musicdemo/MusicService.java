package com.nick.musicdemo;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.support.v7.app.NotificationCompat;
import android.text.TextUtils;
import android.widget.RemoteViews;

import java.io.IOException;
import java.util.Random;

public class MusicService extends Service {
    private Cursor cursor;
    private MediaPlayer player;
    private int position = -1;//播放之前,初始值为-1,不显示指示块
    private Messenger messenger;
    private Random random;
    private Notification notification;

    public MusicService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                null, MediaStore.Audio.Media.IS_MUSIC+" = 1", null, null);
        random = new Random(SystemClock.uptimeMillis());
        player = new MediaPlayer();
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {//当音乐播放完的监听器,可在这实现歌曲循环
            @Override
            public void onCompletion(MediaPlayer mp) {
//                单曲循环
//                mp.start();
//                循环播放
//                int p = position+1;
//                if (p>cursor.getCount()){
//                    p = 0;
//                }
//                playOrPause(p);
                //顺序播放
//                int p = position+1;
//                if (p>=cursor.getCount()){
//                    sendUpdatePlayState();
//                }else {
//                    playOrPause(p);
//                }

                //随机播放
                playOrPause(random.nextInt(cursor.getCount()));
            }
        });
    }

    public void playOrPause(int position) {
        boolean playing = player.isPlaying();
        if (this.position == position) {
            if (player.isPlaying()) {
                player.pause();//有些手机不能stop后再start,所以一般使用pause
            } else {
                player.start();
            }
        } else {
            playMusic(position);
        }
        if (playing != player.isPlaying()) {//如果播放状态发生变化,调用sendUpdatePlayState方法,向activity发送更新消息
            sendUpdatePlayState();
        }
    }

    private void sendUpdatePlayState() {
        Message message = Message.obtain();
        message.what = MainActivity.UPDATE_PLAY_BUTTON;
        message.obj = player.isPlaying();//将播放状态设置进去
        try {
            messenger.send(message);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        if (notification!=null){//当通知不为空时,更新通知内容
            if (player.isPlaying()){
                notification.contentView.setImageViewResource(R.id.noti_play,android.R.drawable.ic_media_pause);
            }else {
                notification.contentView.setImageViewResource(R.id.noti_play,android.R.drawable.ic_media_play);
            }
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(0,notification);//重新发送通知
        }
    }

    private void playMusic(int position) {
        cursor.moveToPosition(position);//将cursor移动至该position去
        String data = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));//得到歌曲的地址
        try {

            player.reset();
            player.setDataSource(data);
//            player.prepare();//准备
//            player.start();//准备完成后调用
            player.prepareAsync();//异步加载音频,异步准备,播放网络歌曲加载数据时间长,必须使用异步加载
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();//异步准备,一定在准备完成后,才能开始,网络必须这样写
                    sendUpdatePlayState();//准备好后,发送更新播放状态消息
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.position = position;
        Message message = Message.obtain();
        message.what = MainActivity.UPDATE_LIST;//发送更新list消息
        try {
            messenger.send(message);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        if (notification!=null) {
            updateNotification();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //由于我们是设置关闭activity才显示通知,所以有通知就没有activity
        String id = intent.getStringExtra("id");//获得来自通知上发出的信息
        if (!TextUtils.isEmpty(id)) {//如果不为空,则通知栏发了消息
            switch (id) {
                case "play"://播放按钮
                    playOrPause(position);
                    break;
                case "pre"://上一首
                    int pre = position -1;
                    if (pre<0){
                        pre = cursor.getCount()-1;
                    }
                    playOrPause(pre);
                    break;
                case "next"://下一首
                    int next = position +1;
                    if (next>=cursor.getCount()){
                        next = 0;
                    }
                    playOrPause(next);
                    break;
            }
        }else {
            //如果为空,则是activity发来的messenger信使对象
            messenger = intent.getParcelableExtra("msg");
        }

        return super.onStartCommand(intent, flags, startId);
    }

    //当activity解绑后,再是绑定会调用这个方法
    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        sendUpdatePlayState();//再次绑定调用发送更新播放状态消息
        Message message = Message.obtain();
        message.what = MainActivity.UPDATE_LIST;//在发送更新listView消息
        try {
            messenger.send(message);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        if (notification!=null) {//如果通知不为空,则取消通知
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(0);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return new MusicBind();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (player.isPlaying()) {//如果仍处理播放状态,则发出通知,在上面显示播放状态
            if (notification==null) {//如果通知对象为空,创建通知
                notification = new NotificationCompat.Builder(this)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContent(new RemoteViews(getPackageName(), R.layout.notification_content))//自定义通知
                        //设置点击通知发出延时意图
                        .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_ONE_SHOT))
                        .setAutoCancel(true)//通知自动取消
                        .build();
                notification.flags |= Notification.FLAG_NO_CLEAR;//设置通知不可被清除
                //设置三个按钮的意图和需要发送的消息
                Intent play = new Intent(this, MusicService.class);
                play.putExtra("id","play");
                Intent pre = new Intent(this, MusicService.class);
                pre.putExtra("id","pre");
                Intent next = new Intent(this, MusicService.class);
                next.putExtra("id","next");
                //设置点击它们时,要发送的延时意图,延时意图的请求码不能有重复,否则会覆盖
                notification.contentView.setOnClickPendingIntent(R.id.noti_play,
                        PendingIntent.getService(this,1,play,PendingIntent.FLAG_UPDATE_CURRENT));

                notification.contentView.setOnClickPendingIntent(R.id.noti_previous,
                        PendingIntent.getService(this,2,pre,PendingIntent.FLAG_UPDATE_CURRENT));

                notification.contentView.setOnClickPendingIntent(R.id.noti_next,
                        PendingIntent.getService(this,3,next,PendingIntent.FLAG_UPDATE_CURRENT));
            }
            updateNotification();//更新通知
        }
        return true;
    }




    private void updateNotification() {
        cursor.moveToPosition(position);//cursor跳至当前position处
        Cursor query = getContentResolver().query(Uri.withAppendedPath(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID))),
                null, null, null, null);
        if (query.getCount() == 1) {
            query.moveToFirst();
            String file = query.getString(query.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART));
            if (file!=null){
                notification.contentView.setImageViewBitmap(R.id.noti_icon, BitmapFactory.decodeFile(file));
            }else {
                notification.contentView.setImageViewResource(R.id.noti_icon,R.mipmap.ic_launcher);
            }
        }else {
            notification.contentView.setImageViewResource(R.id.noti_icon,R.mipmap.ic_launcher);
        }
        query.close();
        String title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
        String artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
        notification.contentView.setTextViewText(R.id.noti_title,title);
        notification.contentView.setTextViewText(R.id.noti_sub,artist);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(0,notification);
    }

    public Cursor getCursor() {
        return cursor;
    }

    /**
     * 当前时间
     *
     * @return
     */
    public int getCurrent() {
        return player.getCurrentPosition();
    }

    /**
     * 总时长
     *
     * @return
     */
    public int getDuration() {
        return player.getDuration();
    }

    public int getPosition() {
        return position;
    }

    public void seekTo(int p) {
        player.seekTo(p);
    }

    public class MusicBind extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }
}