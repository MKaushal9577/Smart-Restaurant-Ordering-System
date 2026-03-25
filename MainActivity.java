package com.example.openend_project;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    // 🔴 CENTRALIZED SERVER URL: Change this here if your PC's local IP changes!
    public static final String SERVER_URL = "http://10.2.104.84:5000";

    public static boolean[] occupiedTables = new boolean[12];
    private static final int TABLE_COUNT = 12;

    private OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Draw initial grid (will use default/cached values until network call finishes)
        buildTableGrid();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Sync with the server every time we return to this screen
        fetchTableStatus();
    }

    private void fetchTableStatus() {
        Request request = new Request.Builder()
                .url(SERVER_URL + "/tables")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to sync tables with server", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String responseData = response.body().string();
                    try {
                        JSONObject jsonTables = new JSONObject(responseData);

                        // Update our local array with the server's single source of truth
                        for (int i = 0; i < TABLE_COUNT; i++) {
                            String tableKey = String.valueOf(i + 1);
                            if (jsonTables.has(tableKey)) {
                                occupiedTables[i] = jsonTables.getBoolean(tableKey);
                            }
                        }

                        // Rebuild the UI on the main thread
                        runOnUiThread(() -> buildTableGrid());

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void buildTableGrid() {
        GridLayout grid = findViewById(R.id.tableGrid);
        if (grid == null) return;

        grid.removeAllViews();

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int padding = (int) (32 * getResources().getDisplayMetrics().density);
        int gap = (int) (12 * getResources().getDisplayMetrics().density);
        int cardSize = (screenWidth - padding - gap * 2) / 3;

        for (int i = 0; i < TABLE_COUNT; i++) {
            final int tableNum = i + 1;
            boolean isOccupied = occupiedTables[i];

            // Card container
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setBackgroundResource(isOccupied ? R.drawable.table_card_occupied : R.drawable.table_card_free);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cardSize;
            params.height = cardSize;
            params.setMargins(6, 6, 6, 6);
            card.setLayoutParams(params);

            // Table emoji icon
            TextView icon = new TextView(this);
            icon.setText("🪑");
            icon.setTextSize(28f);
            icon.setGravity(Gravity.CENTER);

            // Table number label
            TextView label = new TextView(this);
            label.setText("Table " + tableNum);
            label.setTextSize(13f);
            label.setTextColor(Color.WHITE);
            label.setTypeface(null, android.graphics.Typeface.BOLD);
            label.setGravity(Gravity.CENTER);

            // Status text
            TextView status = new TextView(this);
            status.setText(isOccupied ? "Occupied" : "Free");
            status.setTextSize(11f);
            status.setTextColor(isOccupied ? Color.parseColor("#E94560") : Color.parseColor("#38A169"));
            status.setGravity(Gravity.CENTER);

            card.addView(icon);
            card.addView(label);
            card.addView(status);

            if (isOccupied) {
                card.setAlpha(0.55f);

                // Short tap = info toast
                card.setOnClickListener(v ->
                        Toast.makeText(this, "Table " + tableNum + " is occupied. Long press to free it.", Toast.LENGTH_SHORT).show()
                );

                // Long press = SEND NETWORK REQUEST TO FREE TABLE
                card.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Free Table " + tableNum + "?")
                            .setMessage("Mark this table as available?")
                            .setPositiveButton("Yes, Free It", (dialog, which) -> {

                                // Tell the server to free this table
                                RequestBody emptyBody = RequestBody.create(new byte[0], null);
                                Request request = new Request.Builder()
                                        .url(SERVER_URL + "/tables/" + tableNum + "/free")
                                        .post(emptyBody)
                                        .build();

                                client.newCall(request).enqueue(new Callback() {
                                    @Override
                                    public void onFailure(Call call, IOException e) {
                                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Network Error", Toast.LENGTH_SHORT).show());
                                    }

                                    @Override
                                    public void onResponse(Call call, Response response) {
                                        runOnUiThread(() -> {
                                            Toast.makeText(MainActivity.this, "Table " + tableNum + " is now free ✅", Toast.LENGTH_SHORT).show();
                                            fetchTableStatus(); // Immediately refresh data
                                        });
                                    }
                                });
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return true;
                });
            } else {
                // Free table behavior
                card.setAlpha(1f);
                card.setOnClickListener(v -> {
                    Intent intent = new Intent(MainActivity.this, OrderActivity.class);
                    intent.putExtra("tableNumber", tableNum);
                    startActivity(intent);
                });
            }

            grid.addView(card);
        }
    }
}