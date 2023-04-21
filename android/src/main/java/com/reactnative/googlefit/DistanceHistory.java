/**
 * Copyright (c) 2017-present, Stanislav Doskalenko - doskalenko.s@gmail.com
 * All rights reserved.
 * <p>
 * This source code is licensed under the MIT-style license found in the
 * LICENSE file in the root directory of this source tree.
 * <p>
 * Based on Asim Malik android source code, copyright (c) 2015
 **/

package com.reactnative.googlefit;

import static com.reactnative.googlefit.GoogleFitManager.userId;

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResult;

import java.sql.Array;
import java.text.DateFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DistanceHistory {

    private ReactContext mReactContext;
    private GoogleFitManager googleFitManager;
    private static final String TAG = "DistanceHistory";

    public DistanceHistory(ReactContext reactContext, GoogleFitManager googleFitManager) {
        this.mReactContext = reactContext;
        this.googleFitManager = googleFitManager;
    }

    public ReadableArray aggregateDataByDate(long startTime, long endTime, int bucketInterval, String bucketUnit) {
        String TYPE_USER_INPUT = "user_input";
        Float userInputDistance = 0f;
        Float stepCounterDistance = 0f;
        List<Map> clearSamplesArray = new ArrayList<Map>();
        DateFormat dateFormat = DateFormat.getDateInstance();
        Log.i(TAG, "Range Start: " + dateFormat.format(startTime));
        Log.i(TAG, "Range End: " + dateFormat.format(endTime));

        // Check how much distance were walked and recorded in specified days
        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_DISTANCE_DELTA, DataType.AGGREGATE_DISTANCE_DELTA)
                .bucketByTime(bucketInterval, HelperUtil.processBucketUnit(bucketUnit))
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();

        // Additional data request
        PendingResult<DataReadResult> pendingResult = Fitness.HistoryApi.readData(
                googleFitManager.getGoogleApiClient(),
                new DataReadRequest.Builder()
                        .read(DataType.TYPE_DISTANCE_DELTA)
                        .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                        .build());

        DataReadResult additionalReadDataResult = pendingResult.await();

        List<DataSet> additionaldataSets = additionalReadDataResult.getDataSets();

        for (DataSet dataSet : additionaldataSets) {
            for (DataPoint dataPoint : dataSet.getDataPoints()) {
                Long start = dataPoint.getStartTime(TimeUnit.MILLISECONDS);
                Long end = dataPoint.getEndTime(TimeUnit.MILLISECONDS);

                String StreamIdentifier = dataPoint.getOriginalDataSource().getStreamIdentifier();
                if (StreamIdentifier != null) {
                    Log.i("TEST_ID", "StreamIdentifier: " + StreamIdentifier.toString());
                }

                // Check data stream sourse
                Boolean isAddedByUser = StreamIdentifier.contains(TYPE_USER_INPUT);

                Log.i("isAddedByUser", "isAddedByUser: " + isAddedByUser + " ");

                // Used for user input data
                if (isAddedByUser) {

                    for (Field field : dataPoint.getDataType().getFields()) {
                        Log.i(TAG,
                                "Found user input data: " + field.getName() + " Value: " + dataPoint.getValue(field));
                        userInputDistance += dataPoint.getValue(field).asFloat();
                    }
                }

                // Used for step counter data
                else {
                    for (Field field : dataPoint.getDataType().getFields()) {
                        Log.i(TAG,
                                "Found step counter data: " + field.getName() + " Value: " + dataPoint.getValue(field));
                        stepCounterDistance += dataPoint.getValue(field).asFloat();
                        Map<String, Object> myMap = new HashMap<>();
                        myMap.put("distance", dataPoint.getValue(field).asFloat());
                        myMap.put("start", start);
                        myMap.put("end", end);
                        clearSamplesArray.add(myMap);
                    }
                }
            }
        }

        DataReadResult dataReadResult = Fitness.HistoryApi.readData(googleFitManager.getGoogleApiClient(), readRequest)
                .await(1, TimeUnit.MINUTES);

        WritableArray map = Arguments.createArray();

        // Used for aggregated data
        if (dataReadResult.getBuckets().size() > 0) {
            Log.i(TAG, "Number of buckets: " + dataReadResult.getBuckets().size());
            for (Bucket bucket : dataReadResult.getBuckets()) {
                List<DataSet> dataSets = bucket.getDataSets();
                for (DataSet dataSet : dataSets) {
                    processDataSet(dataSet, map, userInputDistance, stepCounterDistance);
                }
            }
        }

        // Used for non-aggregated data
        else if (dataReadResult.getDataSets().size() > 0) {
            Log.i(TAG, "Number of returned DataSets: " + dataReadResult.getDataSets().size());
            for (DataSet dataSet : dataReadResult.getDataSets()) {
                processDataSet(dataSet, map, userInputDistance, stepCounterDistance);
            }
        }

        WritableMap clearSamples = Arguments.createMap();
        clearSamples.putArray("clearSamples", Arguments.makeNativeArray(clearSamplesArray));
        map.pushMap(clearSamples);

        return map;
    }

    private void processDataSet(DataSet dataSet, WritableArray map, float userInputDistance,
                                float stepCounterDistance) {
        Log.i(TAG, "Data returned for Data type: " + dataSet.getDataType().getName());
        DateFormat dateFormat = DateFormat.getDateInstance();
        DateFormat timeFormat = DateFormat.getTimeInstance();
        Format formatter = new SimpleDateFormat("EEE");

        WritableMap stepMap = Arguments.createMap();
        if (userId != null) {
            stepMap.putString("userId", (String) userId);

        }

        for (DataPoint dp : dataSet.getDataPoints()) {
            Log.i(TAG, "Data point:");
            Log.i(TAG, "\tType: " + dp.getDataType().getName());
            Log.i(TAG, "\tStart: " + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)) + " "
                    + timeFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
            Log.i(TAG, "\tEnd: " + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)) + " "
                    + timeFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));

            String day = formatter.format(new Date(dp.getStartTime(TimeUnit.MILLISECONDS)));
            Log.i(TAG, "Day: " + day);

            for (Field field : dp.getDataType().getFields()) {
                Log.i("History", "\tField: " + field.getName() +
                        " Value: " + dp.getValue(field));

                stepMap.putString("day", day);
                stepMap.putDouble("startDate", dp.getStartTime(TimeUnit.MILLISECONDS));
                stepMap.putDouble("endDate", dp.getEndTime(TimeUnit.MILLISECONDS));
                stepMap.putDouble("totalDistance", dp.getValue(field).asFloat());
                stepMap.putDouble("pedometerDistance", stepCounterDistance);
                stepMap.putDouble("userInputDistance", userInputDistance);
                map.pushMap(stepMap);
            }
        }
    }

}
