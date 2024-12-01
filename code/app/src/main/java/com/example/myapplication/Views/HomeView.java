package com.example.myapplication.Views;

import android.os.Bundle;
import android.util.Log;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.BaseActivity;
import com.example.myapplication.Controllers.HomePageController;
import com.example.myapplication.EventAdapter;
import com.example.myapplication.Models.Event;
import com.example.myapplication.Models.Facility;
import com.example.myapplication.R;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class HomeView extends BaseActivity {
    // Aditi
    private ListView selectedEventsListView;
    private ListView waitlistEventsListView;

    private HomePageController selectedEventsAdapter;
    private HomePageController waitlistEventsAdapter;

    private List<Event> selectedEvents;
    private List<Event> waitlistEvents;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the layout for this activity
        setContentView(R.layout.activity_home_page);


        selectedEventsListView = findViewById(R.id.selected_events_list);
        waitlistEventsListView = findViewById(R.id.entrant_waitlist);

        selectedEvents = new ArrayList<>();

        selectedEventsAdapter = new HomePageController(this, selectedEvents, true);

        selectedEventsListView.setAdapter(selectedEventsAdapter);

        fetchWaitlistEvents();

    }

    // Confirm an event from the waitlist to the selected list
    public void confirmEvent(Event event) {
        selectedEvents.add(event);
        selectedEventsAdapter.notifyDataSetChanged();

        Toast.makeText(this, "Event Confirmed", Toast.LENGTH_SHORT).show();
    }

    // Delete an event from either list
    public void deleteEvent(Event event) {

        Toast.makeText(this, "Event Deleted", Toast.LENGTH_SHORT).show();
    }

    private void fetchWaitlistEvents() {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        // String eventId = eventDoc.getId(); //GET EVENT ID
//        db.collection("Events")
//                .document(eventId)
//                .collection("waitlist")
//                .whereEqualTo("status", "not chosen")
//                .get()
//                .addOnSuccessListener(querySnapshot -> {
//                    waitlistEvents.clear(); // Clear the current list to avoid duplicates
//                    for (QueryDocumentSnapshot document : querySnapshot) {
//                        Event event = document.toObject(Event.class);
//                        waitlistEvents.add(event);
//                    }
//                    waitlistEventsAdapter.notifyDataSetChanged(); // Update the ListView
//                })
//                .addOnFailureListener(e -> {
//                    Log.e("HomeView", "Error fetching waitlist events: ", e);
//                    Toast.makeText(this, "Failed to load waitlist events.", Toast.LENGTH_SHORT).show();
//                });
    }
}
