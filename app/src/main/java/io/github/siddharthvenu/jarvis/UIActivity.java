package io.github.siddharthvenu.jarvis;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.jarvis.R;

import java.util.ArrayList;
import java.util.List;
import java.lang.Character;

public class UIActivity extends AppCompatActivity {

    public static final String LOG_TAG = "Project JARVIS";
    private TextView botResponseTextView, nammaInput;
    private ImageView jarvisLogo;
    public ProgressBar progress;
    private final int REQUEST_CODE = 1234;
    SharedPreferences preferences;
    String chat = "Hey there!";

    @SuppressLint("CommitPrefEdits")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ui);

        botResponseTextView = (TextView) findViewById(R.id.botResponse);
        botResponseTextView.setMovementMethod(new ScrollingMovementMethod());
        nammaInput = (TextView) findViewById(R.id.nammaInput);
        nammaInput.setMovementMethod(new ScrollingMovementMethod());
        jarvisLogo = (ImageView) findViewById(R.id.jarvisLogo);
        progress = (ProgressBar) findViewById(R.id.progressBar);

        /* Check if the device has a preference "uid" which each user has a unique one. If the
        device does not have it, create the preference and initialize with current milli time */
        preferences = getSharedPreferences("preference", MODE_PRIVATE);
        if (!(preferences.contains("uid"))) {
            long randomUID = System.currentTimeMillis();
            preferences.edit().putLong("uid", randomUID).commit();
        }

        /* Listen for input every time the ImageButton is clicked. If the device is not connected, then
         alert the user about it */
        ImageButton startListening = (ImageButton) findViewById(R.id.speech_button);
        startListening.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isConnected()) {
                    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    startActivityForResult(intent, REQUEST_CODE);
                } else {
                    Toast.makeText(getApplicationContext(), "Please check your Internet connection", Toast.LENGTH_LONG).show();
                }
            }
        });
        new NetworkAsyncTask().execute("Hey there!");
    }

    // Check if the device is connected to the internet
    private boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo net = cm.getActiveNetworkInfo();
        return net != null && net.isAvailable() && net.isConnected();
    }


    // Exit the app
    private void exit() {
        botResponseTextView.setText(R.string.exit_message);
        Handler mHandler = new Handler();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, 2000);
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            chat = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).get(0);
            nammaInput.setText(chat);
            //Log.v(LOG_TAG, chat);

            /* Check if the user gave a command for Jarvis to open an app, or to exit the app, or
            just a normal chat , and do the appropriate action*/
            if (isAppOpenCommand()) {
                botResponseTextView.setText(R.string.opening_app);
                new openApp().execute();
            } else if (isAppExitCommand()) {
                exit();
            } else new NetworkAsyncTask().execute(chat);

        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    private void showAlertDialog(final ArrayList<ApplicationInfo> appList) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose an application");
        builder.setItems(getAppNames(appList), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //Log.v(LOG_TAG, appList.get(which).packageName);
                startActivity(getPackageManager().getLaunchIntentForPackage(appList.get(which).packageName));
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(alertDialog.getWindow().getAttributes());
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        lp.height = (size.y) * 2 / 3;
        alertDialog.getWindow().setAttributes(lp);
    }

    private String[] getAppNames(List<ApplicationInfo> appList) {
        ArrayList<String> appNames = new ArrayList<>();
        for (ApplicationInfo appInfo : appList) {
            appNames.add(getPackageManager().getApplicationLabel(appInfo).toString());
        }
        return appNames.toArray(new String[appNames.size()]);
    }

    private ArrayList<ApplicationInfo> retrieveMatchedAppInfos(List<ApplicationInfo> appList, String requestedAppName) {
        ArrayList<ApplicationInfo> matchingAppNames = new ArrayList<>();
        for (ApplicationInfo appInfo : appList) {
            String appName = getPackageManager().getApplicationLabel(appInfo).toString();
            if (appName.toLowerCase().contains(requestedAppName.toLowerCase())) {
                matchingAppNames.add(appInfo);
            }
        }
        return matchingAppNames;
    }

    private boolean isAppOpenCommand() {
        return chat.toLowerCase().contains("open ");
    }

    private boolean isAppExitCommand() {
        String temp = chat.toLowerCase();
        return temp.contains("exit") || temp.contains("bye") || temp.contains("gotta go") || temp.contains("got to go");
    }

    private class NetworkAsyncTask extends AsyncTask<String, Void, String> {

        @Override
        protected void onPreExecute() {
            jarvisLogo.setVisibility(View.INVISIBLE);
            progress.setProgress(0);
            progress.setVisibility(View.VISIBLE);
            botResponseTextView.setVisibility(View.INVISIBLE);
        }

        @Override
        protected String doInBackground(String... talkWord) {
            if (talkWord == null || talkWord[0].isEmpty()) return null;
            NetworkParseResponse botChat = new NetworkParseResponse();

            botChat.appendProgressBar(progress);
            botChat.formURL(talkWord[0], preferences.getLong("uid", 0));
            botChat.establishConnectionGetResponse();

            return botChat.getBotResponse();
        }

        @Override
        protected void onPostExecute(String botResponse) {
            progress.setVisibility(View.INVISIBLE);
            jarvisLogo.setVisibility(View.VISIBLE);
            if (botResponse != null) {
                botResponseTextView.setText(botResponse);
            }
            botResponseTextView.setVisibility(View.VISIBLE);
        }
    }

    private class openApp extends AsyncTask<Void,Void,List<ApplicationInfo>> {

        @Override
        protected List<ApplicationInfo> doInBackground(Void[] params) {
            StringBuilder requestedAppName = new StringBuilder(chat.substring(chat.indexOf("open ") + 5));

            List<ApplicationInfo> appList = getPackageManager().getInstalledApplications(0), matchingAppsList;
            matchingAppsList = retrieveMatchedAppInfos(appList, requestedAppName.toString());

        /* If the matchingAppList size be 0, then check Apps with words at the end removed too.
        For example, if "WhatsApp Pro" yields 0 results, then try searching "WhatsApp" too! */
            while (matchingAppsList.size() == 0 && requestedAppName.length() > 0) {
                if (requestedAppName.toString().endsWith(" "))
                    requestedAppName.deleteCharAt(requestedAppName.lastIndexOf(" "));
                int index = requestedAppName.length() - 1;

                for (Character curChar = requestedAppName.charAt(index);
                     !curChar.equals(' ') && !curChar.toString().isEmpty() && index >= 0; ) {
                    curChar = requestedAppName.charAt(index);
                    requestedAppName.deleteCharAt(index);
                    index--;
                }
                //The above for loop removes the word at the end of the requested app name
                Log.v(LOG_TAG, requestedAppName.toString());
                matchingAppsList = retrieveMatchedAppInfos(appList, requestedAppName.toString());
            }
            return matchingAppsList;
        }

        @Override
        protected void onPostExecute(List<ApplicationInfo> matchingAppsList) {
            if (matchingAppsList.size() == 0)
                botResponseTextView.setText(R.string.app_not_found);
            else if (matchingAppsList.size() == 1) {
                botResponseTextView.setText(R.string.app_opened);
                startActivity(getPackageManager().getLaunchIntentForPackage(matchingAppsList.get(0).packageName));
            } else {
                botResponseTextView.setText(R.string.app_opened);
                showAlertDialog((ArrayList<ApplicationInfo>) matchingAppsList);
            }
        }
    }


}

