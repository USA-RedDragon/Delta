package com.mcswainsoftware.desolateddelta;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.*;
import javax.net.ssl.*;
import java.net.*;
import java.util.*;
import org.json.*;
import android.app.*;
import android.content.*;

public class UpdateCheckService extends Service {

	String version, device, deltaResponse;
	boolean notifying=false;
	long notifyTime=0;
	
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
		version = Root.runCommand("getprop ro.deso.version");
        device = Root.runCommand("getprop ro.deso.device");
		
		Thread alwaysThread = new Thread(new AlwaysRun());
		alwaysThread.start();
		
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
	
	public class AlwaysRun implements Runnable
	{
		@Override
		public void run()
		{
			while (true) {
				if((System.currentTimeMillis() - notifyTime) > 1000 * 60 * 60 * 4 && !notifying) new HttpCheckTask().execute("https://desolationrom.com/ota/delta/ota/"+device+"/"+version+".zip.delta");
				try { Thread.sleep(1000*60*5); } catch(Exception x) { x.printStackTrace(); }
			}
		}
	}
	
	private class HttpCheckTask extends AsyncTask<String, Void, Integer> {
        @Override
        protected Integer doInBackground(String... strings) {
            HttpsURLConnection conn = null;
            int response=0;
            try {
                URL url = new URL(strings[0]);
                conn = (HttpsURLConnection) url.openConnection();
                conn.setUseCaches(false);
                conn.connect();
                response = conn.getResponseCode();
                Scanner s = new Scanner(conn.getInputStream()).useDelimiter("\\A");
                deltaResponse = (s.hasNext() ? s.next() : "");
            } catch (Exception x) {
                x.printStackTrace();
            }
            conn.disconnect();
            return response;
        }

        @Override
        protected void onPostExecute(Integer code) {
            super.onPostExecute(code);
            if(code == 404) {
                return;
            } else {
				try {
					JSONObject jsonResponse = new JSONObject(deltaResponse);
               		if(getSharedPreferences("prefs", 0).getBoolean("dev", false)) {
						String version = jsonResponse.getJSONObject("out").getString("name").replaceAll("^DesolationRom-"+device+"-", "").replaceAll("-[0-9]{8}-[0-9]{4}.zip$", "");
						String date = jsonResponse.getJSONObject("out").getString("name").substring(jsonResponse.getJSONObject("out").getString("name").length()-17).replaceAll(".zip", "");
						
						Notification.Builder mBuilder =
							new Notification.Builder(UpdateCheckService.this)
							.setSmallIcon(R.mipmap.ic_launcher)
							.setContentTitle("New" + ((jsonResponse.getJSONObject("update").getString("dev").contains("dev")) ? " Development":"") + " Build")
							.setContentText(version + "-" + date);
						Intent resultIntent = new Intent(UpdateCheckService.this, MainActivity.class);
						Bundle bun = new Bundle();
						bun.putString("json", jsonResponse.toString());
						resultIntent.putExtras(bun);
						TaskStackBuilder stackBuilder = TaskStackBuilder.create(UpdateCheckService.this);
						stackBuilder.addParentStack(MainActivity.class);
						stackBuilder.addNextIntent(resultIntent);
						PendingIntent resultPendingIntent =
							stackBuilder.getPendingIntent(
							0,
							PendingIntent.FLAG_UPDATE_CURRENT
						);
						mBuilder.setContentIntent(resultPendingIntent);
						NotificationManager mNotificationManager =
							(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
							if(mNotificationManager.getActiveNotifications().length == 0) {
								notifying=true;
								notifyTime = System.currentTimeMillis();
								mNotificationManager.notify(9876, mBuilder.build());
							}
                	} else {
                    	try {
							String version = jsonResponse.getJSONObject("out").getString("name").replaceAll("^DesolationRom-"+device+"-", "").replaceAll("-[0-9]{8}-[0-9]{4}.zip$", "");
							String date = jsonResponse.getJSONObject("out").getString("name").substring(jsonResponse.getJSONObject("out").getString("name").length()-17).replaceAll(".zip", "");
							
	                        Notification.Builder mBuilder =
									new Notification.Builder(UpdateCheckService.this)
									.setSmallIcon(R.mipmap.ic_launcher)
									.setContentTitle("New Build")
									.setContentText(version + "-" + date);
								Intent resultIntent = new Intent(UpdateCheckService.this, MainActivity.class);
								Bundle bun = new Bundle();
								bun.putString("json", jsonResponse.toString());
								resultIntent.putExtras(bun);
								TaskStackBuilder stackBuilder = TaskStackBuilder.create(UpdateCheckService.this);
								stackBuilder.addParentStack(MainActivity.class);
								stackBuilder.addNextIntent(resultIntent);
								PendingIntent resultPendingIntent =
									stackBuilder.getPendingIntent(
									0,
									PendingIntent.FLAG_UPDATE_CURRENT
								);
								mBuilder.setContentIntent(resultPendingIntent);
								NotificationManager mNotificationManager =
									(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
								if(mNotificationManager.getActiveNotifications().length == 0) {
									notifying=true;
									notifyTime = System.currentTimeMillis();
									mNotificationManager.notify(9876, mBuilder.build());
								}
                    
                    	} catch (Exception x) {
                        	x.printStackTrace();
                    	}
                	}
				} catch(Exception x) { x.printStackTrace(); }
            }
        }
    }
}
