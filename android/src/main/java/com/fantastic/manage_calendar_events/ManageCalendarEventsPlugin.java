/*
FORKED AND UPDATED BETA VERSION
*/

package com.fantastic.manage_calendar_events;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.fantastic.manage_calendar_events.models.Calendar;
import com.fantastic.manage_calendar_events.models.CalendarEvent;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/**
 * ManageCalendarEventsPlugin aggiornato per Flutter 3+ e AGP 8+
 */
public class ManageCalendarEventsPlugin implements FlutterPlugin, ActivityAware, MethodCallHandler {

    private static final String CHANNEL_NAME = "manage_calendar_events";

    private MethodChannel methodChannel;
    private BinaryMessenger binaryMessenger;
    private Context context;
    private Activity activity;
    private CalendarOperations operations;
    private final Gson gson = new Gson();

    // ===========================
    // FlutterPlugin Lifecycle
    // ===========================
    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        Log.d("DART/NATIVE", "onAttachedToEngine");
        binaryMessenger = binding.getBinaryMessenger();
        context = binding.getApplicationContext();
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        Log.d("DART/NATIVE", "onDetachedFromEngine");
        if (methodChannel != null) {
            methodChannel.setMethodCallHandler(null);
            methodChannel = null;
        }
    }

    // ===========================
    // ActivityAware Lifecycle
    // ===========================
    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        Log.d("DART/NATIVE", "onAttachedToActivity");
        activity = binding.getActivity();
        setupMethodChannel();
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        activity = null;
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
        setupMethodChannel();
    }

    @Override
    public void onDetachedFromActivity() {
        activity = null;
    }

    // ===========================
    // MethodChannel Setup
    // ===========================
    private void setupMethodChannel() {
        if (binaryMessenger == null || activity == null || context == null) {
            return;
        }

        operations = new CalendarOperations(activity, context);

        methodChannel = new MethodChannel(binaryMessenger, CHANNEL_NAME);
        methodChannel.setMethodCallHandler(this);
    }

    // ===========================
    // MethodCallHandler
    // ===========================
    @Override
    public void onMethodCall(MethodCall call, Result result) {
        switch (call.method) {
            case "getPlatformVersion":
                result.success("Android " + android.os.Build.VERSION.RELEASE);
                break;

            case "hasPermissions":
                result.success(operations.hasPermissions());
                break;

            case "requestPermissions":
                operations.requestPermissions();
                break;

            case "getCalendars":
                List<Calendar> calendars = operations.getCalendars();
                result.success(gson.toJson(calendars));
                break;

            case "getEvents":
                String calendarId = call.argument("calendarId");
                result.success(gson.toJson(operations.getAllEvents(calendarId)));
                break;

            case "getEventsByDateRange":
                calendarId = call.argument("calendarId");
                long startDate = call.argument("startDate");
                long endDate = call.argument("endDate");
                result.success(gson.toJson(operations.getEventsByDateRange(calendarId, startDate, endDate)));
                break;

            case "createEvent":
            case "updateEvent":
                handleCreateUpdateEvent(call, result);
                break;

            case "deleteEvent":
                calendarId = call.argument("calendarId");
                String eventId = call.argument("eventId");
                result.success(operations.deleteEvent(calendarId, eventId));
                break;

            case "addReminder":
                calendarId = call.argument("calendarId");
                eventId = call.argument("eventId");
                long minutes = Long.parseLong(call.<String>argument("minutes"));
                operations.addReminder(calendarId, eventId, minutes);
                break;

            case "updateReminder":
                calendarId = call.argument("calendarId");
                eventId = call.argument("eventId");
                minutes = Long.parseLong(call.<String>argument("minutes"));
                result.success(operations.updateReminder(calendarId, eventId, minutes));
                break;

            case "deleteReminder":
                eventId = call.argument("eventId");
                result.success(operations.deleteReminder(eventId));
                break;

            case "getAttendees":
                eventId = call.argument("eventId");
                result.success(gson.toJson(operations.getAttendees(eventId)));
                break;

            case "addAttendees":
                eventId = call.argument("eventId");
                addAttendees(eventId, call);
                break;

            case "deleteAttendee":
                eventId = call.argument("eventId");
                Map<String, Object> attendeeMap = call.argument("attendee");
                String name = (String) attendeeMap.get("name");
                String emailAddress = (String) attendeeMap.get("emailAddress");
                boolean isOrganiser = attendeeMap.get("isOrganiser") != null ? (boolean) attendeeMap.get("isOrganiser") : false;
                CalendarEvent.Attendee attendee = new CalendarEvent.Attendee(name, emailAddress, isOrganiser);
                result.success(operations.deleteAttendee(eventId, attendee));
                break;

            default:
                result.notImplemented();
                break;
        }
    }

    // ===========================
    // Helpers
    // ===========================
    private void handleCreateUpdateEvent(MethodCall call, Result result) {
        String calendarId = call.argument("calendarId");
        String eventId = call.argument("eventId");
        String title = call.argument("title");
        String description = call.argument("description");
        long startDate = call.argument("startDate");
        long endDate = call.argument("endDate");
        String location = call.argument("location");
        String url = call.argument("url");
        boolean isAllDay = call.argument("isAllDay");
        boolean hasAlarm = call.argument("hasAlarm");

        CalendarEvent event = new CalendarEvent(eventId, title, description, startDate, endDate, location, url, isAllDay, hasAlarm);
        operations.createUpdateEvent(calendarId, event);

        if (call.hasArgument("attendees")) {
            addAttendees(event.getEventId(), call);
        }

        result.success(event.getEventId());
    }

    private void addAttendees(String eventId, MethodCall call) {
        List<CalendarEvent.Attendee> attendees = new ArrayList<>();
        List<Map<String, Object>> jsonList = call.argument("attendees");
        if (jsonList != null) {
            for (Map<String, Object> map : jsonList) {
                String name = (String) map.get("name");
                String emailAddress = (String) map.get("emailAddress");
                boolean isOrganiser = map.get("isOrganiser") != null ? (boolean) map.get("isOrganiser") : false;
                attendees.add(new CalendarEvent.Attendee(name, emailAddress, isOrganiser));
            }
            operations.addAttendees(eventId, attendees);
        }
    }
}