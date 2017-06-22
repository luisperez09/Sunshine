/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.sunshine.utilities;

import android.content.ContentValues;
import android.content.Context;

import com.example.android.sunshine.data.SunshinePreferences;
import com.example.android.sunshine.data.WeatherContract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;

/**
 * Utility functions to handle OpenWeatherMap JSON data.
 */
public final class OpenWeatherJsonUtils {

    /* Location information */
    private static final String OWM_CITY = "city";
    private static final String OWM_COORD = "coord";

    /* Location coordinate */
    private static final String OWM_LATITUDE = "lat";
    private static final String OWM_LONGITUDE = "lon";

    /* Weather information. Each day's forecast info is an element of the "list" array */
    private static final String OWM_LIST = "list";

    private static final String OWM_PRESSURE = "pressure";
    private static final String OWM_HUMIDITY = "humidity";
    private static final String OWM_WINDSPEED = "speed";
    private static final String OWM_WIND_DIRECTION = "deg";

    /* All temperatures are children of the "temp" object */
    private static final String OWM_TEMPERATURE = "temp";

    /* Max temperature for the day */
    private static final String OWM_MAX = "temp_max";
    private static final String OWM_MIN = "temp_min";

    private static final String OWM_WEATHER = "weather";
    private static final String OWM_WEATHER_ID = "id";

    private static final String OWM_MESSAGE_CODE = "cod";
    private static final String OWM_MAIN = "main";
    private static final String OWM_WIND = "wind";
    private static final String OWM_DT_TXT = "dt_txt";
    private static final String LAST_FORECAST_TIME = "21:00:00";

    /**
     * Checks highest temperature of current day in the loop
     */
    private static Double currentHigh = null;
    /**
     * Checks highest temperature of current day in the loop
     */
    private static Double currentLow = null;

    /**
     * This method parses JSON from a web response and returns an array of Strings
     * describing the weather over various days from the forecast.
     * <p/>
     * Later on, we'll be parsing the JSON into structured data within the
     * getFullWeatherDataFromJson function, leveraging the data we have stored in the JSON. For
     * now, we just convert the JSON into human-readable strings.
     *
     * @param forecastJsonStr JSON response from server
     * @return Array of Strings describing weather data
     * @throws JSONException If JSON data cannot be properly parsed
     */
    public static ContentValues[] getWeatherContentValuesFromJson(Context context, String forecastJsonStr)
            throws JSONException {

        JSONObject forecastJson = new JSONObject(forecastJsonStr);

        /* Is there an error? */
        if (forecastJson.has(OWM_MESSAGE_CODE)) {
            int errorCode = forecastJson.getInt(OWM_MESSAGE_CODE);

            switch (errorCode) {
                case HttpURLConnection.HTTP_OK:
                    break;
                case HttpURLConnection.HTTP_NOT_FOUND:
                    /* Location invalid */
                    return null;
                default:
                    /* Server probably down */
                    return null;
            }
        }

        JSONArray jsonWeatherArray = forecastJson.getJSONArray(OWM_LIST);

        JSONObject cityJson = forecastJson.getJSONObject(OWM_CITY);

        JSONObject cityCoord = cityJson.getJSONObject(OWM_COORD);
        double cityLatitude = cityCoord.getDouble(OWM_LATITUDE);
        double cityLongitude = cityCoord.getDouble(OWM_LONGITUDE);

        SunshinePreferences.setLocationDetails(context, cityLatitude, cityLongitude);

        // Array contiene 8 predicciones por día, el objeto ContentValues tendrá como tamaño 1
        // por cada día más 1 adicional para el día fraccionado
        ContentValues[] weatherContentValues = new ContentValues[jsonWeatherArray.length() / 8 + 1];

        /*
         * OWM returns daily forecasts based upon the local time of the city that is being asked
         * for, which means that we need to know the GMT offset to translate this data properly.
         * Since this data is also sent in-order and the first day is always the current day, we're
         * going to take advantage of that to get a nice normalized UTC date for all of our weather.
         */
//        long now = System.currentTimeMillis();
//        long normalizedUtcStartDay = SunshineDateUtils.normalizeDate(now);

        long normalizedUtcStartDay = SunshineDateUtils.getNormalizedUtcDateForToday();
        // contador de días para referenciar dentro del array del ContentValues
        int dayIndex = 0;
        for (int i = 0; i < jsonWeatherArray.length(); i++) {

            long dateTimeMillis;
            double pressure;
            int humidity;
            double windSpeed;
            double windDirection;

            double high;
            double low;

            int weatherId;

            /* Get the JSON object representing the day */
            JSONObject dayForecast = jsonWeatherArray.getJSONObject(i);

            /*
             * We ignore all the datetime values embedded in the JSON and assume that
             * the values are returned in-order by day (which is not guaranteed to be correct).
             */
            dateTimeMillis = normalizedUtcStartDay + SunshineDateUtils.DAY_IN_MILLIS * dayIndex;

            JSONObject mainStats = dayForecast.getJSONObject(OWM_MAIN);
            pressure = mainStats.getDouble(OWM_PRESSURE);
            humidity = mainStats.getInt(OWM_HUMIDITY);
            high = mainStats.getDouble(OWM_MAX);
            low = mainStats.getDouble(OWM_MIN);
            compareAndSetHigh(high);
            compareAndSetLow(low);


            JSONObject windStats = dayForecast.getJSONObject(OWM_WIND);
            windSpeed = windStats.getDouble(OWM_WINDSPEED);
            windDirection = windStats.getDouble(OWM_WIND_DIRECTION);

            /*
             * Description is in a child array called "weather", which is 1 element long.
             * That element also contains a weather code.
             */
            JSONObject weatherObject =
                    dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);

            weatherId = weatherObject.getInt(OWM_WEATHER_ID);

            // Check if current forecast is last of the day
            String dateText = dayForecast.getString(OWM_DT_TXT);
            boolean lastOfDay = dateText.toLowerCase().contains(LAST_FORECAST_TIME);

            // Add to array only after last forecast of the day has been compared
            if (lastOfDay) {

                ContentValues weatherValues = new ContentValues();
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DATE, dateTimeMillis);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_HUMIDITY, humidity);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_PRESSURE, pressure);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_WIND_SPEED, windSpeed);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_DEGREES, windDirection);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP, currentHigh);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP, currentLow);
                weatherValues.put(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID, weatherId);

                weatherContentValues[dayIndex] = weatherValues;
                // Saltamos al siguiente día y reseteamos los valores máximos y mínimos
                dayIndex++;
                currentHigh = null;
                currentLow = null;
            }
        }

        return weatherContentValues;
    }

    /**
     * Compara la temperatura máxima de la predicción actual con la máxima registrada del día actual
     * y setea la más alta como la máxima del día
     *
     * @param high temperatura máxima de la predicción actual
     */
    private static void compareAndSetHigh(Double high) {
        if (currentHigh == null || currentHigh < high) {
            currentHigh = high;
        }
    }

    /**
     * Compara la temperatura mínima de la predicción actual con la mínima registrada del día actual
     * y setea la más baja como la mínima del día
     *
     * @param low temperatura mínima de la predicción actual
     */
    private static void compareAndSetLow(Double low) {
        if (currentLow == null || currentLow > low) {
            currentLow = low;
        }
    }
}