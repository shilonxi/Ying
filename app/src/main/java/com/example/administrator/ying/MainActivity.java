package com.example.administrator.ying;

import java.util.ArrayList;
import java.util.List;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.content.Context;
import android.graphics.Color;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;
import com.baidu.trace.LBSTraceClient;
import com.baidu.trace.OnEntityListener;
import com.baidu.trace.OnStartTraceListener;
import com.baidu.trace.Trace;

public class MainActivity extends AppCompatActivity
{
    int gatherInterval=3;
    //位置采集周期（秒）
    int packInterval=12;
    //打包周期（秒）
    String entityName=null;
    //entity标识
    long serviceId=138452;
    //鹰眼服务ID
    int traceType=2;
    //轨迹服务类型
    private static OnStartTraceListener startTraceListener=null;
    //开启轨迹服务监听器
    private static MapView mapView=null;
    private static BaiduMap baiduMap=null;
    private static OnEntityListener entityListener=null;
    private RefreshThread refreshThread=null;
    //刷新地图线程以获取实时点
    private static MapStatusUpdate msUpdate=null;
    private static BitmapDescriptor realtimeBitmap;
    //图标
    private static OverlayOptions overlay;
    //覆盖物
    private static List<LatLng> pointList=new ArrayList<LatLng>();
    //定位点的集合
    private static PolylineOptions polyline=null;
    //路线覆盖物
    private Trace trace;
    //实例化轨迹服务
    private LBSTraceClient client;
    //实例化轨迹服务客户端

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        SDKInitializer.initialize(getApplicationContext());
        setContentView(R.layout.activity_main);
        init();
        //相关变量初始化
        initOnEntityListener();
        //初始化实体监听器
        initOnStartTraceListener();
        //初始化轨迹追踪监听器
        client.startTrace(trace, startTraceListener);
        //开启轨迹服务
    }

    private void init()
    {
        mapView=(MapView)findViewById(R.id.mapView);
        baiduMap=mapView.getMap();
        mapView.showZoomControls(false);
        entityName=getImei(getApplicationContext());
        //手机Imei值的获取，用来充当实体名
        client=new LBSTraceClient(getApplicationContext());
        //实例化轨迹服务客户端
        trace=new Trace(getApplicationContext(),serviceId,entityName,traceType);
        //实例化轨迹服务
        client.setInterval(gatherInterval,packInterval);
        //设置位置采集和打包周期
    }

    private void initOnEntityListener()
    {
        entityListener=new OnEntityListener()
        {
            @Override
            public void onRequestFailedCallback(String arg0)
            {
                Looper.prepare();
                Toast.makeText(getApplicationContext(),"entity请求失败的回调接口信息："+arg0,Toast.LENGTH_SHORT).show();
                Looper.loop();
            }

            @Override
            public void onQueryEntityListCallback(String arg0)
            {
                showRealtimeTrack(arg0);
                //调用实时轨迹方法
            }
        };
    }

    private void initOnStartTraceListener()
    {
        //实例化开启轨迹服务回调接口
        startTraceListener=new OnStartTraceListener()
        {
            //开启轨迹服务回调接口（arg0 : 消息编码，arg1 : 消息内容，详情查看类参考）
            @Override
            public void onTraceCallback(int arg0,String arg1)
            {
                Log.i("TAG","onTraceCallback="+arg1);
                if(arg0==0||arg0==10006)
                {
                    startRefreshThread(true);
                }
            }

            //轨迹服务推送接口（用于接收服务端推送消息，arg0 : 消息类型，arg1 : 消息内容，详情查看类参考）
            @Override
            public void onTracePushCallback(byte arg0,String arg1)
            {
                Log.i("TAG","onTracePushCallback="+arg1);
            }
        };
    }

    private void startRefreshThread(boolean isStart)
    {
        if(refreshThread==null)
        {
            refreshThread=new RefreshThread();
        }
        refreshThread.refresh = isStart;
        if(isStart)
        {
            if(!refreshThread.isAlive())
            {
                refreshThread.start();
            }
        }else
        {
            refreshThread=null;
        }
    }
    //启动刷新线程

    private class RefreshThread extends Thread
    {
        protected boolean refresh=true;
        public void run()
        {
            while(refresh)
            {
                queryRealtimeTrack();
                try{
                    Thread.sleep(packInterval*1000);
                }catch(InterruptedException e)
                {
                    System.out.println("线程休眠失败");
                }
            }
        }
    }
    //轨迹刷新线程

    private void queryRealtimeTrack()
    {
        String entityName=this.entityName;
        String columnKey="";
        int returnType=0;
        int activeTime=0;
        int pageSize=10;
        int pageIndex=1;
        this.client.queryEntityList(serviceId,entityName,columnKey,returnType,activeTime,pageSize,pageIndex,entityListener);
    }
    //查询实时线路

    protected void showRealtimeTrack(String realtimeTrack)
    {
        if(refreshThread==null||!refreshThread.refresh)
        {
            return;
        }
        RealtimeTrackData realtimeTrackData=GsonService.parseJson(realtimeTrack,RealtimeTrackData.class);
        //数据以JSON形式存取
        if(realtimeTrackData!=null&& realtimeTrackData.getStatus()==0)
        {
            LatLng latLng=realtimeTrackData.getRealtimePoint();
            if(latLng != null)
            {
                pointList.add(latLng);
                drawRealtimePoint(latLng);
            } else
            {
                Toast.makeText(getApplicationContext(),"当前无轨迹点",Toast.LENGTH_LONG).show();
            }
        }
    }
    //展示实时线路

    private void drawRealtimePoint(LatLng point)
    {
        baiduMap.clear();
        MapStatus mapStatus=new MapStatus.Builder().target(point).zoom(18).build();
        msUpdate=MapStatusUpdateFactory.newMapStatus(mapStatus);
        realtimeBitmap=BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding);
        overlay=new MarkerOptions().position(point).icon(realtimeBitmap).zIndex(9).draggable(true);
        if(pointList.size()>= 2&&pointList.size()<=1000)
        {
            polyline = new PolylineOptions().width(10).color(Color.RED).points(pointList);
        }
        addMarker();
    }
    //画出实时线路点

    private void addMarker()
    {
        if(msUpdate!=null)
        {
            baiduMap.setMapStatus(msUpdate);
        }
        if(polyline!=null)
        {
            baiduMap.addOverlay(polyline);
        }
        if(overlay!=null)
        {
            baiduMap.addOverlay(overlay);
        }
    }
    //展示点

    private String getImei(Context context){
        String mImei="NULL";
        try
        {
            mImei=((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE)).getDeviceId();
        } catch (Exception e)
        {
            System.out.println("获取IMEI码失败");
            mImei="NULL";
        }
        return mImei;
    }

}