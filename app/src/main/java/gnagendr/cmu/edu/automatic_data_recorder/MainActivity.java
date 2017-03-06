package gnagendr.cmu.edu.automatic_data_recorder;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity extends AppCompatActivity {
    HashMap<String, LinkedList<String>> patientActivityMap,activityDetails;
    String dataResult;
    TextView welcomeMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final ProgressBar progressBar = (ProgressBar)findViewById(R.id.progressBar);
        progressBar.setVisibility(View.INVISIBLE);

        Button beginButton = (Button) findViewById(R.id.begin);
        beginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try{
                    String result = new RetrieveDataTask(welcomeMessage, progressBar).execute().get();
                    ArrayList<HashMap<String,LinkedList<String>>> output = processValue(result);
                    patientActivityMap = output.get(0);
                    activityDetails = output.get(1);
                    System.out.println(patientActivityMap.size());
                    Intent intent = new Intent(MainActivity.this, PatientIDList.class);
                    intent.putExtra("activityDetails",activityDetails);
                    intent.putExtra("patientActivityMap",patientActivityMap);
                    MainActivity.this.startActivity(intent);
                }
                catch(Exception e){
                    e.printStackTrace();
                }
            }
        });
    }

    public class RetrieveDataTask extends AsyncTask<String, Void, String> {
        private AlertDialog.Builder alertDialogBuilder;
        private AlertDialog alertDialog;
        private int resCode;
        private Context context;
        private HttpURLConnection httpURLConnection;
        private String result;
        private TextView textView;
        private ProgressBar progress;
        RetrieveDataTask(TextView textView, ProgressBar progressBar) {
            this.textView = textView;
            this.progress = progressBar;
        }
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progress.setVisibility(View.VISIBLE);
            alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
        }

        @Override
        protected String doInBackground(String... urls) {

            InputStream inputStream;
            Date currentDate = new Date(System.currentTimeMillis());
            CharSequence s  = DateFormat.format("MMddyyyy",currentDate.getTime());
            Log.e("show current date",s.toString());
            try{
                URL url = new URL("https://intense-mountain-41887.herokuapp.com/fetch/" + s.toString());
                httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.setAllowUserInteraction(false);
                httpURLConnection.setInstanceFollowRedirects(true);
                httpURLConnection.setRequestMethod("POST");
                httpURLConnection.setRequestProperty("Content-Type","application/json");
                httpURLConnection.connect(); //android.os.NetworkOnMainThreadException
                //waiting for server response
                resCode = httpURLConnection.getResponseCode();
                Log.e("in try", String.valueOf(resCode));
                System.out.println(HttpURLConnection.HTTP_OK);
                if(resCode == HttpURLConnection.HTTP_OK) {
                    inputStream = httpURLConnection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line=reader.readLine())!=null){
                        sb.append(line).append("\n");
                    }
                    inputStream.close();
                    result = sb.toString();
                }
            }catch (Exception e){
                e.printStackTrace();
                result = e.getMessage();
            }
            return result;
        }

        // method does not call
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            if (resCode == HttpURLConnection.HTTP_NO_CONTENT) {
                String msg = null;
                try {
                    msg = httpURLConnection.getResponseMessage();
                    alertDialogBuilder.setTitle("No matched data");
                    alertDialogBuilder.setMessage(msg)
                            .setNegativeButton("OK",new DialogInterface.OnClickListener(){
                                public void onClick(DialogInterface dialog, int id){
                                    dialog.cancel();
                                }
                            });
                } catch (IOException e) {
                    e.printStackTrace();
                    alertDialogBuilder.setTitle("No matched data error");
                    alertDialogBuilder.setMessage("Cannot find response message")
                            .setNegativeButton("OK",new DialogInterface.OnClickListener(){
                                public void onClick(DialogInterface dialog, int id){
                                    dialog.cancel();
                                }
                            });
                    Log.e("in catch","line 210");
                }
            } else if (resCode != HttpURLConnection.HTTP_OK) {
                alertDialogBuilder.setTitle("Database import error!");
                alertDialogBuilder.setMessage("Cannot connect database. Pls check the setttings.")
                        .setNegativeButton("OK",new DialogInterface.OnClickListener(){
                            public void onClick(DialogInterface dialog, int id){
                                dialog.cancel();
                            }
                        });
                Log.e("in else if", "line 220");
            }
            progress.setVisibility(View.GONE);
        }
    }

    public class ActivityOrderObject {
        private int order_num;
        private String activityId;
        private String patientId;

        ActivityOrderObject() {}
        ActivityOrderObject(String aid, int num, String pid) {
            this.order_num = num;
            this.activityId = aid;
            this.patientId = pid;
        }

        public void setId(String aid) {
            this.activityId = aid;
        }

        public String getId () {
            return this.activityId;
        }

        public void setOrder (int number) {
            this.order_num = number;
        }

        public int getOrder() {
            return this.order_num;
        }

        public void setPatientId (String pid) {
            this.patientId = pid;
        }

        public String getPatientId () {
            return this.patientId;
        }
    }

    public ArrayList<HashMap<String,LinkedList<String>>> processValue (String result) {
        ArrayList<ActivityOrderObject> activityOrderList = new ArrayList<>();

        String resultCopy = result;
        while(resultCopy.length() >= 0) {
            int start, mid, order, end, tale;
            start = resultCopy.indexOf("AID");
            mid = resultCopy.indexOf("PID");
            order = resultCopy.indexOf("Order_Num");
            end = resultCopy.indexOf("---");
            tale = resultCopy.indexOf("--@@");
            if (start >= 0 && mid >= 0 && end >= 0 && order >= 0 && tale >= 0) {
                String aid = resultCopy.substring(start + "AID: ".length(), mid).trim();
                String pid = resultCopy.substring(mid + "PID: ".length(), order).trim();
                String orderNum = resultCopy.substring(order + "Order_Num: ".length(), end).trim();
                Log.e("aid in processValue", aid);
                Log.e("pid in pv", pid);
                Log.e("show orderNum", orderNum);
                int number = Integer.valueOf(orderNum.trim());
                ActivityOrderObject activityOrderObject = new ActivityOrderObject(aid, number, pid);
                activityOrderList.add(activityOrderObject);
            } else {
                break;
            }
            resultCopy = resultCopy.substring(tale + 1);
        }

        Collections.sort(activityOrderList, new Comparator<ActivityOrderObject>() {
            public int compare(ActivityOrderObject o1, ActivityOrderObject o2) {
                return Integer.valueOf(o1.getOrder()).compareTo(o2.getOrder());
            }
        });

        patientActivityMap = new HashMap<>();

        Iterator activityListIterator = activityOrderList.iterator();
        while(activityListIterator.hasNext()){
            ActivityOrderObject obj = (ActivityOrderObject)activityListIterator.next();
            String aid = obj.getId();
            String pid = obj.getPatientId();
            if (!patientActivityMap.containsKey(pid)) {
                LinkedList<String> aList = new LinkedList<>();
                aList.offerFirst(aid);
                patientActivityMap.put(pid, aList);
            } else {
                LinkedList<String> aList = patientActivityMap.get(pid);
                aList.offer(aid);
                patientActivityMap.put(pid, aList);
            }
        }

        String tem = result;

        tem = result;
        HashMap<String,LinkedList<String>> activityDetails = new HashMap<String,LinkedList<String>>();
        int activityIDIndex = tem.indexOf("activity_id");
        int activityNameIndex = tem.indexOf("activity_name");
        int categoryIndex = tem.indexOf("category");
        while (activityIDIndex >= 0) {
            String[] splitActivityID = tem.substring(activityIDIndex,tem.length()).split("\"");
            System.out.println(splitActivityID[2]);
            String ID = splitActivityID[2];
            System.out.println("Id" + ID);
            System.out.println(tem.substring(activityNameIndex,tem.length()));
            String[] splitActivityName = tem.substring(activityNameIndex,tem.length()).split("\"");
            System.out.println(activityNameIndex);

            String name = splitActivityName[2];
            System.out.println("name " + name);
            String[] splitActivityCategory = tem.substring(categoryIndex,tem.length()).split("\"");
            String category = splitActivityCategory[2];
            System.out.println("category " +  category);
            LinkedList<String> details = new LinkedList<>();
            details.add(name);
            details.add(category);
            activityDetails.put(ID,details);
            activityIDIndex = tem.indexOf("activity_id", activityIDIndex + "activity_id".length());
            activityNameIndex = tem.indexOf("activity_name", activityNameIndex + "activity_name".length());
            categoryIndex = tem.indexOf("category", categoryIndex + "category".length());
        }

        for (Map.Entry<String, LinkedList<String>>entry: patientActivityMap.entrySet()){
            String key = entry.getKey();
            Log.e("key", key);
            LinkedList<String> value = entry.getValue();
            Log.e("value in line 204", value.toString());
        }
        int resultTag = result.lastIndexOf("@@");
        dataResult = result.substring(resultTag+2);
        Log.e("dataResult@208", dataResult);

        ArrayList<HashMap<String,LinkedList<String>>> output = new ArrayList<>();
        output.add(patientActivityMap);
        output.add(activityDetails);

        return output;


    }

    public Map sortByValue (Map<String, ActivityOrderObject> map){
        Map<String, ActivityOrderObject> temMap = new TreeMap(new ValueComparator(map));
        temMap.putAll(map);
        return temMap;
    }
}
