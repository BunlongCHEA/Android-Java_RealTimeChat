package com.project.realtimechatui.utils;

import android.text.TextUtils;
import android.util.Patterns;

public class ValidationUtils {
    public static boolean isValidEmail(String email) {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    public static boolean isValidUsername(String username) {
        if (TextUtils.isEmpty(username)) {
            return false;
        }

        int length = username.length();
        return length >= Constants.MIN_USERNAME_LENGTH &&
                length <= Constants.MAX_USERNAME_LENGTH &&
                username.matches("^[a-zA-Z0-9_]+$");
    }

    public static boolean isValidPassword(String password) {
        return !TextUtils.isEmpty(password) && password.length() >= Constants.MIN_PASSWORD_LENGTH;
    }

    public static boolean isValidDisplayName(String displayName) {
        return !TextUtils.isEmpty(displayName) && displayName.trim().length() >= Constants.MIN_USERNAME_LENGTH;
    }

    public static String getEmailError(String email) {
        if (TextUtils.isEmpty(email)) {
            return "Email is required";
        }
        if (!isValidEmail(email)) {
            return "Please enter a valid email address";
        }
        return null;
    }

    public static String getUsernameError(String username) {
        if (TextUtils.isEmpty(username)) {
            return "Username is required";
        }
        if (username.length() < Constants.MIN_USERNAME_LENGTH) {
            return "Username must be at least " + Constants.MIN_USERNAME_LENGTH + " characters";
        }
        if (username.length() > Constants.MAX_USERNAME_LENGTH) {
            return "Username must be less than " + Constants.MAX_USERNAME_LENGTH + " characters";
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            return "Username can only contain letters, numbers, and underscores";
        }
        return null;
    }

    public static String getPasswordError(String password) {
        if (TextUtils.isEmpty(password)) {
            return "Password is required";
        }
        if (password.length() < Constants.MIN_PASSWORD_LENGTH) {
            return "Password must be at least " + Constants.MIN_PASSWORD_LENGTH + " characters";
        }
        return null;
    }

    public static String getFullNameError(String fullName) {
        if (TextUtils.isEmpty(fullName)) {
            return "Display name is required";
        }
        if (fullName.trim().length() < 2) {
            return "Display name must be at least 2 characters";
        }
        return null;
    }
}
