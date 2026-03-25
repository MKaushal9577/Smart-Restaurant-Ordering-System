package com.example.openend_project;

import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OrderActivity extends AppCompatActivity {

    EditText customerName;
    EditText specialInstructions;
    LinearLayout itemsContainer;
    TextView emptyItemsHint;
    TextView statusBadge;

    int tableNumber;
    OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order);

        tableNumber = getIntent().getIntExtra("tableNumber", 1);

        // Bind views
        customerName = findViewById(R.id.customerName);
        specialInstructions = findViewById(R.id.specialInstructions);
        itemsContainer = findViewById(R.id.itemsContainer);
        emptyItemsHint = findViewById(R.id.emptyItemsHint);
        statusBadge = findViewById(R.id.statusBadge);
        TextView headerTitle = findViewById(R.id.headerTableTitle);

        headerTitle.setText("Table " + tableNumber);

        // Mark table as locally occupied (server handles the official database side later)
        MainActivity.occupiedTables[tableNumber - 1] = true;
        updateStatusBadge(true);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.orderRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Back button
        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            // Free table locally if user cancels order
            MainActivity.occupiedTables[tableNumber - 1] = false;
            finish();
        });

        // Add item row
        Button addItemButton = findViewById(R.id.addItemButton);
        addItemButton.setOnClickListener(v -> {
            emptyItemsHint.setVisibility(View.GONE);
            addItemRow();
        });

        // Send order
        Button sendButton = findViewById(R.id.sendButton);
        sendButton.setOnClickListener(v -> sendOrder());
    }

    private void updateStatusBadge(boolean occupied) {
        if (occupied) {
            statusBadge.setText("OCCUPIED");
            statusBadge.setBackgroundResource(R.drawable.badge_occupied);
        } else {
            statusBadge.setText("FREE");
            statusBadge.setBackgroundResource(R.drawable.badge_free);
        }
    }

    private void addItemRow() {
        int dp = (int) getResources().getDisplayMetrics().density;

        LinearLayout itemRow = new LinearLayout(this);
        itemRow.setOrientation(LinearLayout.HORIZONTAL);
        itemRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, 8 * dp);
        itemRow.setLayoutParams(rowParams);

        // Item name input
        EditText itemName = new EditText(this);
        itemName.setHint("Item name");
        itemName.setTextColor(Color.WHITE);
        itemName.setHintTextColor(Color.parseColor("#4A5568"));
        itemName.setBackgroundResource(R.drawable.input_bg);
        itemName.setPadding(12 * dp, 10 * dp, 12 * dp, 10 * dp);
        itemName.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f));

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(8 * dp, 1));

        // Quantity input
        EditText quantity = new EditText(this);
        quantity.setHint("Qty");
        quantity.setTextColor(Color.WHITE);
        quantity.setHintTextColor(Color.parseColor("#4A5568"));
        quantity.setBackgroundResource(R.drawable.input_bg);
        quantity.setPadding(12 * dp, 10 * dp, 12 * dp, 10 * dp);
        quantity.setInputType(InputType.TYPE_CLASS_NUMBER);
        quantity.setGravity(Gravity.CENTER);
        quantity.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        View spacer2 = new View(this);
        spacer2.setLayoutParams(new LinearLayout.LayoutParams(8 * dp, 1));

        // Delete button
        Button deleteBtn = new Button(this);
        deleteBtn.setText("✕");
        deleteBtn.setTextColor(Color.parseColor("#E94560"));
        deleteBtn.setBackgroundColor(Color.TRANSPARENT);
        deleteBtn.setLayoutParams(new LinearLayout.LayoutParams(
                36 * dp, ViewGroup.LayoutParams.WRAP_CONTENT));

        deleteBtn.setOnClickListener(v -> {
            itemsContainer.removeView(itemRow);
            if (itemsContainer.getChildCount() == 0) {
                emptyItemsHint.setVisibility(View.VISIBLE);
            }
        });

        itemRow.addView(itemName);
        itemRow.addView(spacer);
        itemRow.addView(quantity);
        itemRow.addView(spacer2);
        itemRow.addView(deleteBtn);
        itemsContainer.addView(itemRow);
    }

    void sendOrder() {
        try {
            String customer = customerName.getText().toString().trim();
            String instructions = specialInstructions.getText().toString().trim();

            if (customer.isEmpty()) {
                Toast.makeText(this, "Please enter customer name", Toast.LENGTH_SHORT).show();
                return;
            }

            if (itemsContainer.getChildCount() == 0) {
                Toast.makeText(this, "Please add at least one item", Toast.LENGTH_SHORT).show();
                return;
            }

            JSONArray itemsArray = new JSONArray();
            for (int i = 0; i < itemsContainer.getChildCount(); i++) {
                LinearLayout row = (LinearLayout) itemsContainer.getChildAt(i);

                EditText itemName = (EditText) row.getChildAt(0);
                EditText qty = (EditText) row.getChildAt(2);

                String name = itemName.getText().toString().trim();
                String qtyStr = qty.getText().toString().trim();

                if (!name.isEmpty()) {
                    JSONObject item = new JSONObject();
                    item.put("name", name);
                    item.put("qty", qtyStr.isEmpty() ? "1" : qtyStr);
                    itemsArray.put(item);
                }
            }

            JSONObject order = new JSONObject();
            order.put("table", tableNumber);
            order.put("customer", customer);
            order.put("instructions", instructions);
            order.put("items", itemsArray);

            MediaType JSON = MediaType.get("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(order.toString(), JSON);

            // Use the centralized SERVER_URL from MainActivity
            Request request = new Request.Builder()
                    .url(MainActivity.SERVER_URL + "/order")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    e.printStackTrace();
                    runOnUiThread(() ->
                            Toast.makeText(OrderActivity.this,
                                    "Failed to send order. Check connection.",
                                    Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    runOnUiThread(() -> {
                        Toast.makeText(OrderActivity.this,
                                "✅ Order sent for Table " + tableNumber,
                                Toast.LENGTH_SHORT).show();
                        // Close activity. MainActivity's onResume() will trigger and sync the new status!
                        finish();
                    });
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error preparing order", Toast.LENGTH_SHORT).show();
        }
    }
}