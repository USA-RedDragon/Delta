package com.mcswainsoftware.desolateddelta;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Scanner;

import javax.net.ssl.HttpsURLConnection;
import android.app.*;
import android.content.*;
import android.widget.*;

public class MainActivity extends AppCompatActivity {

    boolean enrolled=false;
    String device, version, deltaResponse;
    ProgressBar progressBar;
    TextView statusText;
    Button checkButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent updateChecker = new Intent(this, UpdateCheckService.class);
		if(getIntent().getExtras() == null) startService(updateChecker);
        Root.requestRootAccess();

        if(!Root.isInPath("xdelta3")) {
            try {
                InputStream myInput = getAssets().open("xdelta3");

                String outFileName = getFilesDir() + "/xdelta3";

                OutputStream myOutput = new FileOutputStream(outFileName);

                byte[] buffer = new byte[1024];
                int length;
                while ((length = myInput.read(buffer)) > 0) {
                    myOutput.write(buffer, 0, length);
                }

                myOutput.flush();
                myOutput.close();
                myInput.close();

                Root.mount(true, "/system");
                Root.runCommand("cp " + outFileName + " /sdcard/bin/xdelta3");
                Root.runCommand("chmod 655 /sdcard/bin/xdelta3");

            } catch (Exception x) {
                x.printStackTrace();
            }
        }

        setContentView(R.layout.activity_main);

        version = Root.runCommand("getprop ro.deso.otaversion");
        device = Root.runCommand("getprop ro.deso.device");

        statusText = (TextView) findViewById(R.id.status);
        checkButton = (Button) findViewById(R.id.checkbutton);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        checkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                progressBar.setIndeterminate(true);
                checkButton.setText(getString(R.string.checking));
                checkButton.setEnabled(false);
                new HttpCheckTask().execute("https://desolationrom.com/ota/delta/ota/"+device+"/"+version+".zip.delta");
            }
        });

        SharedPreferences prefs = getSharedPreferences("prefs", 0);
        if(prefs.getBoolean("dev", false)) {
			enrolled=true;
		}
		String json = "";
		
		if(getIntent().getExtras() != null)
			 json = getIntent().getExtras().getString("json", "");
		
		if(json != null && !json.isEmpty()) {
			try {
				JSONObject jobj = new JSONObject(json);
				String version = jobj.getJSONObject("out").getString("name").replaceAll("^DesolationRom-"+device+"-", "").replaceAll("-[0-9]{8}-[0-9]{4}.zip$", "");
				String date = jobj.getJSONObject("out").getString("name").substring(jobj.getJSONObject("out").getString("name").length()-17).replaceAll(".zip", "");
				statusText.setText("Update Found: " + version + "\n"+ "Timestamp: " + date + "\nSize: " + (jobj.getJSONObject("update").getLong("size")/1024/1024) + " mb");
				
			} catch(Exception x) {
				x.printStackTrace();
			}
		}

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
		menu.getItem(0).setChecked(enrolled);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.enroll_in_beta:
				item.setChecked(!(item.isChecked()));
                SharedPreferences prefs = getSharedPreferences("prefs", 0);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("dev", item.isChecked());
				editor.apply();
                enrolled = item.isChecked();
                return true;
            default:
                return super.onOptionsItemSelected(item);
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
                statusText.setText(getText(R.string.no_update_found));
            } else {
                if(getSharedPreferences("prefs", 0).getBoolean("dev", false)) {
                   try {
						JSONObject jobj = new JSONObject(deltaResponse);
						String version = jobj.getJSONObject("out").getString("name").replaceAll("^DesolationRom-"+device+"-", "").replaceAll("-[0-9]{8}-[0-9]{4}.zip$", "");
						String date = jobj.getJSONObject("out").getString("name").substring(jobj.getJSONObject("out").getString("name").length()-17).replaceAll(".zip", "");
						statusText.setText("Update Found: " + version + "\n"+ "Timestamp: " + date + "\nSize: " + (jobj.getJSONObject("update").getLong("size")/1024/1024) + " mb");

					} catch(Exception x) {
						x.printStackTrace();
					}
					AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
					builder.setTitle(R.string.update_found).setCancelable(false).setPositiveButton("Download", new AlertDialog.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							statusText.setText("Downloading");
							
						}
					}).setNegativeButton("Wait", new AlertDialog.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								statusText.setText("Not Downloading");
							}
					}).show();
                } else {
                    try {
                        JSONObject jsonResponse = new JSONObject(deltaResponse);
                        if(jsonResponse.getJSONObject("update").getString("dev").contains("release")) {
                            statusText.setText(getText(R.string.update_found));
                        } else {
                            statusText.setText(getText(R.string.no_update_found));
                        }
                    } catch (Exception x) {
                        x.printStackTrace();
                    }
                }
            }
            progressBar.setIndeterminate(false);
            progressBar.setMax(100);
            progressBar.setProgress(0);
            checkButton.setText(getString(R.string.check_prompt));
            checkButton.setEnabled(true);
        }
    }

    private class HttpDownloadTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... strings) {
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
        }
    }
}
