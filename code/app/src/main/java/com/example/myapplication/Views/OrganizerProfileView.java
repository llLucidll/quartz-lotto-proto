package com.example.myapplication.Views;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.icu.util.Calendar;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.myapplication.AvatarUtil;
import com.example.myapplication.BaseActivity;
import com.example.myapplication.Controllers.EditProfileController;
import com.example.myapplication.Models.User;
import com.example.myapplication.OrganizerNotificationActivity;
import com.example.myapplication.R;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class OrganizerProfileView extends BaseActivity {

    private static final String TAG = "OrganizerProfileView";

    private CircleImageView profileImageView;
    private ImageButton editProfileImageButton, backButton, removeProfileImageButton;
    private EditText nameField, emailField, dobField, phoneField;
    private Spinner countrySpinner;
    private Button saveChangesButton, manageFacilityButton, myEvents, notifGroups;
    private Switch notificationSwitch;

    private Uri imageUri;
    private boolean isAdmin = false;
    private boolean isOrganizer = true; // Default to true for organizers
    private boolean notificationsPerm = false;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    imageUri = result.getData().getData();
                    Glide.with(this)
                            .load(imageUri)
                            .apply(RequestOptions.circleCropTransform())
                            .into(profileImageView);
                    removeProfileImageButton.setVisibility(View.VISIBLE);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_organizer_profile);

        initializeUI();
        setListeners();
        loadUserProfile();
    }

    private void initializeUI() {
        profileImageView = findViewById(R.id.profile_image);
        editProfileImageButton = findViewById(R.id.edit_profile_image_button);
        backButton = findViewById(R.id.back_button);
        nameField = findViewById(R.id.name_field);
        emailField = findViewById(R.id.email_field);
        dobField = findViewById(R.id.dob_field);
        phoneField = findViewById(R.id.phone_field);
        countrySpinner = findViewById(R.id.country_spinner);
        saveChangesButton = findViewById(R.id.save_changes_button);
        removeProfileImageButton = findViewById(R.id.remove_profile_image_button);
        manageFacilityButton = findViewById(R.id.manage_facility_button);
        myEvents = findViewById(R.id.my_events_button);
        notifGroups = findViewById(R.id.notif_groups);
        notificationSwitch = findViewById(R.id.notifications_switch); // Assuming there is a switch for notifications

        dobField.setInputType(InputType.TYPE_CLASS_DATETIME);
        dobField.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                showDatePicker();
                return true;
            }
            return false;
        });

        nameField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No action needed
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Generate avatar as user types their name
                generateDefaultAvatar(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
                // No action needed
            }
        });

    }

    private void setListeners() {
        editProfileImageButton.setOnClickListener(v -> openFileChooser());
        saveChangesButton.setOnClickListener(v -> saveProfileData());
        backButton.setOnClickListener(v -> finish());
        removeProfileImageButton.setOnClickListener(v -> deleteProfileImage());
        manageFacilityButton.setOnClickListener(v -> addFacility());
        myEvents.setOnClickListener(v -> startActivity(new Intent(this, HomeView.class)));
        notifGroups.setOnClickListener(v -> startActivity(new Intent(this, OrganizerNotificationActivity.class)));
        notificationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> notificationsPerm = isChecked);
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void loadUserProfile() {
        String deviceId = retrieveDeviceId();
        if (deviceId == null || deviceId.isEmpty()) {
            Toast.makeText(this, "Device ID not found. Please log in again.", Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference userRef = db.collection("users").document(deviceId);
        userRef.get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            populateUIWithUserData(user);
                        }
                    } else {
                        initializeDefaultFields();
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to load profile.", e));
    }

    private void populateUIWithUserData(User user) {
        nameField.setText(user.getName());
        emailField.setText(user.getEmail());
        dobField.setText(user.getDob());
        phoneField.setText(user.getPhone());
        isAdmin = user.isAdmin();
        isOrganizer = user.isOrganizer();
        notificationsPerm = user.isNotificationsPerm();

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.country_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        countrySpinner.setAdapter(adapter);
        countrySpinner.setSelection(adapter.getPosition(user.getCountry()));

        notificationSwitch.setChecked(notificationsPerm);

        if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
            // Load the profile image from URL
            Glide.with(this)
                    .load(user.getProfileImageUrl())
                    .apply(RequestOptions.circleCropTransform())
                    .into(profileImageView);
            removeProfileImageButton.setVisibility(View.VISIBLE);
        } else {
            // Generate an avatar based on the first letter of the user's name
            generateDefaultAvatar(user.getName());
        }
    }


    private void initializeDefaultFields() {
        nameField.setText("");
        emailField.setText("");
        dobField.setText("");
        phoneField.setText("");
        isAdmin = false;
        isOrganizer = true;
        notificationsPerm = false;
        notificationSwitch.setChecked(false);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.country_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        countrySpinner.setAdapter(adapter);

        // Generate a default avatar
        generateDefaultAvatar(null);
    }

    private void saveProfileData() {
        String name = nameField.getText().toString().trim();
        String email = emailField.getText().toString().trim();
        String dob = dobField.getText().toString().trim();
        String phone = phoneField.getText().toString().trim();
        String country = countrySpinner.getSelectedItem().toString();

        if (name.isEmpty() || email.isEmpty() || dob.isEmpty() || country.isEmpty()) {
            Toast.makeText(this, "Please fill all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Invalid email format.", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> profileData = new HashMap<>();
        profileData.put("name", name);
        profileData.put("email", email);
        profileData.put("dob", dob);
        profileData.put("phone", phone);
        profileData.put("country", country);
        profileData.put("isAdmin", isAdmin);
        profileData.put("isOrganizer", isOrganizer);
        profileData.put("notificationsPerm", notificationsPerm);
        profileData.put("events", null);
        profileData.put("eventsAttending", new HashMap<String, Object>());
        profileData.put("profileImageUrl", null);

        String deviceId = retrieveDeviceId();
        if (deviceId == null) {
            Toast.makeText(this, "Device ID not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference userRef = db.collection("users").document(deviceId);
        userRef.set(profileData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Profile updated successfully.", Toast.LENGTH_SHORT).show();
                    if (imageUri != null) uploadProfileImage(userRef);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update profile.", e));
    }

    private void uploadProfileImage(DocumentReference userRef) {
        StorageReference storageRef = FirebaseStorage.getInstance().getReference("profile_images/" + retrieveDeviceId() + ".jpg");
        storageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    userRef.update("profileImageUrl", uri.toString())
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "Profile image updated successfully."));
                }))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to upload profile image.", e));
    }

    private void deleteProfileImage() {
        DocumentReference userRef = db.collection("users").document(retrieveDeviceId());
        userRef.update("profileImageUrl", null)
                .addOnSuccessListener(aVoid -> {
                    profileImageView.setImageResource(R.drawable.ic_profile);
                    removeProfileImageButton.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to delete profile image.", e));
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year1, month1, dayOfMonth) -> {
            String date = String.format(Locale.US, "%02d/%02d/%04d", month1 + 1, dayOfMonth, year1);
            dobField.setText(date);
        }, year, month, day);

        datePickerDialog.show();
    }

    private void addFacility() {
        Intent intent = new Intent(this, AddFacilityView.class);
        startActivity(intent);
    }

    private void generateDefaultAvatar(String name) {
        String firstLetter = (name != null && !name.isEmpty()) ? name.substring(0, 1).toUpperCase(Locale.US) : "?";
        Bitmap avatar = AvatarUtil.generateAvatar(firstLetter, 200, this);
        profileImageView.setImageBitmap(avatar);
        removeProfileImageButton.setVisibility(View.GONE);
    }

}
