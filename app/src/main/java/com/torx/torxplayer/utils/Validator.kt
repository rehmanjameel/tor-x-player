package com.arconn.devicedesk.utils

import java.util.regex.Pattern

class Validator {

    fun isValidMail(email: String?): Boolean {
        val EMAIL_STRING = "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+)\\.[A-Za-z]{2,}$"
        return Pattern.compile(EMAIL_STRING).matcher(email).matches()
    }

    fun isValidPakistanMobileNumber(phoneNumber: String?): Boolean {
        // Valid formats: +923001234567, 03001234567, 3001234567
        val PHONE_NUMBER_STRING = "^(\\+92|92|0)?[3456789]\\d{9}$"
        return Pattern.compile(PHONE_NUMBER_STRING).matcher(phoneNumber).matches()
    }

    fun isValidWorldMobileNumber(phoneNumber: String?): Boolean {
        // Valid formats: +1234567890, 1234567890
        val PHONE_NUMBER_STRING = "^\\+?[1-9]\\d{1,14}$"
        return phoneNumber != null && Pattern.compile(PHONE_NUMBER_STRING).matcher(phoneNumber).matches()
    }

    fun isValidIndianMobileNumber(phoneNumber: String?): Boolean {
        // Valid formats: +919876543210, 919876543210, 09876543210, 9876543210
        val PHONE_NUMBER_STRING = "^(\\+91|91|0)?[6-9]\\d{9}$"
        return phoneNumber != null && Pattern.compile(PHONE_NUMBER_STRING).matcher(phoneNumber).matches()
    }


    fun isValidPasswordFormat(password: String?): Boolean {
        val passwordREGEX = "^(?=.*[0-9])" +  // at least 1 digit
                //                "(?=.*[a-z])" +         //at least 1 lower case letter
                //                "(?=.*[A-Z])" +         //at least 1 upper case letter
                "(?=.*[a-zA-Z])" +  // any letter
                "(?=.*[@#$%^&+=])" +  // at least 1 special character
                "(?=\\S+$)" +  // no white spaces
                ".{6,}" +  // at least 6 characters
                "$"
        return Pattern.compile(passwordREGEX).matcher(password).matches()
    }

    fun isValidUserName(userName: String?): Boolean {
        val USER_NAME = "^(?=.*[a-zA-Z0-9])" +  // any letter
                //                        "(?=.*[0-9])" +         //at least 1 digit
                // "(?=.*[a-z])" +         //at least 1 lower case letter
                // "(?=.*[A-Z])" +         //at least 1 upper case letter
                "(?=\\S+$)" +  // no white spaces
                // "(?=.*[@#$%^&+=])" +    //at least 1 special character
                ".{4,}" +  // at least 4 characters
                "$"
        return Pattern.compile(USER_NAME).matcher(userName).matches()
    }

    fun isValidFirstLastName(userName: String?): Boolean {
        val USER_NAME = "^(?=.*[a-zA-Z0-9])" +  // any letter
                //                    "(?=.*[0-9])" +         //at least 1 digit
                // "(?=.*[a-z])" +         //at least 1 lower case letter
                // "(?=.*[A-Z])" +         //at least 1 upper case letter
                ".{4,}" +  // at least 4 characters
                // "(?=.*[@#$%^&+=])" +    //at least 1 special character
                //                    "(?=\\S+$)" +           //no white spaces
                "$"
        return Pattern.compile(USER_NAME).matcher(userName).matches()
    }
}