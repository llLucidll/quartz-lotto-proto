package com.example.myapplication;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * Activity for editing user profile information, including name, email, date of birth,
 * phone number, country, profile image, and notification preferences.
 */
public class EditProfileActivity extends BaseActivity {

    private static final int MIN_AGE = 1;
    private static final int MAX_AGE = 100;

    private ImageButton removeProfileImageButton;
    private CircleImageView profileImageView;
    private ImageButton editProfileImageButton, backButton;
    private EditText nameField, emailField, dobField, phoneField;
    private Spinner countrySpinner;
    private Switch notificationsSwitch;
    private Button saveChangesButton;

    private Uri imageUri;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private String userId;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    imageUri = result.getData().getData();
                    Glide.with(this)
                            .load(imageUri)
                            .apply(RequestOptions.circleCropTransform())
                            .into(profileImageView);
                }
                removeProfileImageButton.setVisibility(View.VISIBLE);
            });

    /**
     * Initializes the activity, sets up UI elements, Firebase, and event listeners.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down, this contains the data it most recently supplied.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_entrant_profile);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();


        // Initialize userId using BaseActivity
        userId = getUserId();
        if (userId == null) {
            Toast.makeText(this, "User not authenticated.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize UI elements
        profileImageView = findViewById(R.id.profile_image);
        editProfileImageButton = findViewById(R.id.edit_profile_image_button);
        backButton = findViewById(R.id.back_button);
        nameField = findViewById(R.id.name_field);
        emailField = findViewById(R.id.email_field);
        dobField = findViewById(R.id.dob_field);
        phoneField = findViewById(R.id.phone_field);
        countrySpinner = findViewById(R.id.country_spinner);
        notificationsSwitch = findViewById(R.id.notifications_switch);
        saveChangesButton = findViewById(R.id.save_changes_button);
        removeProfileImageButton = findViewById(R.id.remove_profile_image_button);

        // Set listeners
        editProfileImageButton.setOnClickListener(v -> openFileChooser());
        saveChangesButton.setOnClickListener(v -> saveProfileData());
        backButton.setOnClickListener(v -> finish());
        removeProfileImageButton.setOnClickListener(v -> removeProfileImage());

        setupDateOfBirthField();

        // Update profile image based on name changes
        nameField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                DocumentReference userProfileRef = db.collection("users").document(userId);
                userProfileRef.get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                String profileImageUrl = documentSnapshot.getString("profileImageUrl");
                                if (profileImageUrl == null || profileImageUrl.isEmpty()) {
                                    String name = nameField.getText() != null ? nameField.getText().toString().trim() : "";
                                    if (!name.isEmpty()) {
                                        String firstLetter = String.valueOf(name.charAt(0)).toUpperCase(Locale.US);
                                        Bitmap avatarBitmap = AvatarUtil.generateAvatar(firstLetter, 200, EditProfileActivity.this);
                                        profileImageView.setImageBitmap(avatarBitmap);
                                    } else {
                                        profileImageView.setImageResource(R.drawable.ic_profile);
                                    }
                                }
                            }
                        })
                        .addOnFailureListener(Throwable::printStackTrace);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        loadUserProfile();
    }

    /**
     * Removes the profile image by deleting it from Firebase Storage and updating Firestore.
     */
    private void removeProfileImage() {
        StorageReference storageRef = storage.getReference("profile_images/" + userId + ".jpg");

        storageRef.delete()
                .addOnSuccessListener(aVoid -> {
                    DocumentReference userProfileRef = db.collection("users").document(userId);
                    userProfileRef.update("profileImageUrl", FieldValue.delete())
                            .addOnSuccessListener(aVoid1 -> {
                                Toast.makeText(this, "Profile image removed", Toast.LENGTH_SHORT).show();
                                loadProfileImage();
                            })
                            .addOnFailureListener(e -> {
                                e.printStackTrace();
                                Toast.makeText(this, "Failed to remove profile image URL", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                    Toast.makeText(this, "Failed to delete profile image", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Opens the file chooser for selecting a profile image.
     */
    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    /**
     * Sets up the date of birth field, including a calendar picker and format validation.
     */
    private void setupDateOfBirthField() {
        dobField.setInputType(InputType.TYPE_CLASS_NUMBER);
        dobField.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_calendar, 0);
        dobField.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (event.getRawX() >= (dobField.getRight() - dobField.getCompoundDrawables()[2].getBounds().width())) {
                    showDatePicker();
                    return true;
                }
            }
            return false;
        });
        dobField.addTextChangedListener(new DateOfBirthTextWatcher());
    }

    /**
     * Displays a date picker dialog for selecting date of birth with validation on age range.
     */
    private void showDatePicker() {
        final Calendar calendar = Calendar.getInstance();
        final int currentYear = calendar.get(Calendar.YEAR);
        int year = currentYear;
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        String dobText = dobField.getText().toString();
        if (dobText.length() == 10) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
                Date date = sdf.parse(dobText);
                calendar.setTime(date);
                year = calendar.get(Calendar.YEAR);
                month = calendar.get(Calendar.MONTH);
                day = calendar.get(Calendar.DAY_OF_MONTH);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year1, month1, dayOfMonth) -> {
                    Calendar selectedDate = Calendar.getInstance();
                    selectedDate.set(year1, month1, dayOfMonth);

                    int age = currentYear - year1;
                    if (age >= MIN_AGE && age <= MAX_AGE) {
                        dobField.setText(String.format(Locale.US, "%02d/%02d/%04d", month1 + 1, dayOfMonth, year1));
                    } else {
                        Toast.makeText(this, "Age must be between " + MIN_AGE + " and " + MAX_AGE, Toast.LENGTH_SHORT).show();
                    }
                },
                year, month, day);

        Calendar minDate = Calendar.getInstance();
        minDate.set(currentYear - MAX_AGE, Calendar.JANUARY, 1);

        Calendar maxDate = Calendar.getInstance();
        maxDate.set(currentYear - MIN_AGE, Calendar.DECEMBER, 31);

        datePickerDialog.getDatePicker().setMinDate(minDate.getTimeInMillis());
        datePickerDialog.getDatePicker().setMaxDate(maxDate.getTimeInMillis());

        datePickerDialog.show();
    }

    /**
     * Validates and formats date of birth input.
     */
    private class DateOfBirthTextWatcher implements TextWatcher {
        private boolean isUpdating;
        private final String dateFormat = "MM/dd/yyyy";

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (isUpdating) return;

            String cleanInput = s.toString().replaceAll("[^\\d]", "");
            StringBuilder formatted = new StringBuilder();
            if (cleanInput.length() >= 2) {
                formatted.append(cleanInput.substring(0, 2)).append("/");
                if (cleanInput.length() >= 4) formatted.append(cleanInput.substring(2, 4)).append("/");
                if (cleanInput.length() > 4) formatted.append(cleanInput.substring(4));
            } else {
                formatted.append(cleanInput);
            }

            isUpdating = true;
            dobField.setText(formatted.toString());
            dobField.setSelection(formatted.length());
            isUpdating = false;
        }

        @Override
        public void afterTextChanged(Editable s) {
            // Only validate if the length is exactly 10 (MM/DD/YYYY format)
            if (s.length() == 10) {
                String inputDate = s.toString();
                SimpleDateFormat sdf = new SimpleDateFormat(dateFormat, Locale.US);
                sdf.setLenient(false);

                try {
                    Date date = sdf.parse(inputDate);
                    Calendar calendar = Calendar.getInstance();
                    Calendar minDate = Calendar.getInstance();
                    minDate.add(Calendar.YEAR, -MAX_AGE);
                    Calendar maxDate = Calendar.getInstance();
                    maxDate.add(Calendar.YEAR, -MIN_AGE);

                    calendar.setTime(date);

                    if (calendar.before(minDate) || calendar.after(maxDate)) {
                        dobField.setError("Age must be between " + MIN_AGE + " and " + MAX_AGE + " years.");
                    } else {
                        dobField.setError(null);
                    }
                } catch (ParseException e) {
                    dobField.setError("Invalid date format. Use MM/DD/YYYY.");
                }
            } else {
                dobField.setError(null);
            }
        }
    }

    /**
     * Saves the updated profile data to Firestore and updates the profile image if changed.
     */
    private void saveProfileData() {
        String name = nameField.getText() != null ? nameField.getText().toString().trim() : "";
        String email = emailField.getText() != null ? emailField.getText().toString().trim() : "";
        String dob = dobField.getText() != null ? dobField.getText().toString().trim() : "";
        String country = countrySpinner.getSelectedItem() != null
                ? countrySpinner.getSelectedItem().toString()
                : "";
        boolean notificationsEnabled = notificationsSwitch.isChecked();
        String phone = phoneField.getText() != null ? phoneField.getText().toString().trim() : "";

        if (name.isEmpty() || email.isEmpty() || dob.isEmpty() || country.isEmpty()) {
            Toast.makeText(this, "Please fill out all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isValidEmail(email)) {
            Toast.makeText(this, "Invalid email format.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (dobField.getError() != null) {
            Toast.makeText(this, "Please enter a valid date of birth", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("name", name);
        userProfile.put("email", email);
        userProfile.put("dob", dob);
        userProfile.put("country", country);
        userProfile.put("notificationsEnabled", notificationsEnabled);
        if (!phone.isEmpty()) userProfile.put("phone", phone);

        DocumentReference userProfileRef = db.collection("users").document(userId);
        userProfileRef.set(userProfile)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                    Toast.makeText(this, "Error updating profile", Toast.LENGTH_SHORT).show();
                });

        if (imageUri != null) {
            StorageReference storageRef = storage.getReference("profile_images/" + userId + ".jpg");
            storageRef.putFile(imageUri)
                    .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        userProfileRef.update("profileImageUrl", uri.toString());
                        Toast.makeText(this, "Profile image uploaded", Toast.LENGTH_SHORT).show();
                    }))
                    .addOnFailureListener(e -> {
                        e.printStackTrace();
                        Toast.makeText(this, "Failed to upload profile image", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    /**
     * Loads user profile data from Firestore and populates UI fields accordingly.
     */
    private void loadUserProfile() {
        DocumentReference userProfileRef = db.collection("users").document(userId);
        userProfileRef.get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        String email = documentSnapshot.getString("email");
                        String dob = documentSnapshot.getString("dob");
                        String country = documentSnapshot.getString("country");
                        Boolean notificationsEnabled = documentSnapshot.getBoolean("notificationsEnabled");
                        String phone = documentSnapshot.getString("phone");

                        // Populate fields with retrieved data
                        if (name != null) nameField.setText(name);
                        if (email != null) emailField.setText(email);
                        if (dob != null) dobField.setText(dob);
                        if (country != null) {
                            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                                    this, R.array.country_array, android.R.layout.simple_spinner_item);
                            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                            countrySpinner.setAdapter(adapter);

                            int spinnerPosition = adapter.getPosition(country);
                            countrySpinner.setSelection(spinnerPosition);
                        }
                        if (phone != null) phoneField.setText(phone);

                        // Set the notificationsSwitch state based on notificationsEnabled
                        if (notificationsEnabled != null) {
                            notificationsSwitch.setChecked(notificationsEnabled);
                        } else {
                            notificationsSwitch.setChecked(false);
                        }

                        // Send a notification if notifications are enabled
                        if (notificationsEnabled != null && notificationsEnabled) {
                            Map<String, Object> userProfile = new HashMap<>();
                            userProfile.put("name", name);
                            NotificationService.sendNotification(userProfile, this, "Success", "You have opted into notifications");
                        }

                        loadProfileImage();
                    }
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                    Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Loads the profile image from Firebase Storage or generates an avatar if none is available.
     */
    private void loadProfileImage() {
        StorageReference storageRef = storage.getReference("profile_images/" + userId + ".jpg");
        storageRef.getDownloadUrl()
                .addOnSuccessListener(uri -> {
                    Glide.with(this)
                            .load(uri)
                            .apply(RequestOptions.circleCropTransform())
                            .placeholder(R.drawable.ic_profile)
                            .into(profileImageView);
                    removeProfileImageButton.setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                    String name = nameField.getText() != null ? nameField.getText().toString().trim() : "";
                    if (!name.isEmpty()) {
                        String firstLetter = String.valueOf(name.charAt(0)).toUpperCase(Locale.US);
                        Bitmap avatarBitmap = AvatarUtil.generateAvatar(firstLetter, 200, this);
                        profileImageView.setImageBitmap(avatarBitmap);
                    } else {
                        profileImageView.setImageResource(R.drawable.ic_profile);
                    }
                    removeProfileImageButton.setVisibility(View.GONE);
                });
    }

    /**
     * Validates email format to ensure it ends with .com or .ca.
     *
     * @param email The email to validate.
     * @return True if the email is valid, false otherwise.
     */
    private boolean isValidEmail(String email) {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
                && (email.endsWith(".com") || email.endsWith(".ca"));
    }
}