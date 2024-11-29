package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.example.myapplication.Views.OrganizerProfileView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

public class UserManager {
    private static final String TAG = "UserManager";
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    public UserManager() {
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
    }

    public void fetchUserRole(Context context, RoleCallback callback) {
        String uid = mAuth.getCurrentUser().getUid();
        if (uid == null) {
            Toast.makeText(context, "User not authenticated.", Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference userRef = db.collection("users").document(uid);
        userRef.get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Boolean isAdmin = documentSnapshot.getBoolean("isAdmin");
                        Boolean isOrganizer = documentSnapshot.getBoolean("isOrganizer");

                        //handle nulls by treating them as false
                        isAdmin = isAdmin != null && isAdmin;
                        isOrganizer = isOrganizer != null && isOrganizer;

                        Log.d(TAG, "User roles - isAdmin: " + isAdmin + ", isOrganizer: " + isOrganizer);

                        if (isAdmin) {
                            callback.onRoleFetched("admin");
                        } else if (isOrganizer) {
                            callback.onRoleFetched("organizer");
                        } else {
                            callback.onRoleFetched("entrant");
                        }
                    } else {
                        Log.e(TAG, "User document does not exist.");
                        Toast.makeText(context, "User data not found.", Toast.LENGTH_SHORT).show();
                        callback.onRoleFetched("entrant"); // Default role
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching user role", e);
                    Toast.makeText(context, "Failed to fetch user data.", Toast.LENGTH_SHORT).show();
                    callback.onRoleFetched("entrant"); // Default role
                });
    }

    public interface RoleCallback {
        void onRoleFetched(String role);
    }

    public void navigateToProfile(Context context, String role) {
        Intent intent;
        switch (role.toLowerCase()) {
            case "admin":
                intent = new Intent(context, AdminProfileActivity.class);
                break;
            case "organizer":
                intent = new Intent(context, OrganizerProfileView.class);
                break;
            case "entrant":
            default:
                intent = new Intent(context, EditProfileActivity.class);
                break;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        if (context instanceof MainActivity) {
            ((MainActivity) context).finish();
        }
    }
}